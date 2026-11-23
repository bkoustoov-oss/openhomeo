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
import java.util.concurrent.Executors;

/**
 * Mobile Companion Application Server (Strategic Recommendation 2).
 * Lightweight embedded HTTP micro-server providing a responsive bedside case-taking
 * and patient examination interface for smartphones and tablets across the local Wi-Fi LAN.
 */
public class MobileCompanionServer {
    private static final Logger log = LoggerFactory.getLogger(MobileCompanionServer.class);

    private final ClinicDao clinicDao;
    private final MasterKnowledgeDao masterDao;
    private final int port;
    private final ObjectMapper objectMapper;
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
    }

    public synchronized void start() throws IOException {
        if (isRunning) return;

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/", new StaticAppHandler());
        server.createContext("/api/status", new StatusHandler());
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
                    "timestamp", Instant.now().toString()
            );
            sendResponse(exchange, 200, objectMapper.writeValueAsString(map));
        }
    }

    private class PatientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    List<Patient> patients = clinicDao.listPatients("");
                    sendResponse(exchange, 200, objectMapper.writeValueAsString(patients));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
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
                            gender, "Bedside Patient", "+91 9000000000", "", "Bedside Ward",
                            "O+", constType, miasm
                    );
                    sendResponse(exchange, 201, objectMapper.writeValueAsString(p));
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
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
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
                                "Bedside dispensation", LocalDate.now()
                        ));
                    }

                    Consultation c = new Consultation(
                            consultId, patientId, "doc-bedside", Instant.now(),
                            complaint, loc, sens, mod, conc, mind, phys,
                            "Bedside evaluation", "Bedside mobile entry",
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
                    <title>OpenHomeo Mobile Companion</title>
                    <style>
                        :root {
                            --primary: #0284c7;
                            --primary-dark: #0369a1;
                            --bg: #f8fafc;
                            --card: #ffffff;
                            --text: #0f172a;
                            --muted: #64748b;
                            --border: #e2e8f0;
                            --accent: #10b981;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
                        body { background: var(--bg); color: var(--text); padding-bottom: 30px; }
                        header { background: var(--primary); color: white; padding: 16px; text-align: center; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        header h1 { font-size: 1.25rem; font-weight: 700; }
                        header p { font-size: 0.8rem; opacity: 0.9; margin-top: 4px; }
                        .container { max-width: 600px; margin: 0 auto; padding: 16px; }
                        .card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
                        .card h2 { font-size: 1rem; color: var(--primary-dark); margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
                        label { display: block; font-size: 0.8rem; font-weight: 600; color: var(--muted); margin-bottom: 4px; margin-top: 10px; }
                        input, select, textarea { width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 0.95rem; background: #fff; }
                        input:focus, select:focus, textarea:focus { outline: none; border-color: var(--primary); }
                        textarea { resize: vertical; min-height: 60px; }
                        button { width: 100%; padding: 12px; background: var(--primary); color: white; border: none; border-radius: 8px; font-size: 1rem; font-weight: 600; cursor: pointer; transition: background 0.2s; margin-top: 12px; }
                        button:active { background: var(--primary-dark); }
                        .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 0.75rem; background: #e0f2fe; color: #0369a1; font-weight: 600; }
                        #toast { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); background: #1e293b; color: white; padding: 10px 20px; border-radius: 20px; font-size: 0.85rem; display: none; z-index: 100; box-shadow: 0 4px 6px rgba(0,0,0,0.15); }
                    </style>
                </head>
                <body>
                    <header>
                        <h1>OpenHomeo Workstation</h1>
                        <p>📱 Bedside Clinical Case-Taking Companion</p>
                    </header>
                    <div class="container">
                        <div class="card">
                            <h2>👤 Patient Demographics</h2>
                            <label>Select Patient</label>
                            <select id="patientSelect"><option value="">Loading patients...</option></select>
                        </div>

                        <div class="card">
                            <h2>🩺 Chief Complaints & Symptoms</h2>
                            <label>Presenting Chief Complaints</label>
                            <textarea id="chiefComplaint" placeholder="Onset narrative, presenting clinical complaints..."></textarea>
                            
                            <label>Location (L)</label>
                            <input type="text" id="loc" placeholder="Anatomical site, lateralization...">
                            
                            <label>Sensation (S)</label>
                            <input type="text" id="sens" placeholder="Burning, stitching, throbbing...">
                            
                            <label>Modalities (M)</label>
                            <input type="text" id="mod" placeholder="< worse / > better conditions...">
                            
                            <label>Mind & Emotional Generals</label>
                            <input type="text" id="mind" placeholder="Anxiety, irritability, weeping, fears...">
                            
                            <label>Physical Generals & Thermals</label>
                            <input type="text" id="phys" placeholder="Chilly/Hot, thirst, cravings, sleep...">
                        </div>

                        <div class="card">
                            <h2>💊 Bedside Posology / Prescription</h2>
                            <label>Prescribed Remedy</label>
                            <input type="text" id="remedyInput" placeholder="Type remedy name (e.g. Arnica, Aconite)...">
                            <input type="hidden" id="remedyId">
                            
                            <div style="display: flex; gap: 8px;">
                                <div style="flex: 1;">
                                    <label>Potency</label>
                                    <input type="text" id="potency" value="200">
                                </div>
                                <div style="flex: 1;">
                                    <label>Posology</label>
                                    <input type="text" id="posology" value="4 globules / dose">
                                </div>
                            </div>
                        </div>

                        <button id="saveBtn" onclick="submitCase()">💾 Save Bedside Consultation</button>
                    </div>

                    <div id="toast">Consultation saved!</div>

                    <script>
                        async function loadPatients() {
                            try {
                                const res = await fetch('/api/patients');
                                const data = await res.json();
                                const sel = document.getElementById('patientSelect');
                                sel.innerHTML = '';
                                data.forEach(p => {
                                    const opt = document.createElement('option');
                                    opt.value = p.id;
                                    opt.textContent = `[${p.caseNumber}] ${p.firstName} ${p.lastName} (${p.constitutionalType || 'General'})`;
                                    sel.appendChild(opt);
                                });
                            } catch(e) { console.error(e); }
                        }

                        async function submitCase() {
                            const btn = document.getElementById('saveBtn');
                            btn.disabled = true;
                            btn.textContent = 'Saving...';

                            const payload = {
                                patientId: document.getElementById('patientSelect').value,
                                chiefComplaint: document.getElementById('chiefComplaint').value,
                                location: document.getElementById('loc').value,
                                sensation: document.getElementById('sens').value,
                                modality: document.getElementById('mod').value,
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
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify(payload)
                                });
                                if (res.ok) {
                                    showToast('Bedside consultation saved to clinic database!');
                                    document.getElementById('chiefComplaint').value = '';
                                    document.getElementById('loc').value = '';
                                    document.getElementById('sens').value = '';
                                    document.getElementById('mod').value = '';
                                } else {
                                    showToast('Error saving consultation.');
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
                            setTimeout(() => { t.style.display = 'none'; }, 3000);
                        }

                        loadPatients();
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
