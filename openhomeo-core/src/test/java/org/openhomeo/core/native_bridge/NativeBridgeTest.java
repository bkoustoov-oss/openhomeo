package org.openhomeo.core.native_bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NativeBridgeTest {

    @Test
    public void testNativeOrFallbackRgbToLab() {
        // Pure White (255, 255, 255) -> L* should be ~100.0, a* ~0.0, b* ~0.0
        double[] labWhite = NativeBridge.rgbToLab(255, 255, 255);
        assertEquals(3, labWhite.length);
        assertEquals(100.0, labWhite[0], 0.5, "White L* should be ~100");
        assertEquals(0.0, labWhite[1], 0.5, "White a* should be ~0");
        assertEquals(0.0, labWhite[2], 0.5, "White b* should be ~0");

        // Pure Black (0, 0, 0) -> L* should be ~0.0
        double[] labBlack = NativeBridge.rgbToLab(0, 0, 0);
        assertEquals(0.0, labBlack[0], 0.5, "Black L* should be ~0");
    }

    @Test
    public void testCiede2000ColorDifference() {
        // Identical colors
        double deltaZero = NativeBridge.ciede2000(50.0, 20.0, 30.0, 50.0, 20.0, 30.0);
        assertEquals(0.0, deltaZero, 0.0001, "Delta-E of identical colors must be zero");

        // Clinical erythema flare vs remission
        double deltaFlare = NativeBridge.ciede2000(55.0, 45.0, 20.0, 60.0, 25.0, 15.0);
        assertTrue(deltaFlare > 5.0, "Delta-E should detect perceptible erythema shift, got: " + deltaFlare);
    }

    @Test
    public void testSha256Digest() {
        byte[] sample = "OpenHomeo Clinical Image Asset".getBytes();
        String hash = NativeBridge.sha256Hex(sample);
        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 hash must be 64 hex characters");
    }
}
