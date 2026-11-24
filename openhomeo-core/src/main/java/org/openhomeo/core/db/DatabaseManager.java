package org.openhomeo.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
        boolean needsUpdate = !masterFile.exists() || masterFile.length() == 0;
        if (!needsUpdate) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + masterDbPath);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM remedies")) {
                if (rs.next() && rs.getInt(1) < 800) {
                    needsUpdate = true;
                    log.info("Existing master repertory database is outdated (remedy count = {}). Updating to latest knowledge base...", rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("Could not verify existing master repertory database: {}. Refreshing...", e.getMessage());
                needsUpdate = true;
            }
        }

        if (needsUpdate) {
            log.info("Deploying bundled master repertory seed to: {}", masterDbPath);
            try (InputStream in = getClass().getResourceAsStream("/data/master_repertory.db")) {
                if (in != null) {
                    Files.copy(in, masterFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Bundled master_repertory.db updated successfully ({} bytes)", masterFile.length());
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

            // Enlisted Doctors / Practitioners
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS doctors (
                    id TEXT PRIMARY KEY,
                    full_name TEXT NOT NULL,
                    registration_no TEXT,
                    qualification TEXT,
                    designation TEXT,
                    clinic_name TEXT,
                    address TEXT,
                    phone TEXT,
                    email TEXT,
                    signature_notes TEXT,
                    is_active BOOLEAN DEFAULT TRUE,
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
                    doctor_id TEXT,
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

            // Migration for existing databases
            try {
                stmt.execute("ALTER TABLE consultations ADD COLUMN doctor_id TEXT;");
            } catch (SQLException ignored) {
                // Column already exists
            }

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

            // Past Medications & Suppressions History
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS consultation_past_medications (
                    id TEXT PRIMARY KEY,
                    consultation_id TEXT,
                    patient_id TEXT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                    drug_name TEXT NOT NULL,
                    category TEXT,
                    duration TEXT,
                    dosage TEXT,
                    suppressed_symptoms TEXT,
                    antidote_remedy TEXT,
                    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pastmed_pat ON consultation_past_medications(patient_id);");

            // Remedy Supersede & Override Clinical Audit
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS remedy_supersede_audit (
                    id TEXT PRIMARY KEY,
                    consultation_id TEXT,
                    patient_id TEXT NOT NULL,
                    doctor_id TEXT,
                    suggested_remedy_id TEXT NOT NULL,
                    suggested_remedy_name TEXT NOT NULL,
                    prescribed_remedy_id TEXT NOT NULL,
                    prescribed_remedy_name TEXT NOT NULL,
                    nullification_reason TEXT NOT NULL,
                    clinical_case_notes TEXT,
                    case_study_tag TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Pharmacy Medicine Inventory
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS medicine_inventory (
                    id TEXT PRIMARY KEY,
                    remedy_id TEXT,
                    medicine_name TEXT NOT NULL,
                    company_name TEXT NOT NULL,
                    form TEXT NOT NULL,
                    potency TEXT NOT NULL,
                    batch_number TEXT,
                    expiry_date DATE,
                    current_stock_qty INTEGER NOT NULL DEFAULT 0,
                    reorder_level INTEGER NOT NULL DEFAULT 5,
                    unit_price_inr REAL NOT NULL DEFAULT 100.0,
                    rack_location TEXT,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_med_name ON medicine_inventory(medicine_name);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_med_rem ON medicine_inventory(remedy_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_med_comp ON medicine_inventory(company_name);");

            // Pharmacy Orders (Doctor -> Pharmacy)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pharmacy_orders (
                    id TEXT PRIMARY KEY,
                    order_number TEXT UNIQUE NOT NULL,
                    consultation_id TEXT,
                    patient_id TEXT NOT NULL,
                    patient_name TEXT NOT NULL,
                    doctor_id TEXT,
                    doctor_name TEXT,
                    status TEXT NOT NULL,
                    substitution_notes TEXT,
                    pharmacist_agreed_by TEXT,
                    doctor_agreed_by TEXT,
                    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    completed_date TIMESTAMP
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pharmacy_order_items (
                    id TEXT PRIMARY KEY,
                    order_id TEXT NOT NULL REFERENCES pharmacy_orders(id) ON DELETE CASCADE,
                    remedy_id TEXT,
                    prescribed_remedy TEXT NOT NULL,
                    prescribed_potency TEXT,
                    prescribed_form TEXT,
                    instructions TEXT,
                    substituted_remedy TEXT,
                    substituted_potency TEXT,
                    substituted_form TEXT,
                    company TEXT,
                    quantity INTEGER DEFAULT 1,
                    unit_price_inr REAL DEFAULT 0,
                    dispensed BOOLEAN DEFAULT FALSE
                );
            """);

            // Stock Reorder Requests
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stock_reorder_requests (
                    id TEXT PRIMARY KEY,
                    request_number TEXT UNIQUE NOT NULL,
                    requested_by_user TEXT NOT NULL,
                    status TEXT NOT NULL,
                    approved_by_user TEXT,
                    approval_date TIMESTAMP,
                    total_estimated_inr REAL DEFAULT 0,
                    notes TEXT,
                    items_json TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Pharmacy Billing & Receipts
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pharmacy_bills (
                    id TEXT PRIMARY KEY,
                    bill_number TEXT UNIQUE NOT NULL,
                    order_id TEXT,
                    patient_id TEXT NOT NULL,
                    patient_name TEXT NOT NULL,
                    doctor_name TEXT,
                    bill_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    subtotal_inr REAL DEFAULT 0,
                    discount_inr REAL DEFAULT 0,
                    gst_amount_inr REAL DEFAULT 0,
                    total_inr REAL DEFAULT 0,
                    payment_mode TEXT,
                    payment_status TEXT,
                    items_json TEXT
                );
            """);

            // Pharmacists & Dispensary Staff Registry
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pharmacists (
                    id TEXT PRIMARY KEY,
                    full_name TEXT NOT NULL,
                    registration_no TEXT,
                    qualification TEXT,
                    dispensary_name TEXT,
                    address TEXT,
                    phone TEXT,
                    email TEXT,
                    is_active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Master Medicine Constituents (Phytochemical, Biochemical & Pharmacological Action)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS medicine_constituents (
                    remedy_id TEXT PRIMARY KEY,
                    remedy_name TEXT NOT NULL,
                    common_and_indian_name TEXT,
                    active_constituents TEXT NOT NULL,
                    pharmacological_action TEXT NOT NULL,
                    organ_affinity TEXT,
                    toxicology_and_antidotes TEXT,
                    annexed_remarks TEXT DEFAULT '',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mc_name ON medicine_constituents(remedy_name);");

            // Doctor/Pharmacist Remarks on Constituents (Awaiting Admin Approval)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS medicine_constituent_remarks (
                    id TEXT PRIMARY KEY,
                    remedy_id TEXT NOT NULL,
                    submitted_by_user_id TEXT NOT NULL,
                    submitted_by_user_name TEXT NOT NULL,
                    proposed_remark TEXT NOT NULL,
                    status TEXT NOT NULL,
                    reviewed_by_admin_id TEXT,
                    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    reviewed_at TIMESTAMP
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mcr_rem ON medicine_constituent_remarks(remedy_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mcr_stat ON medicine_constituent_remarks(status);");

            // Secret "Report of Faith" (Clinical Discrepancy & AI Audit Dossier for Admin's Eye Only)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS admin_faith_dossier (
                    id TEXT PRIMARY KEY,
                    consultation_id TEXT,
                    patient_id TEXT NOT NULL,
                    patient_name TEXT NOT NULL,
                    doctor_id TEXT,
                    doctor_name TEXT,
                    remedy_id TEXT NOT NULL,
                    remedy_name TEXT NOT NULL,
                    potency TEXT NOT NULL,
                    repetition_frequency TEXT,
                    violated_rule TEXT NOT NULL,
                    rule_severity TEXT NOT NULL,
                    doctor_explanation TEXT,
                    ai_risk_assessment TEXT NOT NULL,
                    predicted_complications TEXT,
                    is_audited BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faith_doc ON admin_faith_dossier(doctor_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faith_audit ON admin_faith_dossier(is_audited);");

            // Network and Remote Server Configuration
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS network_settings (
                    id TEXT PRIMARY KEY,
                    settings_json TEXT NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Community Proving Ingestion Archive
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS custom_provings (
                    id TEXT PRIMARY KEY,
                    remedy_id TEXT NOT NULL,
                    remedy_name TEXT NOT NULL,
                    prover_director TEXT,
                    proving_year INTEGER,
                    section TEXT NOT NULL,
                    symptoms_text TEXT NOT NULL,
                    source_format TEXT NOT NULL,
                    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_prov_rem ON custom_provings(remedy_id);");

            // Over-The-Air (OTA) Non-Destructive Update Log
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ota_update_history (
                    id TEXT PRIMARY KEY,
                    version TEXT NOT NULL,
                    patch_type TEXT DEFAULT 'SYSTEM_UPGRADE',
                    release_date TEXT,
                    repertory_hash TEXT,
                    status TEXT NOT NULL,
                    applied_by TEXT DEFAULT 'admin',
                    changelog_summary TEXT,
                    execution_log TEXT,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Non-destructive column additions for existing client databases
            try { stmt.execute("ALTER TABLE ota_update_history ADD COLUMN patch_type TEXT DEFAULT 'SYSTEM_UPGRADE';"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE ota_update_history ADD COLUMN applied_by TEXT DEFAULT 'admin';"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE ota_update_history ADD COLUMN changelog_summary TEXT;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE ota_update_history ADD COLUMN execution_log TEXT;"); } catch (SQLException ignored) {}

            // Registered Institution / Clinic Profile
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clinic_institution_profile (
                    id TEXT PRIMARY KEY,
                    institution_name TEXT NOT NULL,
                    tagline TEXT,
                    registration_number TEXT,
                    address TEXT,
                    phone TEXT,
                    email TEXT,
                    website TEXT,
                    chief_consultant_name TEXT,
                    chief_consultant_qualifications TEXT,
                    chief_consultant_reg_no TEXT,
                    default_currency TEXT DEFAULT 'INR (₹)',
                    default_regimen_instructions TEXT,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // Seed default institution profile if none exists
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM clinic_institution_profile")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("""
                        INSERT INTO clinic_institution_profile (
                            id, institution_name, tagline, registration_number, address, phone, email, website,
                            chief_consultant_name, chief_consultant_qualifications, chief_consultant_reg_no,
                            default_currency, default_regimen_instructions, updated_at
                        ) VALUES (
                            'inst-default',
                            'HAHNEMANNIAN CLASSICAL HOMEOPATHIC CLINIC',
                            'Advanced Classical Posology & Miasmatic Practice',
                            'REG-CLINIC-2026/01',
                            'Central Healthcare Facility Premises, Medical Enclave',
                            '+91 00000 00000',
                            'clinic@openhomeo.local',
                            'https://openhomeo.org',
                            'Dr. Consulting Physician',
                            'B.H.M.S., M.D. (Homoeopathy)',
                            'REG-HOM-2026/01',
                            'INR (₹)',
                            '• Avoid strong smelling substances (raw onion, garlic, menthol, camphor, eucalyptus, strong coffee) 30 mins before & after dose.\n• Take globules dry on a clean tongue or dissolved in fresh water as instructed.',
                            CURRENT_TIMESTAMP
                        );
                    """);
                    log.info("Initialized default clinic institution registration profile");
                }
            }

            // Seed default doctor if none exists
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM doctors")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("""
                        INSERT INTO doctors (id, full_name, registration_no, qualification, designation, clinic_name, address, phone, email, signature_notes, is_active)
                        VALUES ('doc-default', 'Dr. Consulting Physician', 'REG-HOM-2026/01', 'B.H.M.S., M.D. (Homoeopathy)', 'Senior Consulting Homeopath',
                                'Hahnemannian Classical Homeopathy Center', 'Clinical Facility Suite, Central Healthcare Campus', '+91 00000 00000', 'clinic@openhomeo.local',
                                'Consulting Physician & Classical Prescriber', TRUE);
                    """);
                    log.info("Initialized default consulting doctor profile");
                }
            }

            // Seed default pharmacist if none exists
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM pharmacists")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("""
                        INSERT INTO pharmacists (id, full_name, registration_no, qualification, dispensary_name, address, phone, email, is_active)
                        VALUES ('pharma-default', 'Dispensing Pharmacist', 'REG-PHARM-2026/01', 'D.Pharm (Homoeopathy)', 'Central Clinic Dispensary',
                                'Clinical Facility Suite, Central Healthcare Campus', '+91 00000 00001', 'dispensary@openhomeo.local', TRUE);
                    """);
                    log.info("Initialized default dispensing pharmacist profile");
                }
            }

            // Seed default RBAC users if none exist: admin, doctor, pharmacist
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("""
                        INSERT INTO users (id, username, password_hash, full_name, role)
                        VALUES ('usr-admin', 'admin', 'admin123', 'System Administrator', 'ADMIN');
                    """);
                    stmt.execute("""
                        INSERT INTO users (id, username, password_hash, full_name, role)
                        VALUES ('usr-doc', 'doctor', 'doctor123', 'Dr. Consulting Physician, M.D. (Hom.)', 'DOCTOR');
                    """);
                    stmt.execute("""
                        INSERT INTO users (id, username, password_hash, full_name, role)
                        VALUES ('usr-pharma', 'pharmacist', 'pharma123', 'Dispensing Pharmacist, D.Pharm', 'PHARMACIST');
                    """);
                    log.info("Initialized default RBAC users: admin, doctor, pharmacist");
                }
            }

            // Seed inventory
            PharmacyInventorySeeder.seedInventoryIfEmpty(conn);

            // Seed medicine constituents (phytochemicals, biochemistry, pharmacology)
            MedicineConstituentSeeder.seedConstituentsIfEmpty(conn);

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
