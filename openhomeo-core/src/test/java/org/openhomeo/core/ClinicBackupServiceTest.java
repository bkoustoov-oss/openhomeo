package org.openhomeo.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.backup.ClinicBackupService;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.model.Patient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClinicBackupServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private ClinicDao clinicDao;
    private File clinicDbFile;
    private File vaultDir;
    private File backupDir;
    private ClinicBackupService backupService;

    @BeforeEach
    void setUp() throws Exception {
        dbManager = new DatabaseManager(tempDir.toString());
        dbManager.initializeClinicSchema();
        clinicDao = new ClinicDao(dbManager);

        clinicDbFile = new File(tempDir.toFile(), "user_clinic.db");
        vaultDir = new File(tempDir.toFile(), "vault");
        vaultDir.mkdirs();

        // Create a dummy image in vault
        File sampleImg = new File(vaultDir, "test_photo.png");
        Files.writeString(sampleImg.toPath(), "DUMMY_IMAGE_DATA_12345");

        backupDir = new File(tempDir.toFile(), "backups");
        backupDir.mkdirs();

        backupService = new ClinicBackupService(clinicDbFile, vaultDir, backupDir);
    }

    @Test
    void testBackupCreationVerificationAndRestore() throws Exception {
        // 1. Create a patient
        Patient p = clinicDao.createPatient(
                "CASE-TEST-001", "Synthetic_Subject", "Test_Gamma",
                LocalDate.of(1985, 5, 20), "Male",
                "Chronic headache", "+91 00000 00000", "test@clinic.local",
                "Test Healthcare Campus", "O+", "Sycotic", "SYCOSIS"
        );
        assertNotNull(p.id());

        // 2. Create backup
        ClinicBackupService.BackupResult result = backupService.createBackup("Initial Test Backup");
        assertTrue(result.success(), "Backup creation should succeed");
        assertNotNull(result.archiveFile());
        assertTrue(result.archiveFile().exists());
        assertTrue(result.archiveFile().length() > 0);
        assertNotNull(result.sha256Checksum());
        assertNotNull(result.md5Checksum());
        assertEquals(1, result.manifest().patientCount());
        assertEquals(1, result.manifest().vaultImageCount());

        // 3. Verify backup
        ClinicBackupService.VerificationResult verResult = backupService.verifyBackup(result.archiveFile());
        assertTrue(verResult.valid(), "Verification should be valid");
        assertTrue(verResult.sqliteIntegrityOk(), "SQLite integrity check should pass");
        assertTrue(verResult.checksumMatched(), "SHA-256 checksums must match");
        assertTrue(verResult.manifestSummary().contains("Patients: 1"));

        // 4. Modify original database (delete patient)
        try (var conn = dbManager.getClinicConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM patients WHERE id = '" + p.id() + "'");
        }
        List<Patient> remaining = clinicDao.listPatients("");
        assertTrue(remaining.isEmpty(), "Patients list should be empty after deletion");

        // 5. Restore from backup
        String restoreMsg = backupService.restoreFromBackup(result.archiveFile());
        assertTrue(restoreMsg.startsWith("SUCCESS"), "Restore should succeed: " + restoreMsg);

        // 6. Verify restored database has the patient back!
        List<Patient> restoredPatients = clinicDao.listPatients("");
        assertEquals(1, restoredPatients.size(), "Restored clinic should contain 1 patient");
        assertEquals("Synthetic_Subject", restoredPatients.get(0).firstName());
        assertEquals("Test_Gamma", restoredPatients.get(0).lastName());
        assertEquals("CASE-TEST-001", restoredPatients.get(0).caseNumber());
    }

    @Test
    void testLocalFolderSync() throws Exception {
        ClinicBackupService.BackupResult result = backupService.createBackup("Sync Test");
        assertTrue(result.success());

        File googleDriveLocalFolder = new File(tempDir.toFile(), "GoogleDriveDesktop/OpenHomeo_Backups");
        boolean synced = backupService.syncToLocalFolder(result.archiveFile(), googleDriveLocalFolder);
        assertTrue(synced, "Local Google Drive directory sync should succeed");

        File syncedFile = new File(googleDriveLocalFolder, result.archiveFile().getName());
        assertTrue(syncedFile.exists());
        assertEquals(result.archiveFile().length(), syncedFile.length());
    }
}
