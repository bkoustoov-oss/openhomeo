package org.openhomeo.core.native_bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * High-performance native bridge.
 * Links to openhomeo_native (Rust cdylib) via Java 22+ Project Panama (FFM API).
 * Provides automatic pure-Java fallback if native library is not compiled or loaded.
 */
public class NativeBridge {
    private static final Logger log = LoggerFactory.getLogger(NativeBridge.class);

    private static boolean nativeLoaded = false;
    private static MethodHandle mhCiede2000;
    private static MethodHandle mhRgbToLab;
    private static MethodHandle mhComputeImageLabMean;
    private static MethodHandle mhArgon2Hash;
    private static MethodHandle mhArgon2Verify;
    private static MethodHandle mhSha256;

    static {
        tryLoadNative();
    }

    private static void tryLoadNative() {
        String libName = System.mapLibraryName("openhomeo_native");
        File[] candidates = new File[] {
                new File("native-core/target/release/" + libName),
                new File("../native-core/target/release/" + libName),
                new File(System.getProperty("user.home") + File.separator + ".openhomeo" + File.separator + libName),
                new File("native-core/target/debug/" + libName),
                new File("../native-core/target/debug/" + libName)
        };

        File libFile = null;
        for (File candidate : candidates) {
            if (candidate.exists()) {
                libFile = candidate;
                break;
            }
        }

        if (libFile.exists()) {
            try {
                System.load(libFile.getAbsolutePath());
                Linker linker = Linker.nativeLinker();
                SymbolLookup lookup = SymbolLookup.loaderLookup();

                lookup.find("openhomeo_ciede2000").ifPresent(seg ->
                        mhCiede2000 = linker.downcallHandle(seg, FunctionDescriptor.of(
                                ValueLayout.JAVA_DOUBLE,
                                ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                                ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE
                        ))
                );

                lookup.find("openhomeo_rgb_to_lab").ifPresent(seg ->
                        mhRgbToLab = linker.downcallHandle(seg, FunctionDescriptor.ofVoid(
                                ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE,
                                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
                        ))
                );

                nativeLoaded = true;
                log.info("Successfully loaded Rust native acceleration library via Panama FFM: {}", libFile.getAbsolutePath());
            } catch (Throwable t) {
                log.warn("Could not bind native Panama handles (falling back to pure Java): {}", t.getMessage());
                nativeLoaded = false;
            }
        } else {
            log.info("Native library ({}) not yet compiled; using pure Java high-performance fallback", libName);
            nativeLoaded = false;
        }
    }

    public static boolean isNativeLoaded() {
        return nativeLoaded;
    }

    // =========================================================================
    // COLOR SPACE CONVERSION: sRGB -> CIE-L*a*b* (D65)
    // =========================================================================

    public static double[] rgbToLab(int r, int g, int b) {
        if (nativeLoaded && mhRgbToLab != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segL = arena.allocate(ValueLayout.JAVA_DOUBLE);
                MemorySegment segA = arena.allocate(ValueLayout.JAVA_DOUBLE);
                MemorySegment segB = arena.allocate(ValueLayout.JAVA_DOUBLE);

                mhRgbToLab.invokeExact((byte) r, (byte) g, (byte) b, segL, segA, segB);
                return new double[]{
                        segL.get(ValueLayout.JAVA_DOUBLE, 0),
                        segA.get(ValueLayout.JAVA_DOUBLE, 0),
                        segB.get(ValueLayout.JAVA_DOUBLE, 0)
                };
            } catch (Throwable t) {
                log.debug("Native rgbToLab failed, using Java fallback", t);
            }
        }
        return rgbToLabJava(r, g, b);
    }

    public static double[] rgbToLabJava(int r, int g, int b) {
        // Pivot sRGB to linear XYZ
        double rNorm = pivotRgb(r);
        double gNorm = pivotRgb(g);
        double bNorm = pivotRgb(b);

        double x = rNorm * 0.4124564 + gNorm * 0.3575761 + bNorm * 0.1804375;
        double y = rNorm * 0.2126729 + gNorm * 0.7151522 + bNorm * 0.0721750;
        double z = rNorm * 0.0193339 + gNorm * 0.1191920 + bNorm * 0.9503041;

        // D65 reference white
        double fx = pivotXyz(x / 95.047);
        double fy = pivotXyz(y / 100.000);
        double fz = pivotXyz(z / 108.883);

        double l = 116.0 * fy - 16.0;
        double a = 500.0 * (fx - fy);
        double bVal = 200.0 * (fy - fz);
        return new double[]{l, a, bVal};
    }

    private static double pivotRgb(int v) {
        double val = (v & 0xFF) / 255.0;
        return val > 0.04045 ? Math.pow((val + 0.055) / 1.055, 2.4) * 100.0 : (val / 12.92) * 100.0;
    }

    private static double pivotXyz(double v) {
        return v > 0.008856 ? Math.cbrt(v) : (903.3 * v + 16.0) / 116.0;
    }

    // =========================================================================
    // CIEDE2000 COLOR DIFFERENCE (Delta-E)
    // =========================================================================

    public static double ciede2000(double l1, double a1, double b1, double l2, double a2, double b2) {
        if (nativeLoaded && mhCiede2000 != null) {
            try {
                return (double) mhCiede2000.invokeExact(l1, a1, b1, l2, a2, b2);
            } catch (Throwable t) {
                log.debug("Native ciede2000 failed, using Java fallback", t);
            }
        }
        return ciede2000Java(l1, a1, b1, l2, a2, b2);
    }

    public static double ciede2000Java(double l1, double a1, double b1, double l2, double a2, double b2) {
        double c1 = Math.hypot(a1, b1);
        double c2 = Math.hypot(a2, b2);
        double cBar = (c1 + c2) / 2.0;

        double g = 0.5 * (1.0 - Math.sqrt(Math.pow(cBar, 7) / (Math.pow(cBar, 7) + Math.pow(25.0, 7))));
        double a1Prime = (1.0 + g) * a1;
        double a2Prime = (1.0 + g) * a2;

        double c1Prime = Math.hypot(a1Prime, b1);
        double c2Prime = Math.hypot(a2Prime, b2);

        double h1Prime = computeHueDeg(a1Prime, b1);
        double h2Prime = computeHueDeg(a2Prime, b2);

        double deltaLPrime = l2 - l1;
        double deltaCPrime = c2Prime - c1Prime;

        double hDiff = Math.abs(h2Prime - h1Prime);
        double deltaHPrime;
        if (c1Prime * c2Prime == 0.0) {
            deltaHPrime = 0.0;
        } else if (hDiff <= 180.0) {
            deltaHPrime = h2Prime - h1Prime;
        } else if (h2Prime <= h1Prime) {
            deltaHPrime = h2Prime - h1Prime + 360.0;
        } else {
            deltaHPrime = h2Prime - h1Prime - 360.0;
        }

        double deltaCapHPrime = 2.0 * Math.sqrt(c1Prime * c2Prime) * Math.sin(Math.toRadians(deltaHPrime / 2.0));
        double lBarPrime = (l1 + l2) / 2.0;
        double cBarPrime = (c1Prime + c2Prime) / 2.0;

        double hBarPrime;
        if (c1Prime * c2Prime == 0.0) {
            hBarPrime = h1Prime + h2Prime;
        } else if (hDiff <= 180.0) {
            hBarPrime = (h1Prime + h2Prime) / 2.0;
        } else if (h1Prime + h2Prime < 360.0) {
            hBarPrime = (h1Prime + h2Prime + 360.0) / 2.0;
        } else {
            hBarPrime = (h1Prime + h2Prime - 360.0) / 2.0;
        }

        double t = 1.0
                - 0.17 * Math.cos(Math.toRadians(hBarPrime - 30.0))
                + 0.24 * Math.cos(Math.toRadians(2.0 * hBarPrime))
                + 0.32 * Math.cos(Math.toRadians(3.0 * hBarPrime + 6.0))
                - 0.20 * Math.cos(Math.toRadians(4.0 * hBarPrime - 63.0));

        double deltaTheta = 30.0 * Math.exp(-Math.pow((hBarPrime - 275.0) / 25.0, 2));
        double rC = 2.0 * Math.sqrt(Math.pow(cBarPrime, 7) / (Math.pow(cBarPrime, 7) + Math.pow(25.0, 7)));
        double sL = 1.0 + (0.015 * Math.pow(lBarPrime - 50.0, 2)) / Math.sqrt(20.0 + Math.pow(lBarPrime - 50.0, 2));
        double sC = 1.0 + 0.045 * cBarPrime;
        double sH = 1.0 + 0.015 * cBarPrime * t;
        double rT = -rC * Math.sin(Math.toRadians(2.0 * deltaTheta));

        double termL = deltaLPrime / sL;
        double termC = deltaCPrime / sC;
        double termH = deltaCapHPrime / sH;

        return Math.sqrt(termL * termL + termC * termC + termH * termH + rT * termC * termH);
    }

    private static double computeHueDeg(double a, double b) {
        if (a == 0.0 && b == 0.0) return 0.0;
        double deg = Math.toDegrees(Math.atan2(b, a));
        return deg >= 0.0 ? deg : deg + 360.0;
    }

    // =========================================================================
    // CRYPTOGRAPHY (SHA-256 & Argon2id Hash)
    // =========================================================================

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }
}
