package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record for network connectivity, LAN auto-discovery, and remote SSH/reverse SSH configuration.
 */
public record NetworkConfig(
        ConnectionMode connectionMode,
        String remoteHost,
        int remotePort,
        String remoteDbName,
        String dbUsername,
        String dbPassword,
        String sshHost,
        int sshPort,
        String sshUser,
        String sshKeyPath,
        int reverseSshPort,
        boolean autoSshEnabled,
        boolean lanDiscoveryActive,
        String lastConnectionStatus,
        Instant lastTestedAt
) {
    public enum ConnectionMode {
        LOCAL_EMBEDDED,       // Local SQLite on current workstation
        LAN_CLIENT_AUTOCONNECT,// Automatically discover clinic server on LAN via UDP beacon
        LAN_DEDICATED_HOST,   // Running as the designated clinic LAN database server
        REMOTE_SSH_TUNNEL     // Remote clinic database via secure SSH / Reverse SSH (autossh)
    }

    public static NetworkConfig defaultLocal() {
        return new NetworkConfig(
                ConnectionMode.LOCAL_EMBEDDED,
                "127.0.0.1",
                45824,
                "user_clinic.db",
                "admin",
                "",
                "127.0.0.1",
                22,
                "homeo_admin",
                "~/.ssh/id_rsa",
                45824,
                false,
                true,
                "Local embedded database ready",
                Instant.now()
        );
    }
}
