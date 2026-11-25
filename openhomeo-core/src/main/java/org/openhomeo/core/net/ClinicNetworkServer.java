package org.openhomeo.core.net;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.openhomeo.core.image.ImageVaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.sql.Date;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Authoritative Clinic Database & Vault Network Server.
 * Exposes the Server workstation's single SQLite user_clinic.db and Clinical Image Vault
 * over authenticated HTTP/JSON RPC on LAN or Remote SSH tunnels, guaranteeing zero
 * split-brain data and serialized WAL write safety across concurrent client terminals.
 */
public class ClinicNetworkServer {
    private static final Logger log = LoggerFactory.getLogger(ClinicNetworkServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String clinicDbPath;
    private final String encryptionKey;
    private final ImageVaultService vaultService;
    private final int port;
    private final String pairingKey;
    private final String clinicName;

    private final Object dbLock = new Object();
    private final Map<String, TxEntry> activeTransactions = new ConcurrentHashMap<>();
    private final AtomicInteger totalRpcCalls = new AtomicInteger(0);
    private final Set<String> connectedClientIps = ConcurrentHashMap.newKeySet();

    private HttpServer httpServer;
    private NetworkDiscoveryService discoveryBeacon;
    private ScheduledExecutorService txCleaner;
    private volatile boolean running = false;

    private record TxEntry(Connection conn, long lastUsedMs) {
        TxEntry touch() { return new TxEntry(conn, System.currentTimeMillis()); }
    }

    public record SqlParam(String type, Object value) {}

    public ClinicNetworkServer(String clinicDbPath, String encryptionKey, ImageVaultService vaultService,
                               int port, String pairingKey, String clinicName) {
        this.clinicDbPath = clinicDbPath;
        this.encryptionKey = encryptionKey;
        this.vaultService = vaultService;
        this.port = port;
        this.pairingKey = pairingKey;
        this.clinicName = (clinicName != null && !clinicName.isBlank()) ? clinicName : "OpenHomeo Clinic Server";
    }

    public synchronized void start() throws IOException {
        if (running) return;
        httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        httpServer.createContext("/clinic-rpc/ping", this::handlePing);
        httpServer.createContext("/clinic-rpc/auth-check", this::handleAuthCheck);
        httpServer.createContext("/clinic-rpc/tx", this::handleTransaction);
        httpServer.createContext("/clinic-rpc/exec", this::handleExec);
        httpServer.createContext("/clinic-rpc/vault/store", this::handleVaultStore);
        httpServer.createContext("/clinic-rpc/vault/image", this::handleVaultFetchImage);
        httpServer.createContext("/clinic-rpc/vault/thumb", this::handleVaultFetchThumb);

        httpServer.start();
        running = true;

        discoveryBeacon = new NetworkDiscoveryService();
        discoveryBeacon.startBeaconServer(clinicName, port);

        txCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "openhomeo-tx-cleaner");
            t.setDaemon(true);
            return t;
        });
        txCleaner.scheduleAtFixedRate(this::evictExpiredTransactions, 30, 30, TimeUnit.SECONDS);

        log.info("Clinic Network Database Server started on port {} (LAN IP: {})", port, org.openhomeo.core.server.MobileCompanionServer.getLocalLanIp());
    }

    public synchronized void stop() {
        running = false;
        if (discoveryBeacon != null) {
            discoveryBeacon.stopBeaconServer();
            discoveryBeacon = null;
        }
        if (txCleaner != null) {
            txCleaner.shutdownNow();
            txCleaner = null;
        }
        for (Map.Entry<String, TxEntry> e : activeTransactions.entrySet()) {
            try { e.getValue().conn().rollback(); e.getValue().conn().close(); } catch (Exception ignored) {}
        }
        activeTransactions.clear();
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        log.info("Clinic Network Database Server stopped.");
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }
    public String getPairingKey() { return pairingKey; }
    public int getTotalRpcCalls() { return totalRpcCalls.get(); }
    public Set<String> getConnectedClientIps() { return Collections.unmodifiableSet(connectedClientIps); }

    private Connection openLocalConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + clinicDbPath);
        try (Statement s = conn.createStatement()) {
            if (encryptionKey != null && !encryptionKey.isBlank()) {
                s.execute("PRAGMA key = '" + encryptionKey.replace("'", "''") + "';");
            }
            s.execute("PRAGMA busy_timeout = 10000;");
            s.execute("PRAGMA journal_mode = WAL;");
            s.execute("PRAGMA foreign_keys = ON;");
            s.execute("PRAGMA synchronous = NORMAL;");
        }
        return conn;
    }

    private boolean isAuthorized(HttpExchange ex) {
        if (pairingKey == null || pairingKey.isBlank()) return true;
        String provided = ex.getRequestHeaders().getFirst("X-OpenHomeo-Key");
        return pairingKey.equals(provided != null ? provided.trim() : "");
    }

    private void recordClient(HttpExchange ex) {
        if (ex.getRemoteAddress() != null && ex.getRemoteAddress().getAddress() != null) {
            connectedClientIps.add(ex.getRemoteAddress().getAddress().getHostAddress());
        }
        totalRpcCalls.incrementAndGet();
    }

    private void handlePing(HttpExchange ex) throws IOException {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "ONLINE");
        info.put("server", "OpenHomeo Clinic Network Server");
        info.put("clinicName", clinicName);
        info.put("port", port);
        info.put("lanIp", org.openhomeo.core.server.MobileCompanionServer.getLocalLanIp());
        info.put("activeClients", connectedClientIps.size());
        info.put("totalRpcCalls", totalRpcCalls.get());
        info.put("timestamp", Instant.now().toString());
        sendJson(ex, 200, info);
    }

    private void handleAuthCheck(HttpExchange ex) throws IOException {
        if (!isAuthorized(ex)) {
            sendJson(ex, 403, Map.of("authenticated", false, "error", "Invalid Clinic Pairing Key"));
            return;
        }
        recordClient(ex);
        sendJson(ex, 200, Map.of("authenticated", true, "clinicName", clinicName, "port", port));
    }

    private void handleTransaction(HttpExchange ex) throws IOException {
        if (!isAuthorized(ex)) {
            sendJson(ex, 403, Map.of("error", "Unauthorized: Invalid pairing key"));
            return;
        }
        recordClient(ex);
        try {
            Map<String, Object> req = MAPPER.readValue(ex.getRequestBody(), new TypeReference<>() {});
            String action = String.valueOf(req.getOrDefault("action", "")).toUpperCase();
            String txId = req.get("txId") != null ? req.get("txId").toString() : null;

            switch (action) {
                case "BEGIN" -> {
                    Connection conn = openLocalConnection();
                    conn.setAutoCommit(false);
                    String newTxId = UUID.randomUUID().toString();
                    activeTransactions.put(newTxId, new TxEntry(conn, System.currentTimeMillis()));
                    sendJson(ex, 200, Map.of("txId", newTxId));
                }
                case "COMMIT" -> {
                    TxEntry entry = txId != null ? activeTransactions.remove(txId) : null;
                    if (entry != null) {
                        synchronized (dbLock) {
                            try { entry.conn().commit(); } finally { entry.conn().close(); }
                        }
                    }
                    sendJson(ex, 200, Map.of("status", "COMMITTED"));
                }
                case "ROLLBACK" -> {
                    TxEntry entry = txId != null ? activeTransactions.remove(txId) : null;
                    if (entry != null) {
                        synchronized (dbLock) {
                            try { entry.conn().rollback(); } finally { entry.conn().close(); }
                        }
                    }
                    sendJson(ex, 200, Map.of("status", "ROLLED_BACK"));
                }
                default -> sendJson(ex, 400, Map.of("error", "Unknown transaction action: " + action));
            }
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    private void handleExec(HttpExchange ex) throws IOException {
        if (!isAuthorized(ex)) {
            sendJson(ex, 403, Map.of("error", "Unauthorized: Invalid pairing key"));
            return;
        }
        recordClient(ex);
        try {
            Map<String, Object> req = MAPPER.readValue(ex.getRequestBody(), new TypeReference<>() {});
            String txId = req.get("txId") != null ? req.get("txId").toString() : null;
            String sql = req.get("sql") != null ? req.get("sql").toString() : "";
            String op = req.get("op") != null ? req.get("op").toString().toUpperCase() : "QUERY";

            String trimmedSql = sql.trim().toUpperCase(Locale.ROOT);
            // Ignore per-connection PRAGMA overrides sent transparently by client helpers
            if (trimmedSql.startsWith("PRAGMA KEY") || trimmedSql.startsWith("PRAGMA JOURNAL_MODE")
                    || trimmedSql.startsWith("PRAGMA SYNCHRONOUS") || trimmedSql.startsWith("PRAGMA FOREIGN_KEYS")) {
                sendJson(ex, 200, Map.of("columns", List.of(), "rows", List.of(), "updateCount", 0, "booleanResult", false));
                return;
            }

            List<Map<String, Object>> params = castParamList(req.get("params"));
            List<List<Map<String, Object>>> batchParams = castBatchList(req.get("batchParams"));

            TxEntry txEntry = txId != null ? activeTransactions.get(txId) : null;
            if (txEntry != null) {
                activeTransactions.put(txId, txEntry.touch());
            }

            synchronized (dbLock) {
                Connection conn = txEntry != null ? txEntry.conn() : openLocalConnection();
                boolean closeAfter = txEntry == null;
                try {
                    Map<String, Object> result = executeSql(conn, sql, op, params, batchParams);
                    sendJson(ex, 200, result);
                } finally {
                    if (closeAfter) {
                        try { conn.close(); } catch (SQLException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Clinic RPC SQL error: {}", e.getMessage());
            sendJson(ex, 500, Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    private Map<String, Object> executeSql(Connection conn, String sql, String op,
                                           List<Map<String, Object>> params,
                                           List<List<Map<String, Object>>> batchParams) throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();

        if ("BATCH".equals(op)) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (batchParams != null) {
                    for (List<Map<String, Object>> rowParams : batchParams) {
                        bindParams(ps, rowParams);
                        ps.addBatch();
                    }
                }
                int[] counts = ps.executeBatch();
                out.put("batchCounts", Arrays.stream(counts).boxed().toList());
                out.put("updateCount", Arrays.stream(counts).sum());
            }
            return out;
        }

        boolean hasParams = params != null && !params.isEmpty();
        if ("QUERY".equals(op)) {
            if (hasParams) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        extractResultSet(rs, out);
                    }
                }
            } else {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(sql)) {
                    extractResultSet(rs, out);
                }
            }
            return out;
        }

        if ("UPDATE".equals(op)) {
            int upd;
            if (hasParams) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindParams(ps, params);
                    upd = ps.executeUpdate();
                }
            } else {
                try (Statement st = conn.createStatement()) {
                    upd = st.executeUpdate(sql);
                }
            }
            out.put("updateCount", upd);
            return out;
        }

        // EXECUTE
        boolean hasRs;
        if (hasParams) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindParams(ps, params);
                hasRs = ps.execute();
                if (hasRs) {
                    try (ResultSet rs = ps.getResultSet()) { extractResultSet(rs, out); }
                } else {
                    out.put("updateCount", ps.getUpdateCount());
                }
            }
        } else {
            try (Statement st = conn.createStatement()) {
                hasRs = st.execute(sql);
                if (hasRs) {
                    try (ResultSet rs = st.getResultSet()) { extractResultSet(rs, out); }
                } else {
                    out.put("updateCount", st.getUpdateCount());
                }
            }
        }
        out.put("booleanResult", hasRs);
        return out;
    }

    private void bindParams(PreparedStatement ps, List<Map<String, Object>> params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) {
            Map<String, Object> p = params.get(i);
            String type = p != null && p.get("type") != null ? p.get("type").toString() : "NULL";
            Object val = p != null ? p.get("value") : null;
            int idx = i + 1;
            if (val == null || "NULL".equals(type)) {
                int sqlType = (val instanceof Integer) ? ((Integer) val) : Types.VARCHAR;
                ps.setNull(idx, sqlType);
            } else {
                switch (type) {
                    case "STRING" -> ps.setString(idx, val.toString());
                    case "INT" -> ps.setInt(idx, ((Number) val).intValue());
                    case "LONG" -> ps.setLong(idx, ((Number) val).longValue());
                    case "DOUBLE" -> ps.setDouble(idx, ((Number) val).doubleValue());
                    case "BOOLEAN" -> ps.setBoolean(idx, Boolean.parseBoolean(val.toString()));
                    case "DATE" -> ps.setDate(idx, Date.valueOf(val.toString()));
                    case "TIMESTAMP" -> ps.setTimestamp(idx, new Timestamp(((Number) val).longValue()));
                    default -> ps.setObject(idx, val);
                }
            }
        }
    }

    private void extractResultSet(ResultSet rs, Map<String, Object> out) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
        }
        out.put("columns", columns);
        out.put("rows", rows);
    }

    private void handleVaultStore(HttpExchange ex) throws IOException {
        if (!isAuthorized(ex)) {
            sendJson(ex, 403, Map.of("error", "Unauthorized"));
            return;
        }
        if (vaultService == null) {
            sendJson(ex, 500, Map.of("error", "Vault service unavailable"));
            return;
        }
        recordClient(ex);
        try {
            String query = ex.getRequestURI().getQuery();
            String format = "JPEG";
            if (query != null && query.contains("format=")) {
                for (String part : query.split("&")) {
                    if (part.startsWith("format=")) format = part.substring(7);
                }
            }
            byte[] bytes = ex.getRequestBody().readAllBytes();
            ImageVaultService.StoreResult res = vaultService.storeImage(bytes, format);
            sendJson(ex, 200, Map.of(
                    "contentHash", res.contentHash(),
                    "width", res.width(),
                    "height", res.height(),
                    "fileFormat", res.fileFormat(),
                    "fileSizeBytes", res.fileSizeBytes()
            ));
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleVaultFetchImage(HttpExchange ex) throws IOException {
        handleVaultBinaryFetch(ex, false);
    }

    private void handleVaultFetchThumb(HttpExchange ex) throws IOException {
        handleVaultBinaryFetch(ex, true);
    }

    private void handleVaultBinaryFetch(HttpExchange ex, boolean thumb) throws IOException {
        if (!isAuthorized(ex)) {
            sendBytes(ex, 403, new byte[0], "text/plain");
            return;
        }
        recordClient(ex);
        String hash = extractQueryParam(ex.getRequestURI().getQuery(), "hash");
        if (hash == null || hash.isBlank() || vaultService == null) {
            sendBytes(ex, 404, new byte[0], "text/plain");
            return;
        }
        try {
            byte[] data = thumb
                    ? Files.readAllBytes(vaultService.getThumbnailFile(hash).toPath())
                    : vaultService.retrieveImage(hash);
            sendBytes(ex, 200, data, thumb ? "image/png" : "application/octet-stream");
        } catch (Exception e) {
            sendBytes(ex, 404, new byte[0], "text/plain");
        }
    }

    private String extractQueryParam(String query, String key) {
        if (query == null) return null;
        for (String p : query.split("&")) {
            if (p.startsWith(key + "=")) return p.substring(key.length() + 1);
        }
        return null;
    }

    private void evictExpiredTransactions() {
        long cutoff = System.currentTimeMillis() - 120_000L;
        for (Map.Entry<String, TxEntry> e : activeTransactions.entrySet()) {
            if (e.getValue().lastUsedMs() < cutoff) {
                TxEntry removed = activeTransactions.remove(e.getKey());
                if (removed != null) {
                    try { removed.conn().rollback(); removed.conn().close(); } catch (SQLException ignored) {}
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castParamList(Object o) {
        return o instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<List<Map<String, Object>>> castBatchList(Object o) {
        return o instanceof List<?> list ? (List<List<Map<String, Object>>>) list : List.of();
    }

    private void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(obj);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void sendBytes(HttpExchange ex, int status, byte[] bytes, String mime) throws IOException {
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
