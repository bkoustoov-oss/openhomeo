package org.openhomeo.core.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.model.NetworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Manager for Clinic Server RPC verification, SSH tunnels, reverse SSH (autossh),
 * and remote clinic database schema verification.
 */
public class RemoteDatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteDatabaseManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record ConnectionTestResult(
            boolean isSuccessful,
            String message,
            long pingMs
    ) {}

    /**
     * Tests raw TCP socket connectivity to the target host and port.
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
     * Tests full authenticated OpenHomeo Clinic Network Server RPC endpoint (/clinic-rpc/ping & /auth-check).
     */
    public static ConnectionTestResult testClinicRpcEndpoint(String host, int port, String pairingKey, int timeoutMs) {
        long start = System.currentTimeMillis();
        String base = "http://" + host.trim() + ":" + port;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest pingReq = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/clinic-rpc/ping"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> pingResp = client.send(pingReq, HttpResponse.BodyHandlers.ofString());
            if (pingResp.statusCode() != 200) {
                return new ConnectionTestResult(false, "Server responded HTTP " + pingResp.statusCode() + " at " + base, -1);
            }
            JsonNode pingJson = MAPPER.readTree(pingResp.body());
            String clinicName = pingJson.path("clinicName").asText("OpenHomeo Clinic Server");

            if (pairingKey != null && !pairingKey.isBlank()) {
                HttpRequest authReq = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/clinic-rpc/auth-check"))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("X-OpenHomeo-Key", pairingKey.trim())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<String> authResp = client.send(authReq, HttpResponse.BodyHandlers.ofString());
                if (authResp.statusCode() != 200) {
                    return new ConnectionTestResult(false, "Server reached ('" + clinicName + "'), but Pairing Key was rejected (HTTP " + authResp.statusCode() + ").", -1);
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            return new ConnectionTestResult(true, "Connected to Shared Clinic DB Server '" + clinicName + "' at " + host + ":" + port + " (" + elapsed + " ms)", elapsed);
        } catch (Exception e) {
            log.warn("Clinic RPC test error for {}:{} -> {}", host, port, e.getMessage());
            return new ConnectionTestResult(false, "Cannot reach OpenHomeo Clinic Server at " + base + ": " + e.getMessage(), -1);
        }
    }

    /**
     * Generates reverse SSH (autossh) shell command for hosting a clinic behind NAT/firewall
     * and client-side SSH forward command for remote practitioners.
     */
    public static String buildAutoSshCommand(NetworkConfig cfg) {
        String sshKeyParam = (cfg.sshKeyPath() != null && !cfg.sshKeyPath().isBlank())
                ? "-i \"" + cfg.sshKeyPath() + "\" " : "";
        int rpcPort = cfg.remotePort() > 0 ? cfg.remotePort() : DeploymentProfile.DEFAULT_RPC_PORT;
        int revPort = cfg.reverseSshPort() > 0 ? cfg.reverseSshPort() : rpcPort;

        return String.format(
                "# On Clinic Server (expose local port %d over reverse SSH):\n" +
                "autossh -M 0 -N -o \"ServerAliveInterval 30\" -o \"ServerAliveCountMax 3\" " +
                "-R %d:127.0.0.1:%d %s%s@%s -p %d\n\n" +
                "# On Remote Doctor Client PC (tunnel %d to localhost, then connect Client to 127.0.0.1:%d):\n" +
                "ssh -N -L %d:127.0.0.1:%d %s%s@%s -p %d",
                rpcPort, revPort, rpcPort, sshKeyParam, cfg.sshUser(), cfg.sshHost(), cfg.sshPort(),
                rpcPort, rpcPort, rpcPort, revPort, sshKeyParam, cfg.sshUser(), cfg.sshHost(), cfg.sshPort()
        );
    }

    /**
     * Verifies and initializes clinic schema against the active DatabaseManager (local or remote Server RPC).
     */
    public static ConnectionTestResult verifyActiveClinicSchema(DatabaseManager dbManager) {
        if (dbManager == null) {
            return new ConnectionTestResult(false, "DatabaseManager unavailable.", -1);
        }
        long t0 = System.currentTimeMillis();
        try {
            dbManager.initializeClinicSchema();
            long elapsed = System.currentTimeMillis() - t0;
            String mode = dbManager.isClientMode()
                    ? "Remote Clinic Server (" + dbManager.getDeploymentProfile().getBaseUrl() + ")"
                    : "Local / Host Clinic DB";
            return new ConnectionTestResult(true, "Clinic schema & tables validated cleanly on " + mode + " (" + elapsed + " ms).", elapsed);
        } catch (Exception e) {
            log.error("Failed verifying active clinic schema", e);
            return new ConnectionTestResult(false, "Schema verification error: " + e.getMessage(), -1);
        }
    }

    public static ConnectionTestResult generateRemoteDbSchema(String jdbcUrl, String username, String password) {
        return new ConnectionTestResult(true, "Use verifyActiveClinicSchema(DatabaseManager) with Client/Server RPC mode for multi-terminal sharing.", 0);
    }
}
