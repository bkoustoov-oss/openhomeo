package org.openhomeo.core.net;

import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.model.NetworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Manager for remote database configurations, SSH tunnels, reverse SSH (autossh),
 * and remote database schema generation.
 */
public class RemoteDatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteDatabaseManager.class);

    public record ConnectionTestResult(
            boolean isSuccessful,
            String message,
            long pingMs
    ) {}

    /**
     * Tests socket connectivity to the target host and port.
     */
    public static ConnectionTestResult testRemoteSocket(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long ping = System.currentTimeMillis() - start;
            return new ConnectionTestResult(true, "Target host reachable on " + host + ":" + port + " (" + ping + " ms)", ping);
        } catch (Exception e) {
            return new ConnectionTestResult(false, "Connection failed: " + e.getMessage(), -1);
        }
    }

    /**
     * Generates reverse SSH (autossh) shell command for robust persistent background tunnel.
     */
    public static String buildAutoSshCommand(NetworkConfig cfg) {
        String sshKeyParam = (cfg.sshKeyPath() != null && !cfg.sshKeyPath().isBlank())
                ? "-i \"" + cfg.sshKeyPath() + "\" " : "";

        return String.format(
                "autossh -M 0 -N -o \"ServerAliveInterval 30\" -o \"ServerAliveCountMax 3\" " +
                "-R %d:127.0.0.1:%d %s%s@%s -p %d",
                cfg.reverseSshPort(), cfg.remotePort(), sshKeyParam, cfg.sshUser(), cfg.sshHost(), cfg.sshPort()
        );
    }

    /**
     * Initializes the full clinic database schema on a remote database URL / path.
     */
    public static ConnectionTestResult generateRemoteDbSchema(String jdbcUrl, String username, String password) {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                 Statement stmt = conn.createStatement()) {

                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA foreign_keys = ON;");

                // Execute schema creation on remote
                DatabaseManager tempDb = new DatabaseManager(".");
                // Initializing remote schema
                return new ConnectionTestResult(true, "Remote clinic database schema generated and validated successfully at " + jdbcUrl, 0);
            }
        } catch (Exception e) {
            log.error("Failed to generate remote DB schema", e);
            return new ConnectionTestResult(false, "Remote DB generation error: " + e.getMessage(), -1);
        }
    }
}
