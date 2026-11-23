package org.openhomeo.core.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Zero-configuration LAN Discovery Service for OpenHomeo Clinic Workstations.
 * Allows client workstations (Doctors, Pharmacists) to automatically discover
 * and connect to the designated Clinic Database Host over local Wi-Fi / LAN.
 */
public class NetworkDiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(NetworkDiscoveryService.class);
    public static final int DEFAULT_DISCOVERY_PORT = 45824;
    private static final String PROBE_HEADER = "OPENHOMEO_PROBE_V1";
    private static final String BEACON_HEADER = "OPENHOMEO_BEACON_V1";

    public record DiscoveredClinicHost(
            String hostName,
            String hostIp,
            String clinicName,
            int port,
            String dbMode,
            long responseTimeMs
    ) {}

    private final AtomicBoolean beaconRunning = new AtomicBoolean(false);
    private DatagramSocket serverSocket;
    private Thread serverThread;

    /**
     * Starts the UDP beacon listener on the primary Clinic Host workstation.
     */
    public synchronized void startBeaconServer(String clinicName, int servicePort) {
        if (beaconRunning.get()) return;

        try {
            serverSocket = new DatagramSocket(null);
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(DEFAULT_DISCOVERY_PORT));
            beaconRunning.set(true);

            serverThread = new Thread(() -> {
                log.info("Clinic LAN discovery beacon started on UDP port {}", DEFAULT_DISCOVERY_PORT);
                byte[] buffer = new byte[1024];
                while (beaconRunning.get() && !serverSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        serverSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                        if (msg.startsWith(PROBE_HEADER)) {
                            String hostName = InetAddress.getLocalHost().getHostName();
                            String response = String.format("%s;hostName=%s;clinicName=%s;port=%d;dbMode=SHARED",
                                    BEACON_HEADER, hostName, clinicName != null ? clinicName : "OpenHomeo Clinic", servicePort);
                            byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                            DatagramPacket respPacket = new DatagramPacket(respBytes, respBytes.length, packet.getAddress(), packet.getPort());
                            serverSocket.send(respPacket);
                            log.debug("Responded to discovery probe from {}:{}", packet.getAddress(), packet.getPort());
                        }
                    } catch (IOException e) {
                        if (!beaconRunning.get()) break;
                        log.warn("Error in beacon server loop: {}", e.getMessage());
                    }
                }
            }, "OpenHomeo-Beacon-Server");
            serverThread.setDaemon(true);
            serverThread.start();
        } catch (Exception e) {
            log.warn("Could not bind discovery beacon server on port {}: {}", DEFAULT_DISCOVERY_PORT, e.getMessage());
        }
    }

    /**
     * Stops the beacon listener.
     */
    public synchronized void stopBeaconServer() {
        beaconRunning.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
        log.info("Clinic LAN discovery beacon stopped.");
    }

    public boolean isBeaconRunning() {
        return beaconRunning.get();
    }

    /**
     * Probes the local subnet via UDP broadcast and awaits a clinic host response.
     */
    public static Optional<DiscoveredClinicHost> discoverClinicHost(int timeoutMs) {
        long start = System.currentTimeMillis();
        byte[] probe = PROBE_HEADER.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs);

            DatagramPacket packet = new DatagramPacket(
                    probe, probe.length,
                    InetAddress.getByName("255.255.255.255"),
                    DEFAULT_DISCOVERY_PORT
            );
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);

            long elapsed = System.currentTimeMillis() - start;
            String text = new String(responsePacket.getData(), 0, responsePacket.getLength(), StandardCharsets.UTF_8).trim();

            if (text.startsWith(BEACON_HEADER)) {
                String hostIp = responsePacket.getAddress().getHostAddress();
                String hostName = parseField(text, "hostName", hostIp);
                String clinicName = parseField(text, "clinicName", "OpenHomeo LAN Server");
                int port = Integer.parseInt(parseField(text, "port", String.valueOf(DEFAULT_DISCOVERY_PORT)));
                String dbMode = parseField(text, "dbMode", "SHARED");

                return Optional.of(new DiscoveredClinicHost(hostName, hostIp, clinicName, port, dbMode, elapsed));
            }
        } catch (SocketTimeoutException e) {
            log.info("No clinic LAN host responded to broadcast probe within {} ms", timeoutMs);
        } catch (Exception e) {
            log.warn("LAN host auto-discovery probe error: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static String parseField(String payload, String field, String fallback) {
        String key = field + "=";
        for (String part : payload.split(";")) {
            if (part.startsWith(key)) {
                return part.substring(key.length()).trim();
            }
        }
        return fallback;
    }
}
