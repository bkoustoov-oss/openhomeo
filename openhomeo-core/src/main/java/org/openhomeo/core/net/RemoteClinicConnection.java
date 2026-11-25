package org.openhomeo.core.net;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * JDBC Connection proxy that transparently forwards all SQL operations and transactions
 * over HTTP/JSON to the Clinic Network Server hosting the shared user_clinic.db.
 */
public class RemoteClinicConnection implements Connection {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String baseUrl;
    private final String pairingKey;
    private boolean autoCommit = true;
    private String activeTxId = null;
    private boolean closed = false;

    public RemoteClinicConnection(String baseUrl, String pairingKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.pairingKey = pairingKey != null ? pairingKey : "";
    }

    private Map<String, Object> postJson(String path, Map<String, Object> payload) throws SQLException {
        if (closed) throw new SQLException("Remote clinic connection is closed.");
        try {
            String json = MAPPER.writeValueAsString(payload);
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (!pairingKey.isBlank()) {
                req.header("X-OpenHomeo-Key", pairingKey);
            }
            HttpResponse<String> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> map = MAPPER.readValue(resp.body(), new TypeReference<>() {});
            if (resp.statusCode() >= 400) {
                throw new SQLException("Clinic Server RPC Error (" + resp.statusCode() + "): " + map.getOrDefault("error", resp.body()));
            }
            return map;
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Remote Clinic Server unreachable at " + baseUrl + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        return new RemoteStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new RemotePreparedStatement(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        if (this.autoCommit == autoCommit) return;
        if (!autoCommit) {
            Map<String, Object> res = postJson("/clinic-rpc/tx", Map.of("action", "BEGIN"));
            this.activeTxId = String.valueOf(res.get("txId"));
            this.autoCommit = false;
        } else {
            if (this.activeTxId != null) {
                commit();
            }
            this.autoCommit = true;
        }
    }

    @Override
    public boolean getAutoCommit() { return autoCommit; }

    @Override
    public void commit() throws SQLException {
        if (activeTxId != null) {
            String tx = activeTxId;
            activeTxId = null;
            postJson("/clinic-rpc/tx", Map.of("action", "COMMIT", "txId", tx));
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (activeTxId != null) {
            String tx = activeTxId;
            activeTxId = null;
            postJson("/clinic-rpc/tx", Map.of("action", "ROLLBACK", "txId", tx));
        }
    }

    @Override
    public void close() throws SQLException {
        if (activeTxId != null) {
            try { rollback(); } catch (SQLException ignored) {}
        }
        closed = true;
    }

    @Override public boolean isClosed() { return closed; }

    // --- Unsupported / Boilerplate Connection overrides ---
    @Override public CallableStatement prepareCall(String sql) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public String nativeSQL(String sql) { return sql; }
    @Override public DatabaseMetaData getMetaData() { return null; }
    @Override public void setReadOnly(boolean readOnly) {}
    @Override public boolean isReadOnly() { return false; }
    @Override public void setCatalog(String catalog) {}
    @Override public String getCatalog() { return null; }
    @Override public void setTransactionIsolation(int level) {}
    @Override public int getTransactionIsolation() { return TRANSACTION_SERIALIZABLE; }
    @Override public SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() {}
    @Override public Statement createStatement(int a, int b) throws SQLException { return createStatement(); }
    @Override public PreparedStatement prepareStatement(String s, int a, int b) throws SQLException { return prepareStatement(s); }
    @Override public CallableStatement prepareCall(String s, int a, int b) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Map<String, Class<?>> getTypeMap() { return Map.of(); }
    @Override public void setTypeMap(Map<String, Class<?>> map) {}
    @Override public void setHoldability(int h) {}
    @Override public int getHoldability() { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public Savepoint setSavepoint() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Savepoint setSavepoint(String name) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void rollback(Savepoint savepoint) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void releaseSavepoint(Savepoint savepoint) {}
    @Override public Statement createStatement(int a, int b, int c) throws SQLException { return createStatement(); }
    @Override public PreparedStatement prepareStatement(String s, int a, int b, int c) throws SQLException { return prepareStatement(s); }
    @Override public CallableStatement prepareCall(String s, int a, int b, int c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public PreparedStatement prepareStatement(String s, int a) throws SQLException { return prepareStatement(s); }
    @Override public PreparedStatement prepareStatement(String s, int[] a) throws SQLException { return prepareStatement(s); }
    @Override public PreparedStatement prepareStatement(String s, String[] a) throws SQLException { return prepareStatement(s); }
    @Override public Clob createClob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Blob createBlob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public NClob createNClob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public SQLXML createSQLXML() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean isValid(int timeout) { return !closed; }
    @Override public void setClientInfo(String n, String v) {}
    @Override public void setClientInfo(Properties p) {}
    @Override public String getClientInfo(String n) { return null; }
    @Override public Properties getClientInfo() { return new Properties(); }
    @Override public Array createArrayOf(String t, Object[] e) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Struct createStruct(String t, Object[] a) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void setSchema(String schema) {}
    @Override public String getSchema() { return null; }
    @Override public void abort(Executor executor) { closed = true; }
    @Override public void setNetworkTimeout(Executor e, int ms) {}
    @Override public int getNetworkTimeout() { return 30000; }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }

    // =========================================================================
    // REMOTE STATEMENT
    // =========================================================================
    private class RemoteStatement implements Statement {
        protected int updateCount = -1;
        protected ResultSet currentRs = null;
        protected boolean closedStmt = false;

        protected Map<String, Object> execRpc(String sql, String op, List<Map<String, Object>> params, List<List<Map<String, Object>>> batch) throws SQLException {
            Map<String, Object> req = new LinkedHashMap<>();
            if (activeTxId != null) req.put("txId", activeTxId);
            req.put("sql", sql);
            req.put("op", op);
            if (params != null && !params.isEmpty()) req.put("params", params);
            if (batch != null && !batch.isEmpty()) req.put("batchParams", batch);
            return postJson("/clinic-rpc/exec", req);
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            Map<String, Object> res = execRpc(sql, "QUERY", null, null);
            currentRs = toResultSet(res);
            return currentRs;
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            Map<String, Object> res = execRpc(sql, "UPDATE", null, null);
            updateCount = ((Number) res.getOrDefault("updateCount", 0)).intValue();
            return updateCount;
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            Map<String, Object> res = execRpc(sql, "EXECUTE", null, null);
            boolean hasRs = Boolean.TRUE.equals(res.get("booleanResult"));
            if (hasRs) currentRs = toResultSet(res);
            else updateCount = ((Number) res.getOrDefault("updateCount", 0)).intValue();
            return hasRs;
        }

        @SuppressWarnings("unchecked")
        protected ResultSet toResultSet(Map<String, Object> res) {
            List<String> cols = (List<String>) res.getOrDefault("columns", List.of());
            List<List<Object>> rows = (List<List<Object>>) res.getOrDefault("rows", List.of());
            return new RemoteResultSet(cols, rows);
        }

        @Override public void close() { closedStmt = true; }
        @Override public boolean isClosed() { return closedStmt; }
        @Override public int getMaxFieldSize() { return 0; }
        @Override public void setMaxFieldSize(int max) {}
        @Override public int getMaxRows() { return 0; }
        @Override public void setMaxRows(int max) {}
        @Override public void setEscapeProcessing(boolean enable) {}
        @Override public int getQueryTimeout() { return 30; }
        @Override public void setQueryTimeout(int seconds) {}
        @Override public void cancel() {}
        @Override public SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public void setCursorName(String name) {}
        @Override public ResultSet getResultSet() { return currentRs; }
        @Override public int getUpdateCount() { return updateCount; }
        @Override public boolean getMoreResults() { return false; }
        @Override public void setFetchDirection(int d) {}
        @Override public int getFetchDirection() { return ResultSet.FETCH_FORWARD; }
        @Override public void setFetchSize(int rows) {}
        @Override public int getFetchSize() { return 0; }
        @Override public int getResultSetConcurrency() { return ResultSet.CONCUR_READ_ONLY; }
        @Override public int getResultSetType() { return ResultSet.TYPE_FORWARD_ONLY; }
        @Override public void addBatch(String sql) {}
        @Override public void clearBatch() {}
        @Override public int[] executeBatch() throws SQLException { return new int[0]; }
        @Override public Connection getConnection() { return RemoteClinicConnection.this; }
        @Override public boolean getMoreResults(int current) { return false; }
        @Override public ResultSet getGeneratedKeys() { return new RemoteResultSet(List.of(), List.of()); }
        @Override public int executeUpdate(String sql, int a) throws SQLException { return executeUpdate(sql); }
        @Override public int executeUpdate(String sql, int[] a) throws SQLException { return executeUpdate(sql); }
        @Override public int executeUpdate(String sql, String[] a) throws SQLException { return executeUpdate(sql); }
        @Override public boolean execute(String sql, int a) throws SQLException { return execute(sql); }
        @Override public boolean execute(String sql, int[] a) throws SQLException { return execute(sql); }
        @Override public boolean execute(String sql, String[] a) throws SQLException { return execute(sql); }
        @Override public int getResultSetHoldability() { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
        @Override public void setPoolable(boolean poolable) {}
        @Override public boolean isPoolable() { return false; }
        @Override public void closeOnCompletion() {}
        @Override public boolean isCloseOnCompletion() { return false; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    // =========================================================================
    // REMOTE PREPARED STATEMENT
    // =========================================================================
    private class RemotePreparedStatement extends RemoteStatement implements PreparedStatement {
        private final String sql;
        private List<Map<String, Object>> currentParams = new ArrayList<>();
        private final List<List<Map<String, Object>>> batch = new ArrayList<>();

        RemotePreparedStatement(String sql) { this.sql = sql; }

        private void setParam(int index, String type, Object value) {
            while (currentParams.size() < index) currentParams.add(null);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", value == null ? "NULL" : type);
            entry.put("value", value);
            currentParams.set(index - 1, entry);
        }

        @Override public ResultSet executeQuery() throws SQLException {
            Map<String, Object> res = execRpc(sql, "QUERY", currentParams, null);
            currentRs = toResultSet(res);
            return currentRs;
        }

        @Override public int executeUpdate() throws SQLException {
            Map<String, Object> res = execRpc(sql, "UPDATE", currentParams, null);
            updateCount = ((Number) res.getOrDefault("updateCount", 0)).intValue();
            return updateCount;
        }

        @Override public boolean execute() throws SQLException {
            Map<String, Object> res = execRpc(sql, "EXECUTE", currentParams, null);
            boolean hasRs = Boolean.TRUE.equals(res.get("booleanResult"));
            if (hasRs) currentRs = toResultSet(res);
            else updateCount = ((Number) res.getOrDefault("updateCount", 0)).intValue();
            return hasRs;
        }

        @Override public void addBatch() {
            batch.add(new ArrayList<>(currentParams));
            currentParams = new ArrayList<>();
        }

        @Override public void clearBatch() { batch.clear(); }

        @Override @SuppressWarnings("unchecked")
        public int[] executeBatch() throws SQLException {
            if (batch.isEmpty()) return new int[0];
            Map<String, Object> res = execRpc(sql, "BATCH", null, batch);
            batch.clear();
            List<Number> list = (List<Number>) res.getOrDefault("batchCounts", List.of());
            return list.stream().mapToInt(Number::intValue).toArray();
        }

        @Override public void setNull(int idx, int sqlType) { setParam(idx, "NULL", sqlType); }
        @Override public void setNull(int idx, int sqlType, String t) { setParam(idx, "NULL", sqlType); }
        @Override public void setBoolean(int idx, boolean x) { setParam(idx, "BOOLEAN", x); }
        @Override public void setByte(int idx, byte x) { setParam(idx, "INT", (int) x); }
        @Override public void setShort(int idx, short x) { setParam(idx, "INT", (int) x); }
        @Override public void setInt(int idx, int x) { setParam(idx, "INT", x); }
        @Override public void setLong(int idx, long x) { setParam(idx, "LONG", x); }
        @Override public void setFloat(int idx, float x) { setParam(idx, "DOUBLE", (double) x); }
        @Override public void setDouble(int idx, double x) { setParam(idx, "DOUBLE", x); }
        @Override public void setBigDecimal(int idx, BigDecimal x) { setParam(idx, "DOUBLE", x != null ? x.doubleValue() : null); }
        @Override public void setString(int idx, String x) { setParam(idx, "STRING", x); }
        @Override public void setDate(int idx, Date x) { setParam(idx, "DATE", x != null ? x.toString() : null); }
        @Override public void setTimestamp(int idx, Timestamp x) { setParam(idx, "TIMESTAMP", x != null ? x.getTime() : null); }
        @Override public void clearParameters() { currentParams.clear(); }
        @Override public void setObject(int idx, Object x) {
            if (x == null) setParam(idx, "NULL", null);
            else if (x instanceof Integer i) setInt(idx, i);
            else if (x instanceof Long l) setLong(idx, l);
            else if (x instanceof Double d) setDouble(idx, d);
            else if (x instanceof Boolean b) setBoolean(idx, b);
            else if (x instanceof Date d) setDate(idx, d);
            else if (x instanceof Timestamp t) setTimestamp(idx, t);
            else setString(idx, x.toString());
        }
        @Override public void setObject(int idx, Object x, int t) { setObject(idx, x); }
        @Override public void setObject(int idx, Object x, int t, int s) { setObject(idx, x); }

        @Override public void setBytes(int i, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setTime(int i, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setAsciiStream(int i, InputStream x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setUnicodeStream(int i, InputStream x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setBinaryStream(int i, InputStream x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setCharacterStream(int i, Reader r, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setRef(int i, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setBlob(int i, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setClob(int i, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setArray(int i, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public ResultSetMetaData getMetaData() { return null; }
        @Override public ParameterMetaData getParameterMetaData() { return null; }
        @Override public void setDate(int i, Date x, Calendar c) { setDate(i, x); }
        @Override public void setTime(int i, Time x, Calendar c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setTimestamp(int i, Timestamp x, Calendar c) { setTimestamp(i, x); }
        @Override public void setURL(int i, URL x) { setString(i, x != null ? x.toString() : null); }
        @Override public void setRowId(int i, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setNString(int i, String v) { setString(i, v); }
        @Override public void setNCharacterStream(int i, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setNClob(int i, NClob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setClob(int i, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setBlob(int i, InputStream s, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setNClob(int i, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setSQLXML(int i, SQLXML xml) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setAsciiStream(int i, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setBinaryStream(int i, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setCharacterStream(int i, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setAsciiStream(int i, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setBinaryStream(int i, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setCharacterStream(int i, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setNCharacterStream(int i, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setClob(int i, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setBlob(int i, InputStream s) throws SQLException { throw new SQLFeatureNotSupportedException(); }
        @Override public void setNClob(int i, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    }

    // =========================================================================
    // REMOTE RESULT SET
    // =========================================================================
    private static class RemoteResultSet implements ResultSet {
        private final List<String> columns;
        private final Map<String, Integer> colIndexMap = new HashMap<>();
        private final List<List<Object>> rows;
        private int cursor = -1;
        private boolean lastWasNull = false;

        RemoteResultSet(List<String> columns, List<List<Object>> rows) {
            this.columns = columns;
            this.rows = rows;
            for (int i = 0; i < columns.size(); i++) {
                colIndexMap.put(columns.get(i).toLowerCase(Locale.ROOT), i);
            }
        }

        private int colIdx(String label) throws SQLException {
            Integer idx = colIndexMap.get(label.toLowerCase(Locale.ROOT));
            if (idx == null) throw new SQLException("Column not found: " + label);
            return idx;
        }

        private Object rawVal(int oneBasedIdx) throws SQLException {
            if (cursor < 0 || cursor >= rows.size()) throw new SQLException("Cursor out of range");
            Object v = rows.get(cursor).get(oneBasedIdx - 1);
            lastWasNull = (v == null);
            return v;
        }

        @Override public boolean next() { cursor++; return cursor < rows.size(); }
        @Override public void close() {}
        @Override public boolean wasNull() { return lastWasNull; }

        @Override public Object getObject(int i) throws SQLException { return rawVal(i); }
        @Override public Object getObject(String l) throws SQLException { return rawVal(colIdx(l) + 1); }

        @Override public String getString(int i) throws SQLException { Object v = rawVal(i); return v != null ? v.toString() : null; }
        @Override public String getString(String l) throws SQLException { return getString(colIdx(l) + 1); }

        @Override public int getInt(int i) throws SQLException {
            Object v = rawVal(i);
            if (v == null) return 0;
            if (v instanceof Number n) return n.intValue();
            if (v instanceof Boolean b) return b ? 1 : 0;
            return Integer.parseInt(v.toString().trim());
        }
        @Override public int getInt(String l) throws SQLException { return getInt(colIdx(l) + 1); }

        @Override public long getLong(int i) throws SQLException {
            Object v = rawVal(i);
            if (v == null) return 0L;
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(v.toString().trim());
        }
        @Override public long getLong(String l) throws SQLException { return getLong(colIdx(l) + 1); }

        @Override public double getDouble(int i) throws SQLException {
            Object v = rawVal(i);
            if (v == null) return 0.0;
            if (v instanceof Number n) return n.doubleValue();
            return Double.parseDouble(v.toString().trim());
        }
        @Override public double getDouble(String l) throws SQLException { return getDouble(colIdx(l) + 1); }

        @Override public boolean getBoolean(int i) throws SQLException {
            Object v = rawVal(i);
            if (v == null) return false;
            if (v instanceof Boolean b) return b;
            if (v instanceof Number n) return n.intValue() != 0;
            String s = v.toString().trim().toLowerCase(Locale.ROOT);
            return "true".equals(s) || "1".equals(s);
        }
        @Override public boolean getBoolean(String l) throws SQLException { return getBoolean(colIdx(l) + 1); }

        @Override public Date getDate(int i) throws SQLException {
            Object v = rawVal(i);
            if (v == null) return null;
            if (v instanceof Number n) return new Date(n.longValue());
            String s = v.toString().trim();
            if (s.length() >= 10 && s.charAt(4) == '-') {
                return Date.valueOf(LocalDate.parse(s.substring(0, 10)));
            }
            return new Date(Long.parseLong(s));
        }
        @Override public Date getDate(String l) throws SQLException { return getDate(colIdx(l) + 1); }

        @Override public Timestamp getTimestamp(int i) throws SQLException {
            Object v = rawVal(i);
            if (v == null) return null;
            if (v instanceof Number n) return new Timestamp(n.longValue());
            String s = v.toString().trim();
            try { return new Timestamp(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
            try { return Timestamp.from(Instant.parse(s)); } catch (Exception ignored) {}
            return Timestamp.valueOf(s.replace('T', ' ').replace("Z", ""));
        }
        @Override public Timestamp getTimestamp(String l) throws SQLException { return getTimestamp(colIdx(l) + 1); }

        // Unused ResultSet stubs
        @Override public byte getByte(int i) { return 0; }
        @Override public short getShort(int i) { return 0; }
        @Override public float getFloat(int i) throws SQLException { return (float) getDouble(i); }
        @Override public BigDecimal getBigDecimal(int i, int s) { return null; }
        @Override public byte[] getBytes(int i) { return new byte[0]; }
        @Override public Time getTime(int i) { return null; }
        @Override public InputStream getAsciiStream(int i) { return null; }
        @Override public InputStream getUnicodeStream(int i) { return null; }
        @Override public InputStream getBinaryStream(int i) { return null; }
        @Override public byte getByte(String l) { return 0; }
        @Override public short getShort(String l) { return 0; }
        @Override public float getFloat(String l) throws SQLException { return (float) getDouble(l); }
        @Override public BigDecimal getBigDecimal(String l, int s) { return null; }
        @Override public byte[] getBytes(String l) { return new byte[0]; }
        @Override public Time getTime(String l) { return null; }
        @Override public InputStream getAsciiStream(String l) { return null; }
        @Override public InputStream getUnicodeStream(String l) { return null; }
        @Override public InputStream getBinaryStream(String l) { return null; }
        @Override public SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public String getCursorName() { return null; }
        @Override public ResultSetMetaData getMetaData() { return null; }
        @Override public int findColumn(String l) throws SQLException { return colIdx(l) + 1; }
        @Override public Reader getCharacterStream(int i) { return null; }
        @Override public Reader getCharacterStream(String l) { return null; }
        @Override public BigDecimal getBigDecimal(int i) { return null; }
        @Override public BigDecimal getBigDecimal(String l) { return null; }
        @Override public boolean isBeforeFirst() { return cursor < 0; }
        @Override public boolean isAfterLast() { return cursor >= rows.size(); }
        @Override public boolean isFirst() { return cursor == 0; }
        @Override public boolean isLast() { return cursor == rows.size() - 1; }
        @Override public void beforeFirst() { cursor = -1; }
        @Override public void afterLast() { cursor = rows.size(); }
        @Override public boolean first() { cursor = 0; return !rows.isEmpty(); }
        @Override public boolean last() { cursor = rows.size() - 1; return !rows.isEmpty(); }
        @Override public int getRow() { return cursor + 1; }
        @Override public boolean absolute(int r) { cursor = r - 1; return cursor >= 0 && cursor < rows.size(); }
        @Override public boolean relative(int r) { cursor += r; return cursor >= 0 && cursor < rows.size(); }
        @Override public boolean previous() { cursor--; return cursor >= 0; }
        @Override public void setFetchDirection(int d) {}
        @Override public int getFetchDirection() { return FETCH_FORWARD; }
        @Override public void setFetchSize(int rows) {}
        @Override public int getFetchSize() { return 0; }
        @Override public int getType() { return TYPE_FORWARD_ONLY; }
        @Override public int getConcurrency() { return CONCUR_READ_ONLY; }
        @Override public boolean rowUpdated() { return false; }
        @Override public boolean rowInserted() { return false; }
        @Override public boolean rowDeleted() { return false; }
        @Override public void updateNull(int i) {}
        @Override public void updateBoolean(int i, boolean x) {}
        @Override public void updateByte(int i, byte x) {}
        @Override public void updateShort(int i, short x) {}
        @Override public void updateInt(int i, int x) {}
        @Override public void updateLong(int i, long x) {}
        @Override public void updateFloat(int i, float x) {}
        @Override public void updateDouble(int i, double x) {}
        @Override public void updateBigDecimal(int i, BigDecimal x) {}
        @Override public void updateString(int i, String x) {}
        @Override public void updateBytes(int i, byte[] x) {}
        @Override public void updateDate(int i, Date x) {}
        @Override public void updateTime(int i, Time x) {}
        @Override public void updateTimestamp(int i, Timestamp x) {}
        @Override public void updateAsciiStream(int i, InputStream x, int l) {}
        @Override public void updateBinaryStream(int i, InputStream x, int l) {}
        @Override public void updateCharacterStream(int i, Reader x, int l) {}
        @Override public void updateObject(int i, Object x, int s) {}
        @Override public void updateObject(int i, Object x) {}
        @Override public void updateNull(String c) {}
        @Override public void updateBoolean(String c, boolean x) {}
        @Override public void updateByte(String c, byte x) {}
        @Override public void updateShort(String c, short x) {}
        @Override public void updateInt(String c, int x) {}
        @Override public void updateLong(String c, long x) {}
        @Override public void updateFloat(String c, float x) {}
        @Override public void updateDouble(String c, double x) {}
        @Override public void updateBigDecimal(String c, BigDecimal x) {}
        @Override public void updateString(String c, String x) {}
        @Override public void updateBytes(String c, byte[] x) {}
        @Override public void updateDate(String c, Date x) {}
        @Override public void updateTime(String c, Time x) {}
        @Override public void updateTimestamp(String c, Timestamp x) {}
        @Override public void updateAsciiStream(String c, InputStream x, int l) {}
        @Override public void updateBinaryStream(String c, InputStream x, int l) {}
        @Override public void updateCharacterStream(String c, Reader r, int l) {}
        @Override public void updateObject(String c, Object x, int s) {}
        @Override public void updateObject(String c, Object x) {}
        @Override public void insertRow() {}
        @Override public void updateRow() {}
        @Override public void deleteRow() {}
        @Override public void refreshRow() {}
        @Override public void cancelRowUpdates() {}
        @Override public void moveToInsertRow() {}
        @Override public void moveToCurrentRow() {}
        @Override public Statement getStatement() { return null; }
        @Override public Object getObject(int i, Map<String, Class<?>> m) throws SQLException { return getObject(i); }
        @Override public Ref getRef(int i) { return null; }
        @Override public Blob getBlob(int i) { return null; }
        @Override public Clob getClob(int i) { return null; }
        @Override public Array getArray(int i) { return null; }
        @Override public Object getObject(String c, Map<String, Class<?>> m) throws SQLException { return getObject(c); }
        @Override public Ref getRef(String c) { return null; }
        @Override public Blob getBlob(String c) { return null; }
        @Override public Clob getClob(String c) { return null; }
        @Override public Array getArray(String c) { return null; }
        @Override public Date getDate(int i, Calendar c) throws SQLException { return getDate(i); }
        @Override public Date getDate(String l, Calendar c) throws SQLException { return getDate(l); }
        @Override public Time getTime(int i, Calendar c) { return null; }
        @Override public Time getTime(String l, Calendar c) { return null; }
        @Override public Timestamp getTimestamp(int i, Calendar c) throws SQLException { return getTimestamp(i); }
        @Override public Timestamp getTimestamp(String l, Calendar c) throws SQLException { return getTimestamp(l); }
        @Override public URL getURL(int i) { return null; }
        @Override public URL getURL(String c) { return null; }
        @Override public void updateRef(int i, Ref x) {}
        @Override public void updateRef(String c, Ref x) {}
        @Override public void updateBlob(int i, Blob x) {}
        @Override public void updateBlob(String c, Blob x) {}
        @Override public void updateClob(int i, Clob x) {}
        @Override public void updateClob(String c, Clob x) {}
        @Override public void updateArray(int i, Array x) {}
        @Override public void updateArray(String c, Array x) {}
        @Override public RowId getRowId(int i) { return null; }
        @Override public RowId getRowId(String c) { return null; }
        @Override public void updateRowId(int i, RowId x) {}
        @Override public void updateRowId(String c, RowId x) {}
        @Override public int getHoldability() { return CLOSE_CURSORS_AT_COMMIT; }
        @Override public boolean isClosed() { return false; }
        @Override public void updateNString(int i, String v) {}
        @Override public void updateNString(String c, String v) {}
        @Override public void updateNClob(int i, NClob v) {}
        @Override public void updateNClob(String c, NClob v) {}
        @Override public NClob getNClob(int i) { return null; }
        @Override public NClob getNClob(String c) { return null; }
        @Override public SQLXML getSQLXML(int i) { return null; }
        @Override public SQLXML getSQLXML(String c) { return null; }
        @Override public void updateSQLXML(int i, SQLXML x) {}
        @Override public void updateSQLXML(String c, SQLXML x) {}
        @Override public String getNString(int i) throws SQLException { return getString(i); }
        @Override public String getNString(String c) throws SQLException { return getString(c); }
        @Override public Reader getNCharacterStream(int i) { return null; }
        @Override public Reader getNCharacterStream(String c) { return null; }
        @Override public void updateNCharacterStream(int i, Reader x, long l) {}
        @Override public void updateNCharacterStream(String c, Reader x, long l) {}
        @Override public void updateAsciiStream(int i, InputStream x, long l) {}
        @Override public void updateBinaryStream(int i, InputStream x, long l) {}
        @Override public void updateCharacterStream(int i, Reader x, long l) {}
        @Override public void updateAsciiStream(String c, InputStream x, long l) {}
        @Override public void updateBinaryStream(String c, InputStream x, long l) {}
        @Override public void updateCharacterStream(String c, Reader x, long l) {}
        @Override public void updateBlob(int i, InputStream x, long l) {}
        @Override public void updateBlob(String c, InputStream x, long l) {}
        @Override public void updateClob(int i, Reader x, long l) {}
        @Override public void updateClob(String c, Reader x, long l) {}
        @Override public void updateNClob(int i, Reader x, long l) {}
        @Override public void updateNClob(String c, Reader x, long l) {}
        @Override public void updateNCharacterStream(int i, Reader x) {}
        @Override public void updateNCharacterStream(String c, Reader x) {}
        @Override public void updateAsciiStream(int i, InputStream x) {}
        @Override public void updateBinaryStream(int i, InputStream x) {}
        @Override public void updateCharacterStream(int i, Reader x) {}
        @Override public void updateAsciiStream(String c, InputStream x) {}
        @Override public void updateBinaryStream(String c, InputStream x) {}
        @Override public void updateCharacterStream(String c, Reader x) {}
        @Override public void updateBlob(int i, InputStream x) {}
        @Override public void updateBlob(String c, InputStream x) {}
        @Override public void updateClob(int i, Reader x) {}
        @Override public void updateClob(String c, Reader x) {}
        @Override public void updateNClob(int i, Reader x) {}
        @Override public void updateNClob(String c, Reader x) {}
        @Override public <T> T getObject(int i, Class<T> type) throws SQLException { return null; }
        @Override public <T> T getObject(String c, Class<T> type) throws SQLException { return null; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
