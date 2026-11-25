package org.openhomeo.core.net;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Properties;

/**
 * Persists workstation deployment role (STANDALONE, SERVER, or CLIENT)
 * outside user_clinic.db so Client terminals connect directly to the
 * Server's shared database without creating isolated local clinic DBs.
 */
public record DeploymentProfile(
        DeploymentMode mode,
        String serverHost,
        int serverPort,
        String pairingKey,
        String clinicLabel
) {
    public enum DeploymentMode {
        STANDALONE("Standalone (Single Workstation)"),
        SERVER("Clinic Server Host (Share Clinic DB on LAN / Remote)"),
        CLIENT("Client Terminal (Connect to Clinic Server DB)");

        private final String displayName;
        DeploymentMode(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override public String toString() { return displayName; }
    }

    public static final int DEFAULT_RPC_PORT = 45825;
    private static final String FILE_NAME = "workstation_mode.properties";
    private static final String KEY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public static DeploymentProfile defaultStandalone() {
        return new DeploymentProfile(
                DeploymentMode.STANDALONE,
                "127.0.0.1",
                DEFAULT_RPC_PORT,
                generatePairingKey(),
                "OpenHomeo Clinic Workstation"
        );
    }

    public static String generatePairingKey() {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder("OHC-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(KEY_CHARS.charAt(rnd.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }

    public static DeploymentProfile load(String baseDataDir) {
        File file = new File(baseDataDir, FILE_NAME);
        if (!file.exists()) {
            DeploymentProfile def = defaultStandalone();
            save(baseDataDir, def);
            return def;
        }
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            p.load(r);
            DeploymentMode mode = DeploymentMode.valueOf(p.getProperty("mode", "STANDALONE").trim());
            String host = p.getProperty("serverHost", "127.0.0.1").trim();
            int port = Integer.parseInt(p.getProperty("serverPort", String.valueOf(DEFAULT_RPC_PORT)).trim());
            String key = p.getProperty("pairingKey", "").trim();
            if (key.isBlank()) key = generatePairingKey();
            String label = p.getProperty("clinicLabel", "OpenHomeo Clinic Server").trim();
            return new DeploymentProfile(mode, host, port, key, label);
        } catch (Exception e) {
            return defaultStandalone();
        }
    }

    public static void save(String baseDataDir, DeploymentProfile profile) {
        File dir = new File(baseDataDir);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, FILE_NAME);
        Properties p = new Properties();
        p.setProperty("mode", profile.mode().name());
        p.setProperty("serverHost", profile.serverHost() != null ? profile.serverHost().trim() : "127.0.0.1");
        p.setProperty("serverPort", String.valueOf(profile.serverPort() > 0 ? profile.serverPort() : DEFAULT_RPC_PORT));
        p.setProperty("pairingKey", (profile.pairingKey() != null && !profile.pairingKey().isBlank()) ? profile.pairingKey().trim() : generatePairingKey());
        p.setProperty("clinicLabel", profile.clinicLabel() != null ? profile.clinicLabel().trim() : "OpenHomeo Clinic Server");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            p.store(w, "OpenHomeo Workstation Deployment Mode & Shared Server Config");
        } catch (IOException ignored) {}
    }

    public String getBaseUrl() {
        return "http://" + serverHost + ":" + serverPort;
    }
}
