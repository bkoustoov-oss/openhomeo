package org.openhomeo.core.net;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.ClinicalImage;
import org.openhomeo.core.model.Consultation;
import org.openhomeo.core.model.Patient;
import org.openhomeo.core.model.Prescription;
import org.openhomeo.core.ota.OtaUpdateService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies real Client-Server DB & Image Vault separation across independent workstations,
 * pairing-key enforcement, ACID transactions, and hardened OTA SemVer + SHA-256 staging.
 */
public class ClientServerAndOtaTest {

    @Test
    void testClientServerSharedDatabaseAndImageVault(@TempDir File serverDir, @TempDir File clientDir) throws Exception {
        // 1. Setup Server workstation database & vault
        DatabaseManager serverDb = new DatabaseManager(serverDir.getAbsolutePath());
        serverDb.initializeClinicSchema();
        ClinicDao serverDao = new ClinicDao(serverDb);
        ImageVaultService serverVault = new ImageVaultService(new File(serverDir, "vault").getAbsolutePath());

        Patient serverPatient = serverDao.createPatient(
                "CASE-SRV-01", "Aarav", "Sen",
                LocalDate.of(1988, 3, 12), "Male", "Architect",
                "+91 90000 11111", "aarav@clinic.local", "Kolkata",
                "O+", "Natrum muriaticum", "PSORA"
        );

        int rpcPort;
        try (ServerSocket ss = new ServerSocket(0)) {
            rpcPort = ss.getLocalPort();
        }

        DeploymentProfile serverProfile = new DeploymentProfile(
                DeploymentProfile.DeploymentMode.SERVER,
                "127.0.0.1", rpcPort, "OHC-PAIR-2026", "Central Shared Homeo Clinic"
        );
        serverDb.reconfigureDeployment(serverProfile, serverVault);
        assertTrue(serverDb.isServerMode());
        assertNotNull(serverDb.getNetworkServer());
        assertTrue(serverDb.getNetworkServer().isRunning());

        try {
            // 2. Verify pairing-key enforcement via RPC ping/auth
            RemoteDatabaseManager.ConnectionTestResult badAuth =
                    RemoteDatabaseManager.testClinicRpcEndpoint("127.0.0.1", rpcPort, "WRONG-KEY", 3000);
            assertFalse(badAuth.isSuccessful(), "Wrong pairing key must be rejected");

            RemoteDatabaseManager.ConnectionTestResult goodAuth =
                    RemoteDatabaseManager.testClinicRpcEndpoint("127.0.0.1", rpcPort, "OHC-PAIR-2026", 3000);
            assertTrue(goodAuth.isSuccessful(), "Valid pairing key must connect cleanly: " + goodAuth.message());

            // 3. Configure Client workstation pointing to Server
            DeploymentProfile clientProfile = new DeploymentProfile(
                    DeploymentProfile.DeploymentMode.CLIENT,
                    "127.0.0.1", rpcPort, "OHC-PAIR-2026", "Central Shared Homeo Clinic"
            );
            DeploymentProfile.save(clientDir.getAbsolutePath(), clientProfile);

            DatabaseManager clientDb = new DatabaseManager(clientDir.getAbsolutePath());
            assertTrue(clientDb.isClientMode(), "Client must detect CLIENT deployment mode");

            File isolatedClientClinicDb = new File(clientDir, "user_clinic.db");
            assertFalse(isolatedClientClinicDb.exists(), "Client mode MUST NOT create an isolated local user_clinic.db!");

            ClinicDao clientDao = new ClinicDao(clientDb);
            RemoteImageVaultService clientVault = new RemoteImageVaultService(
                    new File(clientDir, "cache_vault").getAbsolutePath(),
                    clientProfile.getBaseUrl(),
                    clientProfile.pairingKey()
            );

            // 4. Client queries Server's patient & verifies zero split-brain
            Optional<Patient> fetchedFromServer = clientDao.getPatientById(serverPatient.id());
            assertTrue(fetchedFromServer.isPresent(), "Client must see patient created on Server");
            assertEquals("CASE-SRV-01", fetchedFromServer.get().caseNumber());
            assertFalse(isolatedClientClinicDb.exists(), "user_clinic.db must remain absent on Client even after queries");

            // 5. Client saves Patient + Consultation + Prescription + Image directly into Server
            Patient clientCreated = clientDao.createPatient(
                    "CASE-CLI-02", "Meera", "Roy",
                    LocalDate.of(1995, 7, 22), "Female", "Teacher",
                    "+91 90000 22222", "meera@clinic.local", "Salt Lake",
                    "B+", "Pulsatilla", "SYCOSIS"
            );

            String consultId = UUID.randomUUID().toString();
            Prescription rx = new Prescription(
                    UUID.randomUUID().toString(), consultId, "puls", "Pulsatilla nigricans",
                    "30C", Prescription.PotencyScale.CENTESIMAL, "Twice daily", "Review in 10 days", LocalDate.now()
            );
            Consultation c = new Consultation(
                    consultId, clientCreated.id(), Instant.now(),
                    "Thirstless, desires open air", "Head & Resp", "Throbbing", "Better open air",
                    "Weepy", "Gentle", "Amelioration outdoor", "Initial visit", "Migraine",
                    List.of(), List.of(rx)
            );
            clientDao.saveConsultation(c);

            BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(180, 60, 60));
            g.fillRect(0, 0, 48, 48);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] pngBytes = baos.toByteArray();

            ImageVaultService.StoreResult storedRemote = clientVault.storeImage(pngBytes, "PNG");
            clientDao.saveClinicalImage(new ClinicalImage(
                    UUID.randomUUID().toString(), clientCreated.id(), consultId,
                    storedRemote.contentHash(), "PNG", "Forehead", "D65",
                    52.0, 24.0, 12.0, 0.0, "Uploaded from Client PC", Instant.now(), List.of()
            ));

            // 6. Server immediately sees Client's patient, consultation, prescription & image bytes
            Optional<Patient> serverSeesClientPatient = serverDao.getPatientById(clientCreated.id());
            assertTrue(serverSeesClientPatient.isPresent(), "Server DB must immediately hold patient created on Client");
            assertEquals(1, serverDao.getConsultationsForPatient(clientCreated.id()).size());
            assertEquals(1, serverDao.getImagesForPatient(clientCreated.id()).size());
            byte[] serverVaultBytes = serverVault.retrieveImage(storedRemote.contentHash());
            assertArrayEquals(pngBytes, serverVaultBytes, "Image uploaded from Client must reside verbatim in Server vault");

            // 7. Test remote ACID transaction commit & rollback
            try (Connection conn = clientDb.getClinicConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement("UPDATE patients SET occupation = ? WHERE id = ?")) {
                    ps.setString(1, "ShouldBeRolledBack");
                    ps.setString(2, clientCreated.id());
                    ps.executeUpdate();
                }
                conn.rollback();
            }
            assertEquals("Teacher", serverDao.getPatientById(clientCreated.id()).orElseThrow().occupation(),
                    "Remote rollback must revert uncommitted update");

            assertTrue(serverDb.getNetworkServer().getTotalRpcCalls() > 0);
        } finally {
            serverDb.stopClinicServer();
        }
    }

    @Test
    void testOtaSemverSha256VerificationAndStaging(@TempDir File workDir) throws Exception {
        assertTrue(OtaUpdateService.compareSemver("1.3.1", "1.3.0") > 0);
        assertTrue(OtaUpdateService.compareSemver("2.0.0", "1.9.9") > 0);
        assertEquals(0, OtaUpdateService.compareSemver("1.3.0-Diamond+", "1.3.0"));
        assertTrue(OtaUpdateService.compareSemver("1.2.0", "1.3.0") < 0);

        DatabaseManager dm = new DatabaseManager(workDir.getAbsolutePath());
        dm.initializeClinicSchema();
        ClinicDao dao = new ClinicDao(dm);

        Patient p = dao.createPatient(
                "CASE-OTA-01", "Pat", "Preserve",
                LocalDate.of(1990, 1, 1), "Female", "Nurse",
                "000", "pat@local", "Address", "A+", "Sulphur", "PSORA"
        );

        // Build mock update zip archive
        File zipFile = new File(workDir, "mock_ota.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("OpenHomeo-v99.0.0/openhomeo-gui-1.0.0-SNAPSHOT.jar"));
            zos.write("MOCK_UPDATED_JAR_BYTES".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        String realSha = OtaUpdateService.computeSha256Hex(zipFile);

        int httpPort;
        try (ServerSocket ss = new ServerSocket(0)) {
            httpPort = ss.getLocalPort();
        }

        final String[] activeSha = {realSha};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 0);
        server.createContext("/manifest.json", ex -> {
            String osKey = System.getProperty("os.name", "").toLowerCase().contains("win") ? "windows" : "macOSZip";
            String url = "http://127.0.0.1:" + httpPort + "/bundle.zip";
            String json = """
                {
                  "semver": "99.0.0",
                  "versionTitle": "99.0.0-Diamond+ (Future Verified Upgrade)",
                  "releaseDate": "2026-12-01",
                  "totalRemedies": 1500,
                  "updateAvailable": true,
                  "changelog": "Automated OTA verification payload",
                  "downloadUrls": { "%s": "%s" },
                  "sha256": { "%s": "%s" }
                }
                """.formatted(osKey, url, osKey, activeSha[0]);
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
        server.createContext("/bundle.zip", ex -> {
            byte[] b = Files.readAllBytes(zipFile.toPath());
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
        server.start();

        // Fake standalone installation root (so test exercises binary download/SHA-256/staging, not .git branch)
        File fakeInstallDir = new File(workDir, "installed_app");
        fakeInstallDir.mkdirs();
        File existingJar = new File(fakeInstallDir, "openhomeo-gui-1.0.0-SNAPSHOT.jar");
        Files.writeString(existingJar.toPath(), "OLD_UNPATCHED_JAR");

        try {
            OtaUpdateService ota = new OtaUpdateService(
                    dm.getMasterDbPath(), dm.getClinicDbPath(), dao,
                    "http://127.0.0.1:" + httpPort + "/manifest.json",
                    fakeInstallDir
            );

            OtaUpdateService.UpdateInfo info = ota.checkForUpdates();
            assertTrue(info.updateAvailable());
            assertEquals("99.0.0", info.latestSemver());

            // 1. Corrupt SHA-256 check MUST abort before modifying anything
            activeSha[0] = "0000000000000000000000000000000000000000000000000000000000000000";
            OtaUpdateService.ApplyResult tampered = ota.applyLiveUpdateAutomated(null, "tester");
            assertFalse(tampered.success(), "Tampered SHA-256 checksum must abort OTA safely");
            assertTrue(tampered.message().contains("SHA-256"), "Error should report checksum failure");
            assertEquals("OLD_UNPATCHED_JAR", Files.readString(existingJar.toPath()), "Tampered update must not touch existing jar");

            // 2. Valid SHA-256 stages safely and preserves user_clinic.db
            activeSha[0] = realSha;
            OtaUpdateService.ApplyResult ok = ota.applyLiveUpdateAutomated(null, "tester");
            assertTrue(ok.success(), "Verified SHA-256 OTA must succeed: " + ok.message());
            assertEquals(realSha, ok.hash());
            assertTrue(ok.restartRequired(), "Binary upgrade must stage detached swapper requiring clean restart");
            assertNotNull(ok.updaterScriptPath());
            assertTrue(new File(ok.updaterScriptPath()).exists(), "Staged PID-waiting updater script must exist");
            assertEquals("Preserve", dao.getPatientById(p.id()).orElseThrow().lastName(),
                    "Patient record in user_clinic.db must remain untouched");
        } finally {
            server.stop(0);
            File staged99 = new File(System.getProperty("user.home"), ".openhomeo/updates/staged/99.0.0");
            deleteRec(staged99);
            File appliedState = new File(System.getProperty("user.home"), ".openhomeo/updates/applied_version.properties");
            appliedState.delete();
        }
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] list = f.listFiles();
        if (list != null) {
            for (File c : list) deleteRec(c);
        }
        f.delete();
    }
}
