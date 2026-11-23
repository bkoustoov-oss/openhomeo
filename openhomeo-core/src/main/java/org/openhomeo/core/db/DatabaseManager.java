package org.openhomeo.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages connections and lifecycle for both:
 * 1. Master Knowledge Base Database (master_repertory.db) - Read-only seed
 * 2. User Clinic EMR Database (user_clinic.db) - Transactional encrypted data
 */
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final String masterDbPath;
    private final String clinicDbPath;
    private String activeEncryptionKey;

    public DatabaseManager(String baseDataDir) {
        File dataDir = new File(baseDataDir);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.masterDbPath = new File(dataDir, "master_repertory.db").getAbsolutePath();
        this.clinicDbPath = new File(dataDir, "user_clinic.db").getAbsolutePath();

        ensureMasterDatabaseSeeded();
    }

    public synchronized void setEncryptionKey(String key) {
        this.activeEncryptionKey = key;
    }

    private void ensureMasterDatabaseSeeded() {
        File masterFile = new File(masterDbPath);
        if (!masterFile.exists() || masterFile.length() == 0) {
            log.info("Master repertory database not found at {}. Copying bundled seed...", masterDbPath);
            try (InputStream in = getClass().getResourceAsStream("/data/master_repertory.db")) {
                if (in != null) {
                    Files.copy(in, masterFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Bundled master_repertory.db copied successfully ({} bytes)", masterFile.length());
                } else {
                    log.warn("Bundled /data/master_repertory.db not found in classpath resources!");
                }
            } catch (Exception e) {
                log.error("Failed to copy bundled master_repertory.db", e);
            }
        }
    }

    public Connection getMasterConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + masterDbPath);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA query_only = ON;");
            stmt.execute("PRAGMA cache_size = -64000;"); // 64MB cache
        }
        return conn;
    }

    public Connection getClinicConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + clinicDbPath);
        try (Statement stmt = conn.createStatement()) {
            if (activeEncryptionKey != null && !activeEncryptionKey.isBlank()) {
                // If SQLCipher native extension is loaded, PRAGMA key activates page encryption
                stmt.execute("PRAGMA key = '" + activeEncryptionKey.replace("'", "''") + "';");
            }
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        }
        return conn;
    }

    public void initializeClinicSchema() throws SQLException {
        try (Connection conn = getClinicConnection();
             Statement stmt = conn.createStatement()) {

            // Users & Auth
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    full_name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Patients EMR
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS patients (
                    id TEXT PRIMARY KEY,
                    case_number TEXT UNIQUE NOT NULL,
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    dob DATE,
                    gender TEXT,
                    occupation TEXT,
                    phone TEXT,
                    email TEXT,
                    address TEXT,
                    blood_group TEXT,
                    constitutional_type TEXT,
                    miasmatic_background TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pat_name ON patients(last_name, first_name);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pat_case ON patients(case_number);");

            // Consultations & Case Taking
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS consultations (
                    id TEXT PRIMARY KEY,
                    patient_id TEXT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                    consult_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    chief_complaint TEXT NOT NULL,
                    location_notes TEXT,
                    sensation_notes TEXT,
                    modality_notes TEXT,
                    concomitant_notes TEXT,
                    mind_disposition TEXT,
                    physical_generals TEXT,
                    herings_law_analysis TEXT,
                    doctor_notes TEXT
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cons_pat ON consultations(patient_id);");

            // Selected Rubrics associated with consultation
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS consultation_rubrics (
                    id TEXT PRIMARY KEY,
                    consultation_id TEXT NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
                    rubric_id TEXT NOT NULL,
                    full_path TEXT NOT NULL,
                    clipboard_index INTEGER DEFAULT 1,
                    user_weight REAL DEFAULT 1.0,
                    is_eliminative BOOLEAN DEFAULT FALSE
                );
            """);

            // Prescriptions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS prescriptions (
                    id TEXT PRIMARY KEY,
                    consultation_id TEXT NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
                    remedy_id TEXT NOT NULL,
                    remedy_name TEXT NOT NULL,
                    potency TEXT NOT NULL,
                    scale TEXT NOT NULL,
                    posology_instructions TEXT NOT NULL,
                    repetition_frequency TEXT,
                    prescribed_date DATE NOT NULL
                );
            """);

            // Clinical Images
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clinical_images (
                    id TEXT PRIMARY KEY,
                    patient_id TEXT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                    consultation_id TEXT REFERENCES consultations(id) ON DELETE SET NULL,
                    content_hash TEXT NOT NULL,
                    file_format TEXT NOT NULL,
                    body_part TEXT NOT NULL,
                    color_profile TEXT,
                    mean_lab_l REAL,
                    mean_lab_a REAL,
                    mean_lab_b REAL,
                    delta_e_baseline REAL,
                    notes TEXT,
                    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_img_pat ON clinical_images(patient_id);");

            // Image Annotations
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS image_annotations (
                    id TEXT PRIMARY KEY,
                    image_id TEXT NOT NULL REFERENCES clinical_images(id) ON DELETE CASCADE,
                    annotation_type TEXT NOT NULL,
                    geometry_json TEXT NOT NULL,
                    label TEXT,
                    color_hex TEXT DEFAULT '#FF0000'
                );
            """);

            log.info("Clinic database schema initialized successfully.");
        }
    }

    public String getMasterDbPath() {
        return masterDbPath;
    }

    public String getClinicDbPath() {
        return clinicDbPath;
    }
}
