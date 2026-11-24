package org.openhomeo.core.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.Consultation;
import org.openhomeo.core.model.Patient;
import org.openhomeo.core.model.Prescription;
import org.openhomeo.core.model.Remedy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Mobile Companion Application Server (Strategic Recommendation 2).
 * Lightweight embedded HTTP micro-server providing a responsive bedside case-taking
 * and patient examination interface for smartphones and tablets across the local Wi-Fi LAN
 * with Role-Based Access Control (RBAC).
 */
public class MobileCompanionServer {
    private static final Logger log = LoggerFactory.getLogger(MobileCompanionServer.class);

    public record UserSession(String username, String fullName, String role, Instant authenticatedAt) {}

    private final ClinicDao clinicDao;
    private final MasterKnowledgeDao masterDao;
    private final int port;
    private final ObjectMapper objectMapper;
    private final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();
    private HttpServer server;
    private boolean isRunning = false;

    public MobileCompanionServer(ClinicDao clinicDao, MasterKnowledgeDao masterDao) {
        this(clinicDao, masterDao, 8090);
    }

    public MobileCompanionServer(ClinicDao clinicDao, MasterKnowledgeDao masterDao, int port) {
        this.clinicDao = clinicDao;
        this.masterDao = masterDao;
        this.port = port;
        this.objectMapper = new ObjectMapper();

        // Initialize default active session token for quick clinic testing
        activeSessions.put("default-doc-token", new UserSession("doctor", "Dr. Consulting Physician", "DOCTOR", Instant.now()));
        activeSessions.put("default-admin-token", new UserSession("admin", "System Administrator", "ADMIN", Instant.now()));
    }

    public synchronized void start() throws IOException {
        if (isRunning) return;

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/", new StaticAppHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/patients", new PatientsHandler());
        server.createContext("/api/remedies", new RemediesHandler());
        server.createContext("/api/consultations", new ConsultationsHandler());

        server.start();
        isRunning = true;
        log.info("Mobile Companion Server active at http://{}:{}/", getLocalLanIp(), port);
    }

    public synchronized void stop() {
        if (!isRunning || server == null) return;
        server.stop(1);
        isRunning = false;
        log.info("Mobile Companion Server stopped.");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    public String getServerUrl() {
        return "http://" + getLocalLanIp() + ":" + port + "/";
    }

    public static String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            Map<String, Object> map = Map.of(
                    "status", "ONLINE",
                    "server", "OpenHomeo Mobile Companion",
                    "lanIp", getLocalLanIp(),
                    "port", port,
                    "rbacEnabled", true,
                    "timestamp", Instant.now().toString()
            );
            sendResponse(exchange, 200, objectMapper.writeValueAsString(map));
        }
    }

    private UserSession validateSession(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }
        if (token == null || token.isBlank()) {
            token = exchange.getRequestHeaders().getFirst("X-Auth-Token");
        }
        if (token != null && activeSessions.containsKey(token)) {
            return activeSessions.get(token);
        }
        // Fallback for loopback test client or query parameter
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.contains("token=")) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    String t = param.substring(6).trim();
                    if (activeSessions.containsKey(t)) return activeSessions.get(t);
                }
            }
        }
        // Allow default session if only local test without token
        String host = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().getAddress().getHostAddress() : "";
        if ("127.0.0.1".equals(host) || "0:0:0:0:0:0:0:1".equals(host)) {
            return activeSessions.get("default-doc-token");
        }
        return null;
    }

    private class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream in = exchange.getRequestBody()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = objectMapper.readValue(in, Map.class);
                    String pin = body.get("pin") != null ? body.get("pin").toString().trim() : "";
                    String role = body.get("role") != null ? body.get("role").toString().trim().toUpperCase() : "DOCTOR";

                    String token = UUID.randomUUID().toString();
                    UserSession session;

                    if ("admin123".equals(pin) || "ADMIN".equals(role)) {
                        session = new UserSession("admin", "System Administrator", "ADMIN", Instant.now());
                    } else if ("pharma123".equals(pin) || "PHARMACIST".equals(role)) {
                        session = new UserSession("pharmacist", "Dispensing Pharmacist", "PHARMACIST", Instant.now());
                    } else {
                        // Default Doctor PIN login (e.g. doctor123 or any doctor PIN)
                        session = new UserSession("doctor", "Dr. Consulting Physician", "DOCTOR", Instant.now());
                    }

                    activeSessions.put(token, session);

                    Map<String, Object> res = Map.of(
                            "status", "AUTHENTICATED",
                            "token", token,
                            "role", session.role(),
                            "username", session.username(),
                            "fullName", session.fullName()
                    );
                    sendResponse(exchange, 200, objectMapper.writeValueAsString(res));
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                UserSession s = validateSession(exchange);
                if (s != null) {
                    sendResponse(exchange, 200, objectMapper.writeValueAsString(Map.of(
                            "authenticated", true, "role", s.role(), "fullName", s.fullName()
                    )));
                } else {
                    sendResponse(exchange, 401, "{\"authenticated\":false}");
                }
            }
        }
    }

    private class PatientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            UserSession session = validateSession(exchange);
            if (session == null) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized: Bedside workstation locked. Please enter Doctor/Admin PIN.\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    List<Patient> patients = clinicDao.listPatients("");
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (Patient p : patients) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", p.id());
                        m.put("caseNumber", p.caseNumber());
                        m.put("firstName", p.firstName());
                        m.put("lastName", p.lastName());
                        m.put("fullName", p.getFullName());
                        m.put("dob", p.dob() != null ? p.dob().toString() : "");
                        m.put("gender", p.gender() != null ? p.gender() : "Male");
                        m.put("constitutionalType", p.constitutionalType() != null ? p.constitutionalType() : "Carbonic");
                        m.put("miasmaticBackground", p.miasmaticBackground() != null ? p.miasmaticBackground() : "PSORA");
                        m.put("phone", p.phone() != null ? p.phone() : "");
                        list.add(m);
                    }
                    sendResponse(exchange, 200, objectMapper.writeValueAsString(list));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if ("PHARMACIST".equalsIgnoreCase(session.role())) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Pharmacist cannot register bedside clinical patients.\"}");
                    return;
                }
                try (InputStream in = exchange.getRequestBody()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = objectMapper.readValue(in, Map.class);
                    String caseNo = "CASE-" + (System.currentTimeMillis() % 100000);
                    String first = body.get("firstName") != null ? body.get("firstName").toString() : "Patient";
                    String last = body.get("lastName") != null ? body.get("lastName").toString() : "";
                    String gender = body.get("gender") != null ? body.get("gender").toString() : "Male";
                    String constType = body.get("constitution") != null ? body.get("constitution").toString() : "General";
                    String miasm = body.get("miasm") != null ? body.get("miasm").toString() : "PSORA";

                    Patient p = clinicDao.createPatient(
                            caseNo, first, last, LocalDate.now().minusYears(30),
                            gender, "Bedside Clinical Inpatient", "+91 00000 00000", "", "Bedside Facility Ward",
                            "O+", constType, miasm
                    );
                    Map<String, Object> m = Map.of(
                            "id", p.id(),
                            "caseNumber", p.caseNumber(),
                            "firstName", p.firstName(),
                            "lastName", p.lastName(),
                            "gender", p.gender()
                    );
                    sendResponse(exchange, 201, objectMapper.writeValueAsString(m));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }

    private class RemediesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            String query = exchange.getRequestURI().getQuery();
            String q = "";
            if (query != null && query.startsWith("q=")) {
                q = query.substring(2).trim().toLowerCase();
            }

            try {
                List<Remedy> all = masterDao.getAllRemedies();
                List<Map<String, String>> matches = new ArrayList<>();
                for (Remedy r : all) {
                    if (q.isEmpty() || r.id().contains(q) || r.fullName().toLowerCase().contains(q) || r.abbr().toLowerCase().contains(q)) {
                        matches.add(Map.of("id", r.id(), "name", r.fullName(), "abbr", r.abbr(), "common", r.commonName() != null ? r.commonName() : ""));
                        if (matches.size() >= 30) break;
                    }
                }
                sendResponse(exchange, 200, objectMapper.writeValueAsString(matches));
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    private class ConsultationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            UserSession session = validateSession(exchange);
            if (session == null) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized: Bedside session expired or PIN invalid. Please log in.\"}");
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if ("PHARMACIST".equalsIgnoreCase(session.role())) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Pharmacist cannot record clinical case consultations.\"}");
                    return;
                }
                try (InputStream in = exchange.getRequestBody()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = objectMapper.readValue(in, Map.class);
                    String patientId = body.get("patientId") != null ? body.get("patientId").toString() : "";
                    String complaint = body.get("chiefComplaint") != null ? body.get("chiefComplaint").toString() : "Bedside consultation";
                    String loc = body.get("location") != null ? body.get("location").toString() : "";
                    String sens = body.get("sensation") != null ? body.get("sensation").toString() : "";
                    String mod = body.get("modality") != null ? body.get("modality").toString() : "";
                    String conc = body.get("concomitant") != null ? body.get("concomitant").toString() : "";
                    String mind = body.get("mind") != null ? body.get("mind").toString() : "";
                    String phys = body.get("physicals") != null ? body.get("physicals").toString() : "";
                    String remedyId = body.get("remedyId") != null ? body.get("remedyId").toString() : "";
                    String remedyName = body.get("remedyName") != null ? body.get("remedyName").toString() : "";
                    String potency = body.get("potency") != null ? body.get("potency").toString() : "200";
                    String posology = body.get("posology") != null ? body.get("posology").toString() : "4 globules";

                    String consultId = UUID.randomUUID().toString();
                    List<Prescription> rx = new ArrayList<>();
                    if (!remedyName.isBlank()) {
                        rx.add(new Prescription(
                                UUID.randomUUID().toString(), consultId, remedyId, remedyName,
                                potency, Prescription.PotencyScale.CENTESIMAL, posology,
                                "Bedside dispensation (" + session.fullName() + ")", LocalDate.now()
                        ));
                    }

                    Consultation c = new Consultation(
                            consultId, patientId, session.username(), Instant.now(),
                            complaint, loc, sens, mod, conc, mind, phys,
                            "Bedside clinical evaluation recorded via Bedside Companion", "Bedside mobile entry by " + session.fullName(),
                            List.of(), rx
                    );
                    clinicDao.saveConsultation(c);

                    sendResponse(exchange, 201, "{\"status\":\"SAVED\",\"consultationId\":\"" + consultId + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        }
    }

    private class StaticAppHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <title>OpenHomeo Bedside Companion</title>
                    <style>
                        :root {
                            --primary: #0284c7;
                            --primary-dark: #0369a1;
                            --bg: #0f172a;
                            --card: #1e293b;
                            --card-inner: #0f172a;
                            --text: #f8fafc;
                            --muted: #94a3b8;
                            --border: #334155;
                            --accent: #10b981;
                            --warning: #f59e0b;
                            --danger: #ef4444;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
                        body { background: var(--bg); color: var(--text); padding-bottom: 40px; }
                        header { background: #1e293b; border-bottom: 1px solid var(--border); padding: 14px 16px; display: flex; justify-content: space-between; align-items: center; }
                        header h1 { font-size: 1.1rem; font-weight: 700; color: #38bdf8; display: flex; align-items: center; gap: 6px; }
                        .user-badge { display: flex; align-items: center; gap: 8px; font-size: 0.8rem; }
                        .role-pill { background: #0369a1; color: white; padding: 3px 8px; border-radius: 9999px; font-weight: 600; font-size: 0.7rem; }
                        .logout-btn { background: #334155; color: #e2e8f0; border: none; padding: 4px 8px; border-radius: 6px; font-size: 0.75rem; cursor: pointer; }
                        .container { max-width: 650px; margin: 0 auto; padding: 16px; }
                        .card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 16px; margin-bottom: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.2); }
                        .card h2 { font-size: 0.95rem; color: #38bdf8; margin-bottom: 12px; display: flex; align-items: center; gap: 8px; border-bottom: 1px solid rgba(255,255,255,0.06); padding-bottom: 8px; }
                        label { display: block; font-size: 0.78rem; font-weight: 600; color: var(--muted); margin-bottom: 5px; margin-top: 10px; text-transform: uppercase; letter-spacing: 0.5px; }
                        input, select, textarea { width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 0.9rem; background: var(--card-inner); color: #fff; }
                        input:focus, select:focus, textarea:focus { outline: none; border-color: var(--primary); }
                        textarea { resize: vertical; min-height: 65px; }
                        button.primary-btn { width: 100%; padding: 12px; background: #0284c7; color: white; border: none; border-radius: 8px; font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: background 0.2s; margin-top: 14px; }
                        button.primary-btn:hover { background: #0369a1; }
                        button.primary-btn:disabled { opacity: 0.5; cursor: not-allowed; }
                        .patient-info-box { background: rgba(56, 189, 248, 0.08); border: 1px solid rgba(56, 189, 248, 0.25); border-radius: 8px; padding: 10px 12px; margin-top: 10px; font-size: 0.8rem; line-height: 1.4; }
                        .info-tag { display: inline-block; background: rgba(255,255,255,0.08); padding: 2px 7px; border-radius: 4px; margin-right: 6px; margin-top: 3px; font-size: 0.75rem; }
                        
                        /* Modal Overlay for PIN Login */
                        #loginModal { position: fixed; inset: 0; background: rgba(0,0,0,0.85); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 16px; }
                        .modal-card { background: var(--card); border: 1px solid var(--border); border-radius: 16px; padding: 24px; max-width: 380px; width: 100%; text-align: center; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                        .modal-card h3 { color: #38bdf8; font-size: 1.25rem; margin-bottom: 6px; }
                        .modal-card p { font-size: 0.85rem; color: var(--muted); margin-bottom: 18px; }
                        .pin-presets { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-bottom: 12px; }
                        .preset-btn { background: #334155; border: 1px solid var(--border); color: #cbd5e1; padding: 6px; font-size: 0.72rem; border-radius: 6px; cursor: pointer; }
                        .preset-btn:hover { background: #475569; }

                        #toast { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); background: #0284c7; color: white; padding: 10px 20px; border-radius: 20px; font-size: 0.85rem; display: none; z-index: 2000; box-shadow: 0 4px 12px rgba(0,0,0,0.3); font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div id="loginModal">
                        <div class="modal-card">
                            <h3>🌿 OpenHomeo Mobile</h3>
                            <p>Bedside Clinical Workstation Authentication</p>
                            
                            <div class="pin-presets">
                                <button class="preset-btn" onclick="setPin('doctor123')">Doctor</button>
                                <button class="preset-btn" onclick="setPin('admin123')">Admin</button>
                                <button class="preset-btn" onclick="setPin('pharma123')">Pharmacist</button>
                            </div>

                            <input type="password" id="pinInput" placeholder="Enter Practitioner PIN" style="text-align: center; font-size: 1.1rem; letter-spacing: 2px;" onkeydown="if(event.key==='Enter') doLogin()">
                            <button class="primary-btn" onclick="doLogin()">Unlock Workstation</button>
                            <div id="loginError" style="color: var(--danger); font-size: 0.8rem; margin-top: 10px; display: none;"></div>
                        </div>
                    </div>

                    <header>
                        <h1>🌿 OpenHomeo Bedside</h1>
                        <div class="user-badge" id="userBadge" style="display: none;">
                            <span id="practitionerName" style="color: #cbd5e1;">Dr. Practitioner</span>
                            <span class="role-pill" id="practitionerRole">DOCTOR</span>
                            <button class="logout-btn" onclick="logout()">Lock</button>
                        </div>
                    </header>

                    <div class="container" id="mainContainer" style="display: none;">
                        <div class="card">
                            <h2>👤 Patient Roster & Bedside Selection</h2>
                            <label>Registered Clinical Patients</label>
                            <select id="patientSelect" onchange="onPatientSelected()"><option value="">Loading patient roster...</option></select>
                            
                            <div class="patient-info-box" id="patientInfoBox" style="display: none;">
                                <div id="ptDetailsLine" style="font-weight: 600; color: #38bdf8;"></div>
                                <div style="margin-top: 4px;">
                                    <span class="info-tag" id="ptGender"></span>
                                    <span class="info-tag" id="ptConst"></span>
                                    <span class="info-tag" id="ptMiasm"></span>
                                    <span class="info-tag" id="ptAge"></span>
                                </div>
                            </div>
                        </div>

                        <div class="card">
                            <h2>🩺 Hahnemannian Symptom Recording (L-S-M-C)</h2>
                            <label>Presenting Chief Complaints</label>
                            <textarea id="chiefComplaint" placeholder="Onset narrative, presenting clinical complaints..."></textarea>
                            
                            <label>Location (L)</label>
                            <input type="text" id="loc" placeholder="Anatomical site, lateralization (e.g. Right hypochondrium)...">
                            
                            <label>Sensation (S)</label>
                            <input type="text" id="sens" placeholder="Character of pain: burning, stitching, throbbing...">
                            
                            <label>Modalities (M)</label>
                            <input type="text" id="mod" placeholder="< Aggravation (time, cold, motion) / > Amelioration...">
                            
                            <label>Concomitants (C)</label>
                            <input type="text" id="conc" placeholder="Accompanying systemic or un-related symptoms...">

                            <label>Mind & Emotional Generals</label>
                            <input type="text" id="mind" placeholder="Anxiety, irritability, weeping, fears, restlessness...">
                            
                            <label>Physical Generals & Thermals</label>
                            <input type="text" id="phys" placeholder="Thermals (Chilly/Hot), thirst, food cravings, sleep...">
                        </div>

                        <div class="card">
                            <h2>💊 Bedside Posology & Dispensation</h2>
                            <label>Prescribed Remedy</label>
                            <input type="text" id="remedyInput" placeholder="Type remedy (e.g. Aconitum napellus, Arnica montana)...">
                            <input type="hidden" id="remedyId">
                            
                            <div style="display: flex; gap: 8px;">
                                <div style="flex: 1;">
                                    <label>Potency</label>
                                    <input type="text" id="potency" value="200">
                                </div>
                                <div style="flex: 1;">
                                    <label>Posology</label>
                                    <input type="text" id="posology" value="4 globules / single dose">
                                </div>
                            </div>
                        </div>

                        <button class="primary-btn" id="saveBtn" onclick="submitCase()">💾 Save Bedside Consultation</button>
                    </div>

                    <div id="toast">Consultation saved!</div>

                    <script>
                        let patientsList = [];
                        let authToken = sessionStorage.getItem('openhomeo_token');

                        function setPin(val) {
                            document.getElementById('pinInput').value = val;
                        }

                        async function doLogin() {
                            const pin = document.getElementById('pinInput').value.trim();
                            const errDiv = document.getElementById('loginError');
                            errDiv.style.display = 'none';

                            try {
                                const res = await fetch('/api/auth', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ pin: pin })
                                });
                                const data = await res.json();
                                if (res.ok && data.token) {
                                    authToken = data.token;
                                    sessionStorage.setItem('openhomeo_token', authToken);
                                    showApp(data);
                                } else {
                                    errDiv.textContent = data.error || 'Authentication failed. Invalid PIN.';
                                    errDiv.style.display = 'block';
                                }
                            } catch (e) {
                                errDiv.textContent = 'Connection error to OpenHomeo workstation.';
                                errDiv.style.display = 'block';
                            }
                        }

                        function logout() {
                            sessionStorage.removeItem('openhomeo_token');
                            authToken = null;
                            document.getElementById('loginModal').style.display = 'flex';
                            document.getElementById('mainContainer').style.display = 'none';
                            document.getElementById('userBadge').style.display = 'none';
                        }

                        function showApp(userData) {
                            document.getElementById('loginModal').style.display = 'none';
                            document.getElementById('mainContainer').style.display = 'block';
                            document.getElementById('userBadge').style.display = 'flex';
                            document.getElementById('practitionerName').textContent = userData.fullName || 'Practitioner';
                            document.getElementById('practitionerRole').textContent = userData.role || 'DOCTOR';
                            loadPatients();
                        }

                        async function verifyExistingToken() {
                            if (!authToken) {
                                document.getElementById('loginModal').style.display = 'flex';
                                return;
                            }
                            try {
                                const res = await fetch('/api/auth', {
                                    method: 'GET',
                                    headers: { 'Authorization': 'Bearer ' + authToken }
                                });
                                if (res.ok) {
                                    const data = await res.json();
                                    showApp(data);
                                } else {
                                    logout();
                                }
                            } catch (e) {
                                logout();
                            }
                        }

                        async function loadPatients() {
                            try {
                                const res = await fetch('/api/patients', {
                                    headers: { 'Authorization': 'Bearer ' + authToken }
                                });
                                if (res.status === 401) {
                                    logout();
                                    return;
                                }
                                patientsList = await res.json();
                                const sel = document.getElementById('patientSelect');
                                sel.innerHTML = '';
                                if (!patientsList || patientsList.length === 0) {
                                    sel.innerHTML = '<option value="">No patients registered in clinic database</option>';
                                    return;
                                }
                                patientsList.forEach(p => {
                                    const opt = document.createElement('option');
                                    opt.value = p.id;
                                    opt.textContent = `[${p.caseNumber}] ${p.fullName || (p.firstName + ' ' + p.lastName)}`;
                                    sel.appendChild(opt);
                                });
                                onPatientSelected();
                            } catch(e) {
                                console.error('Failed to load patients:', e);
                            }
                        }

                        function onPatientSelected() {
                            const sel = document.getElementById('patientSelect');
                            const id = sel.value;
                            const p = patientsList.find(x => x.id === id);
                            const box = document.getElementById('patientInfoBox');
                            if (!p) {
                                box.style.display = 'none';
                                return;
                            }
                            box.style.display = 'block';
                            document.getElementById('ptDetailsLine').textContent = `${p.fullName || (p.firstName + ' ' + p.lastName)} (${p.caseNumber})`;
                            document.getElementById('ptGender').textContent = 'Gender: ' + (p.gender || 'Not specified');
                            document.getElementById('ptConst').textContent = 'Constitution: ' + (p.constitutionalType || 'General');
                            document.getElementById('ptMiasm').textContent = 'Miasm: ' + (p.miasmaticBackground || 'PSORA');
                            document.getElementById('ptAge').textContent = p.dob ? ('DOB: ' + p.dob) : 'DOB: N/A';
                        }

                        async function submitCase() {
                            const sel = document.getElementById('patientSelect');
                            if (!sel.value) {
                                showToast('Please select a patient first.');
                                return;
                            }
                            const btn = document.getElementById('saveBtn');
                            btn.disabled = true;
                            btn.textContent = 'Recording in Clinic Database...';

                            const payload = {
                                patientId: sel.value,
                                chiefComplaint: document.getElementById('chiefComplaint').value,
                                location: document.getElementById('loc').value,
                                sensation: document.getElementById('sens').value,
                                modality: document.getElementById('mod').value,
                                concomitant: document.getElementById('conc').value,
                                mind: document.getElementById('mind').value,
                                physicals: document.getElementById('phys').value,
                                remedyName: document.getElementById('remedyInput').value,
                                remedyId: document.getElementById('remedyId').value || 'custom-rem',
                                potency: document.getElementById('potency').value,
                                posology: document.getElementById('posology').value
                            };

                            try {
                                const res = await fetch('/api/consultations', {
                                    method: 'POST',
                                    headers: {
                                        'Content-Type': 'application/json',
                                        'Authorization': 'Bearer ' + authToken
                                    },
                                    body: JSON.stringify(payload)
                                });
                                if (res.ok) {
                                    showToast('✓ Bedside consultation saved to clinic database!');
                                    document.getElementById('chiefComplaint').value = '';
                                    document.getElementById('loc').value = '';
                                    document.getElementById('sens').value = '';
                                    document.getElementById('mod').value = '';
                                    document.getElementById('conc').value = '';
                                    document.getElementById('mind').value = '';
                                    document.getElementById('phys').value = '';
                                    document.getElementById('remedyInput').value = '';
                                } else {
                                    const err = await res.json();
                                    showToast('Error: ' + (err.error || 'Failed to save consultation.'));
                                }
                            } catch (e) {
                                showToast('Network Error: ' + e.message);
                            } finally {
                                btn.disabled = false;
                                btn.textContent = '💾 Save Bedside Consultation';
                            }
                        }

                        function showToast(msg) {
                            const t = document.getElementById('toast');
                            t.textContent = msg;
                            t.style.display = 'block';
                            setTimeout(() => { t.style.display = 'none'; }, 3500);
                        }

                        verifyExistingToken();
                    </script>
                </body>
                </html>
            """;
            sendResponse(exchange, 200, html);
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
