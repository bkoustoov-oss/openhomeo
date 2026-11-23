package org.openhomeo.core.db;

import org.openhomeo.core.model.*;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for Clinical Patients, Consultations, Prescriptions, and Encrypted Images.
 */
public class ClinicDao {
    private final DatabaseManager dbManager;

    public ClinicDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    // =========================================================================
    // DOCTORS / PRACTITIONERS
    // =========================================================================

    public Doctor saveDoctor(Doctor d) throws SQLException {
        String sql = """
            INSERT INTO doctors (id, full_name, registration_no, qualification, designation, clinic_name, address, phone, email, signature_notes, is_active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.id());
            ps.setString(2, d.fullName());
            ps.setString(3, d.registrationNo());
            ps.setString(4, d.qualification());
            ps.setString(5, d.designation());
            ps.setString(6, d.clinicName());
            ps.setString(7, d.address());
            ps.setString(8, d.phone());
            ps.setString(9, d.email());
            ps.setString(10, d.signatureNotes());
            ps.setBoolean(11, d.isActive());
            ps.setTimestamp(12, Timestamp.from(d.createdAt()));
            ps.executeUpdate();
        }
        return d;
    }

    public void updateDoctor(Doctor d) throws SQLException {
        String sql = """
            UPDATE doctors
            SET full_name = ?, registration_no = ?, qualification = ?, designation = ?, clinic_name = ?,
                address = ?, phone = ?, email = ?, signature_notes = ?, is_active = ?
            WHERE id = ?;
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.fullName());
            ps.setString(2, d.registrationNo());
            ps.setString(3, d.qualification());
            ps.setString(4, d.designation());
            ps.setString(5, d.clinicName());
            ps.setString(6, d.address());
            ps.setString(7, d.phone());
            ps.setString(8, d.email());
            ps.setString(9, d.signatureNotes());
            ps.setBoolean(10, d.isActive());
            ps.setString(11, d.id());
            ps.executeUpdate();
        }
    }

    public Optional<Doctor> getDoctorById(String id) throws SQLException {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM doctors WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapDoctor(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Doctor> getAllDoctors() throws SQLException {
        List<Doctor> list = new ArrayList<>();
        String sql = "SELECT * FROM doctors ORDER BY is_active DESC, full_name ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapDoctor(rs));
            }
        }
        return list;
    }

    public List<Doctor> getActiveDoctors() throws SQLException {
        List<Doctor> list = new ArrayList<>();
        String sql = "SELECT * FROM doctors WHERE is_active = TRUE ORDER BY full_name ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapDoctor(rs));
            }
        }
        return list;
    }

    private Doctor mapDoctor(ResultSet rs) throws SQLException {
        return new Doctor(
                rs.getString("id"),
                rs.getString("full_name"),
                rs.getString("registration_no"),
                rs.getString("qualification"),
                rs.getString("designation"),
                rs.getString("clinic_name"),
                rs.getString("address"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("signature_notes"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    // =========================================================================
    // PATIENTS
    // =========================================================================

    public Patient createPatient(String caseNumber, String firstName, String lastName,
                                 LocalDate dob, String gender, String occupation,
                                 String phone, String email, String address,
                                 String bloodGroup, String constitution, String miasm) throws SQLException {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String sql = """
            INSERT INTO patients (id, case_number, first_name, last_name, dob, gender, occupation, phone, email, address, blood_group, constitutional_type, miasmatic_background, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, caseNumber);
            ps.setString(3, firstName);
            ps.setString(4, lastName);
            ps.setDate(5, dob != null ? Date.valueOf(dob) : null);
            ps.setString(6, gender);
            ps.setString(7, occupation);
            ps.setString(8, phone);
            ps.setString(9, email);
            ps.setString(10, address);
            ps.setString(11, bloodGroup);
            ps.setString(12, constitution);
            ps.setString(13, miasm);
            ps.setTimestamp(14, Timestamp.from(now));
            ps.setTimestamp(15, Timestamp.from(now));
            ps.executeUpdate();
        }

        return new Patient(id, caseNumber, firstName, lastName, dob, gender, occupation, phone, email, address, bloodGroup, constitution, miasm, now, now);
    }

    public List<Patient> listPatients(String searchTerm) throws SQLException {
        List<Patient> list = new ArrayList<>();
        String sql = "SELECT * FROM patients";
        if (searchTerm != null && !searchTerm.isBlank()) {
            sql += " WHERE first_name LIKE ? OR last_name LIKE ? OR case_number LIKE ? OR phone LIKE ?";
        }
        sql += " ORDER BY last_name ASC, first_name ASC LIMIT 200;";

        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (searchTerm != null && !searchTerm.isBlank()) {
                String like = "%" + searchTerm.trim() + "%";
                ps.setString(1, like);
                ps.setString(2, like);
                ps.setString(3, like);
                ps.setString(4, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPatient(rs));
                }
            }
        }
        return list;
    }

    public Optional<Patient> getPatientById(String id) throws SQLException {
        String sql = "SELECT * FROM patients WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPatient(rs));
                }
            }
        }
        return Optional.empty();
    }

    // =========================================================================
    // CONSULTATIONS & CASE TAKING
    // =========================================================================

    public Consultation saveConsultation(Consultation c) throws SQLException {
        String sql = """
            INSERT INTO consultations (id, patient_id, doctor_id, consult_date, chief_complaint, location_notes, sensation_notes, modality_notes, concomitant_notes, mind_disposition, physical_generals, herings_law_analysis, doctor_notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = dbManager.getClinicConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, c.id());
                ps.setString(2, c.patientId());
                ps.setString(3, c.doctorId());
                ps.setTimestamp(4, Timestamp.from(c.consultDate()));
                ps.setString(5, c.chiefComplaint());
                ps.setString(6, c.locationNotes());
                ps.setString(7, c.sensationNotes());
                ps.setString(8, c.modalityNotes());
                ps.setString(9, c.concomitantNotes());
                ps.setString(10, c.mindDisposition());
                ps.setString(11, c.physicalGenerals());
                ps.setString(12, c.heringsLawAnalysis());
                ps.setString(13, c.doctorNotes());
                ps.executeUpdate();
            }

            // Save selected rubrics snapshot
            if (c.selectedRubrics() != null) {
                String sqlRubric = "INSERT INTO consultation_rubrics (id, consultation_id, rubric_id, full_path, clipboard_index, user_weight, is_eliminative) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psR = conn.prepareStatement(sqlRubric)) {
                    for (Consultation.SelectedRubric sr : c.selectedRubrics()) {
                        psR.setString(1, UUID.randomUUID().toString());
                        psR.setString(2, c.id());
                        psR.setString(3, sr.rubricId());
                        psR.setString(4, sr.fullPath());
                        psR.setInt(5, sr.clipboardIndex());
                        psR.setDouble(6, sr.userWeight());
                        psR.setBoolean(7, sr.isEliminative());
                        psR.addBatch();
                    }
                    psR.executeBatch();
                }
            }

            // Save prescriptions
            if (c.prescriptions() != null) {
                String sqlPresc = "INSERT INTO prescriptions (id, consultation_id, remedy_id, remedy_name, potency, scale, posology_instructions, repetition_frequency, prescribed_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psP = conn.prepareStatement(sqlPresc)) {
                    for (Prescription p : c.prescriptions()) {
                        psP.setString(1, p.id());
                        psP.setString(2, c.id());
                        psP.setString(3, p.remedyId());
                        psP.setString(4, p.remedyName());
                        psP.setString(5, p.potency());
                        psP.setString(6, p.scale().name());
                        psP.setString(7, p.posologyInstructions());
                        psP.setString(8, p.repetitionFrequency());
                        psP.setDate(9, Date.valueOf(p.prescribedDate()));
                        psP.addBatch();
                    }
                    psP.executeBatch();
                }
            }

            conn.commit();
        }
        return c;
    }

    public List<Consultation> getConsultationsForPatient(String patientId) throws SQLException {
        List<Consultation> list = new ArrayList<>();
        String sql = "SELECT * FROM consultations WHERE patient_id = ? ORDER BY consult_date DESC";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapConsultation(rs, conn));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // CLINICAL COLOR IMAGES & ANNOTATIONS
    // =========================================================================

    public ClinicalImage saveClinicalImage(ClinicalImage img) throws SQLException {
        String sql = """
            INSERT INTO clinical_images (id, patient_id, consultation_id, content_hash, file_format, body_part, color_profile, mean_lab_l, mean_lab_a, mean_lab_b, delta_e_baseline, notes, captured_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = dbManager.getClinicConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, img.id());
                ps.setString(2, img.patientId());
                ps.setString(3, img.consultationId());
                ps.setString(4, img.contentHash());
                ps.setString(5, img.fileFormat());
                ps.setString(6, img.bodyPart());
                ps.setString(7, img.colorProfile());
                if (img.meanLabL() != null) ps.setDouble(8, img.meanLabL()); else ps.setNull(8, Types.DOUBLE);
                if (img.meanLabA() != null) ps.setDouble(9, img.meanLabA()); else ps.setNull(9, Types.DOUBLE);
                if (img.meanLabB() != null) ps.setDouble(10, img.meanLabB()); else ps.setNull(10, Types.DOUBLE);
                if (img.deltaEBaseline() != null) ps.setDouble(11, img.deltaEBaseline()); else ps.setNull(11, Types.DOUBLE);
                ps.setString(12, img.notes());
                ps.setTimestamp(13, Timestamp.from(img.capturedAt()));
                ps.executeUpdate();
            }

            if (img.annotations() != null) {
                String sqlAnn = "INSERT INTO image_annotations (id, image_id, annotation_type, geometry_json, label, color_hex) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psA = conn.prepareStatement(sqlAnn)) {
                    for (ImageAnnotation ann : img.annotations()) {
                        psA.setString(1, ann.id());
                        psA.setString(2, img.id());
                        psA.setString(3, ann.type().name());
                        psA.setString(4, ann.geometryJson());
                        psA.setString(5, ann.label());
                        psA.setString(6, ann.colorHex());
                        psA.addBatch();
                    }
                    psA.executeBatch();
                }
            }
            conn.commit();
        }
        return img;
    }

    public List<ClinicalImage> getImagesForPatient(String patientId) throws SQLException {
        List<ClinicalImage> list = new ArrayList<>();
        String sql = "SELECT * FROM clinical_images WHERE patient_id = ? ORDER BY captured_at DESC";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapImage(rs, conn));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // USERS & AUTH
    // =========================================================================

    public void createUser(User u) throws SQLException {
        String sql = "INSERT INTO users (id, username, password_hash, full_name, role, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.id());
            ps.setString(2, u.username());
            ps.setString(3, u.passwordHash());
            ps.setString(4, u.fullName());
            ps.setString(5, u.role().name());
            ps.setTimestamp(6, Timestamp.from(u.createdAt()));
            ps.executeUpdate();
        }
    }

    public Optional<User> findUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new User(
                            rs.getString("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("full_name"),
                            User.UserRole.valueOf(rs.getString("role")),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY full_name ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("full_name"),
                        User.UserRole.valueOf(rs.getString("role")),
                        rs.getTimestamp("created_at").toInstant()
                ));
            }
        }
        return list;
    }

    // =========================================================================
    // PAST MEDICATIONS & SUPPRESSIONS
    // =========================================================================

    public PastMedication savePastMedication(PastMedication m) throws SQLException {
        String sql = """
            INSERT INTO consultation_past_medications (id, consultation_id, patient_id, drug_name, category, duration, dosage, suppressed_symptoms, antidote_remedy, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.id());
            ps.setString(2, m.consultationId());
            ps.setString(3, m.patientId());
            ps.setString(4, m.drugName());
            ps.setString(5, m.category());
            ps.setString(6, m.duration());
            ps.setString(7, m.dosage());
            ps.setString(8, m.suppressedSymptoms());
            ps.setString(9, m.antidoteRemedy());
            ps.setTimestamp(10, Timestamp.from(m.recordedAt()));
            ps.executeUpdate();
        }
        return m;
    }

    public List<PastMedication> getPastMedicationsByPatient(String patientId) throws SQLException {
        List<PastMedication> list = new ArrayList<>();
        String sql = "SELECT * FROM consultation_past_medications WHERE patient_id = ? ORDER BY recorded_at DESC";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PastMedication(
                            rs.getString("id"),
                            rs.getString("consultation_id"),
                            rs.getString("patient_id"),
                            rs.getString("drug_name"),
                            rs.getString("category"),
                            rs.getString("duration"),
                            rs.getString("dosage"),
                            rs.getString("suppressed_symptoms"),
                            rs.getString("antidote_remedy"),
                            rs.getTimestamp("recorded_at").toInstant()
                    ));
                }
            }
        }
        return list;
    }

    public void deletePastMedication(String id) throws SQLException {
        String sql = "DELETE FROM consultation_past_medications WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // REMEDY SUPERSEDE AUDIT (CASE STUDY & ML DATABASE)
    // =========================================================================

    public RemedySupersedeAudit saveSupersedeAudit(RemedySupersedeAudit a) throws SQLException {
        String sql = """
            INSERT INTO remedy_supersede_audit (id, consultation_id, patient_id, doctor_id, suggested_remedy_id,
                                                suggested_remedy_name, prescribed_remedy_id, prescribed_remedy_name,
                                                nullification_reason, clinical_case_notes, case_study_tag, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.id());
            ps.setString(2, a.consultationId());
            ps.setString(3, a.patientId());
            ps.setString(4, a.doctorId());
            ps.setString(5, a.suggestedRemedyId());
            ps.setString(6, a.suggestedRemedyName());
            ps.setString(7, a.prescribedRemedyId());
            ps.setString(8, a.prescribedRemedyName());
            ps.setString(9, a.nullificationReason());
            ps.setString(10, a.clinicalCaseNotes());
            ps.setString(11, a.caseStudyTag());
            ps.setTimestamp(12, Timestamp.from(a.createdAt()));
            ps.executeUpdate();
        }
        return a;
    }

    public List<RemedySupersedeAudit> getAllSupersedeAudits() throws SQLException {
        List<RemedySupersedeAudit> list = new ArrayList<>();
        String sql = "SELECT * FROM remedy_supersede_audit ORDER BY created_at DESC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new RemedySupersedeAudit(
                        rs.getString("id"),
                        rs.getString("consultation_id"),
                        rs.getString("patient_id"),
                        rs.getString("doctor_id"),
                        rs.getString("suggested_remedy_id"),
                        rs.getString("suggested_remedy_name"),
                        rs.getString("prescribed_remedy_id"),
                        rs.getString("prescribed_remedy_name"),
                        rs.getString("nullification_reason"),
                        rs.getString("clinical_case_notes"),
                        rs.getString("case_study_tag"),
                        rs.getTimestamp("created_at").toInstant()
                ));
            }
        }
        return list;
    }

    // =========================================================================
    // MEDICINE INVENTORY / STOCK REGISTER
    // =========================================================================

    public MedicineInventoryItem saveInventoryItem(MedicineInventoryItem item) throws SQLException {
        String sql = """
            INSERT INTO medicine_inventory (id, remedy_id, medicine_name, company_name, form, potency,
                                            batch_number, expiry_date, current_stock_qty, reorder_level,
                                            unit_price_inr, rack_location, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.id());
            ps.setString(2, item.remedyId());
            ps.setString(3, item.medicineName());
            ps.setString(4, item.companyName());
            ps.setString(5, item.form());
            ps.setString(6, item.potency());
            ps.setString(7, item.batchNumber());
            ps.setString(8, item.expiryDate() != null ? item.expiryDate().toString() : null);
            ps.setInt(9, item.currentStockQty());
            ps.setInt(10, item.reorderLevel());
            ps.setDouble(11, item.unitPriceInr());
            ps.setString(12, item.rackLocation());
            ps.setTimestamp(13, Timestamp.from(item.updatedAt()));
            ps.executeUpdate();
        }
        return item;
    }

    public void updateInventoryItem(MedicineInventoryItem item) throws SQLException {
        String sql = """
            UPDATE medicine_inventory
            SET medicine_name = ?, company_name = ?, form = ?, potency = ?, batch_number = ?,
                expiry_date = ?, current_stock_qty = ?, reorder_level = ?, unit_price_inr = ?,
                rack_location = ?, updated_at = ?
            WHERE id = ?;
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.medicineName());
            ps.setString(2, item.companyName());
            ps.setString(3, item.form());
            ps.setString(4, item.potency());
            ps.setString(5, item.batchNumber());
            ps.setString(6, item.expiryDate() != null ? item.expiryDate().toString() : null);
            ps.setInt(7, item.currentStockQty());
            ps.setInt(8, item.reorderLevel());
            ps.setDouble(9, item.unitPriceInr());
            ps.setString(10, item.rackLocation());
            ps.setTimestamp(11, Timestamp.from(Instant.now()));
            ps.setString(12, item.id());
            ps.executeUpdate();
        }
    }

    public void adjustStock(String id, int deltaQty) throws SQLException {
        String sql = "UPDATE medicine_inventory SET current_stock_qty = MAX(0, current_stock_qty + ?), updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deltaQty);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    public void deleteInventoryItem(String id) throws SQLException {
        String sql = "DELETE FROM medicine_inventory WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public List<MedicineInventoryItem> getAllInventoryItems() throws SQLException {
        List<MedicineInventoryItem> list = new ArrayList<>();
        String sql = "SELECT * FROM medicine_inventory ORDER BY medicine_name ASC, potency ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapInventoryItem(rs));
            }
        }
        return list;
    }

    public List<MedicineInventoryItem> searchInventory(String query, String form, String potency, String company) throws SQLException {
        List<MedicineInventoryItem> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT * FROM medicine_inventory WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            sb.append(" AND (medicine_name LIKE ? OR remedy_id LIKE ? OR rack_location LIKE ?) ");
            String q = "%" + query.trim() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (form != null && !form.isBlank() && !form.equalsIgnoreCase("ALL FORMS")) {
            sb.append(" AND form LIKE ? ");
            params.add("%" + form.trim() + "%");
        }
        if (potency != null && !potency.isBlank() && !potency.equalsIgnoreCase("ALL POTENCIES")) {
            sb.append(" AND potency = ? ");
            params.add(potency.trim());
        }
        if (company != null && !company.isBlank() && !company.equalsIgnoreCase("ALL COMPANIES")) {
            sb.append(" AND company_name LIKE ? ");
            params.add("%" + company.trim() + "%");
        }

        sb.append(" ORDER BY medicine_name ASC, potency ASC LIMIT 200");

        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapInventoryItem(rs));
                }
            }
        }
        return list;
    }

    public List<MedicineInventoryItem> getLowStockItems() throws SQLException {
        List<MedicineInventoryItem> list = new ArrayList<>();
        String sql = "SELECT * FROM medicine_inventory WHERE current_stock_qty <= reorder_level ORDER BY current_stock_qty ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapInventoryItem(rs));
            }
        }
        return list;
    }

    public void batchInsertInventory(List<MedicineInventoryItem> items) throws SQLException {
        String sql = """
            INSERT INTO medicine_inventory (id, remedy_id, medicine_name, company_name, form, potency,
                                            batch_number, expiry_date, current_stock_qty, reorder_level,
                                            unit_price_inr, rack_location, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (MedicineInventoryItem item : items) {
                ps.setString(1, item.id());
                ps.setString(2, item.remedyId());
                ps.setString(3, item.medicineName());
                ps.setString(4, item.companyName());
                ps.setString(5, item.form());
                ps.setString(6, item.potency());
                ps.setString(7, item.batchNumber());
                ps.setString(8, item.expiryDate() != null ? item.expiryDate().toString() : null);
                ps.setInt(9, item.currentStockQty());
                ps.setInt(10, item.reorderLevel());
                ps.setDouble(11, item.unitPriceInr());
                ps.setString(12, item.rackLocation());
                ps.setTimestamp(13, Timestamp.from(Instant.now()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // =========================================================================
    // PHARMACY ORDERS & INTERNAL CONSULTATIONS
    // =========================================================================

    public PharmacyOrder createPharmacyOrder(PharmacyOrder order) throws SQLException {
        String sqlOrder = """
            INSERT INTO pharmacy_orders (id, order_number, consultation_id, patient_id, patient_name,
                                         doctor_id, doctor_name, status, substitution_notes,
                                         pharmacist_agreed_by, doctor_agreed_by, order_date, completed_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        String sqlItem = """
            INSERT INTO pharmacy_order_items (id, order_id, remedy_id, prescribed_remedy, prescribed_potency,
                                              prescribed_form, instructions, substituted_remedy,
                                              substituted_potency, substituted_form, company, quantity,
                                              unit_price_inr, dispensed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = dbManager.getClinicConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psO = conn.prepareStatement(sqlOrder);
                 PreparedStatement psI = conn.prepareStatement(sqlItem)) {
                psO.setString(1, order.id());
                psO.setString(2, order.orderNumber());
                psO.setString(3, order.consultationId());
                psO.setString(4, order.patientId());
                psO.setString(5, order.patientName());
                psO.setString(6, order.doctorId());
                psO.setString(7, order.doctorName());
                psO.setString(8, order.status().name());
                psO.setString(9, order.substitutionNotes());
                psO.setString(10, order.pharmacistAgreedBy());
                psO.setString(11, order.doctorAgreedBy());
                psO.setTimestamp(12, Timestamp.from(order.orderDate()));
                psO.setTimestamp(13, order.completedDate() != null ? Timestamp.from(order.completedDate()) : null);
                psO.executeUpdate();

                if (order.items() != null) {
                    for (PharmacyOrderItem it : order.items()) {
                        psI.setString(1, it.id());
                        psI.setString(2, order.id());
                        psI.setString(3, it.remedyId());
                        psI.setString(4, it.prescribedRemedy());
                        psI.setString(5, it.prescribedPotency());
                        psI.setString(6, it.prescribedForm());
                        psI.setString(7, it.instructions());
                        psI.setString(8, it.substitutedRemedy());
                        psI.setString(9, it.substitutedPotency());
                        psI.setString(10, it.substitutedForm());
                        psI.setString(11, it.company());
                        psI.setInt(12, it.quantity());
                        psI.setDouble(13, it.unitPriceInr());
                        psI.setBoolean(14, it.dispensed());
                        psI.addBatch();
                    }
                    psI.executeBatch();
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return order;
    }

    public void updatePharmacyOrderStatus(String orderId, PharmacyOrder.OrderStatus status,
                                         String substitutionNotes, String doctorAgreedBy, String pharmacistAgreedBy) throws SQLException {
        String sql = """
            UPDATE pharmacy_orders
            SET status = ?, substitution_notes = ?, doctor_agreed_by = ?, pharmacist_agreed_by = ?,
                completed_date = CASE WHEN ? = 'DISPENSED' THEN CURRENT_TIMESTAMP ELSE completed_date END
            WHERE id = ?;
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, substitutionNotes);
            ps.setString(3, doctorAgreedBy);
            ps.setString(4, pharmacistAgreedBy);
            ps.setString(5, status.name());
            ps.setString(6, orderId);
            ps.executeUpdate();
        }
    }

    public List<PharmacyOrder> getAllPharmacyOrders() throws SQLException {
        List<PharmacyOrder> list = new ArrayList<>();
        String sql = "SELECT * FROM pharmacy_orders ORDER BY order_date DESC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapPharmacyOrder(rs, conn));
            }
        }
        return list;
    }

    public Optional<PharmacyOrder> getPharmacyOrderById(String orderId) throws SQLException {
        String sql = "SELECT * FROM pharmacy_orders WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPharmacyOrder(rs, conn));
                }
            }
        }
        return Optional.empty();
    }

    // =========================================================================
    // STOCK REORDER REQUESTS
    // =========================================================================

    public StockReorderRequest createReorderRequest(StockReorderRequest req) throws SQLException {
        String sql = """
            INSERT INTO stock_reorder_requests (id, request_number, requested_by_user, status, approved_by_user,
                                                approval_date, total_estimated_inr, notes, items_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, req.id());
            ps.setString(2, req.requestNumber());
            ps.setString(3, req.requestedByUser());
            ps.setString(4, req.status().name());
            ps.setString(5, req.approvedByUser());
            ps.setTimestamp(6, req.approvalDate() != null ? Timestamp.from(req.approvalDate()) : null);
            ps.setDouble(7, req.totalEstimatedInr());
            ps.setString(8, req.notes());
            ps.setString(9, req.itemsJson());
            ps.setTimestamp(10, Timestamp.from(req.createdAt()));
            ps.executeUpdate();
        }
        return req;
    }

    public void updateReorderStatus(String reqId, StockReorderRequest.ReorderStatus status, String approvedByUser) throws SQLException {
        String sql = """
            UPDATE stock_reorder_requests
            SET status = ?, approved_by_user = ?, approval_date = CURRENT_TIMESTAMP
            WHERE id = ?;
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, approvedByUser);
            ps.setString(3, reqId);
            ps.executeUpdate();
        }
    }

    public List<StockReorderRequest> getAllReorderRequests() throws SQLException {
        List<StockReorderRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM stock_reorder_requests ORDER BY created_at DESC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Timestamp appDate = rs.getTimestamp("approval_date");
                list.add(new StockReorderRequest(
                        rs.getString("id"),
                        rs.getString("request_number"),
                        rs.getString("requested_by_user"),
                        StockReorderRequest.ReorderStatus.valueOf(rs.getString("status")),
                        rs.getString("approved_by_user"),
                        appDate != null ? appDate.toInstant() : null,
                        rs.getDouble("total_estimated_inr"),
                        rs.getString("notes"),
                        rs.getString("items_json"),
                        rs.getTimestamp("created_at").toInstant()
                ));
            }
        }
        return list;
    }

    // =========================================================================
    // PHARMACY BILLING
    // =========================================================================

    public PharmacyBill createPharmacyBill(PharmacyBill bill) throws SQLException {
        String sql = """
            INSERT INTO pharmacy_bills (id, bill_number, order_id, patient_id, patient_name, doctor_name,
                                        bill_date, subtotal_inr, discount_inr, gst_amount_inr, total_inr,
                                        payment_mode, payment_status, items_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bill.id());
            ps.setString(2, bill.billNumber());
            ps.setString(3, bill.orderId());
            ps.setString(4, bill.patientId());
            ps.setString(5, bill.patientName());
            ps.setString(6, bill.doctorName());
            ps.setTimestamp(7, Timestamp.from(bill.billDate()));
            ps.setDouble(8, bill.subtotalInr());
            ps.setDouble(9, bill.discountInr());
            ps.setDouble(10, bill.gstAmountInr());
            ps.setDouble(11, bill.totalInr());
            ps.setString(12, bill.paymentMode());
            ps.setString(13, bill.paymentStatus());
            ps.setString(14, bill.itemsJson());
            ps.executeUpdate();
        }
        return bill;
    }

    public List<PharmacyBill> getAllPharmacyBills() throws SQLException {
        List<PharmacyBill> list = new ArrayList<>();
        String sql = "SELECT * FROM pharmacy_bills ORDER BY bill_date DESC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PharmacyBill(
                        rs.getString("id"),
                        rs.getString("bill_number"),
                        rs.getString("order_id"),
                        rs.getString("patient_id"),
                        rs.getString("patient_name"),
                        rs.getString("doctor_name"),
                        rs.getTimestamp("bill_date").toInstant(),
                        rs.getDouble("subtotal_inr"),
                        rs.getDouble("discount_inr"),
                        rs.getDouble("gst_amount_inr"),
                        rs.getDouble("total_inr"),
                        rs.getString("payment_mode"),
                        rs.getString("payment_status"),
                        rs.getString("items_json")
                ));
            }
        }
        return list;
    }

    private MedicineInventoryItem mapInventoryItem(ResultSet rs) throws SQLException {
        String exp = rs.getString("expiry_date");
        return new MedicineInventoryItem(
                rs.getString("id"),
                rs.getString("remedy_id"),
                rs.getString("medicine_name"),
                rs.getString("company_name"),
                rs.getString("form"),
                rs.getString("potency"),
                rs.getString("batch_number"),
                exp != null ? LocalDate.parse(exp) : null,
                rs.getInt("current_stock_qty"),
                rs.getInt("reorder_level"),
                rs.getDouble("unit_price_inr"),
                rs.getString("rack_location"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private PharmacyOrder mapPharmacyOrder(ResultSet rs, Connection conn) throws SQLException {
        String orderId = rs.getString("id");
        List<PharmacyOrderItem> items = new ArrayList<>();
        String sqlI = "SELECT * FROM pharmacy_order_items WHERE order_id = ?";
        try (PreparedStatement psI = conn.prepareStatement(sqlI)) {
            psI.setString(1, orderId);
            try (ResultSet rsI = psI.executeQuery()) {
                while (rsI.next()) {
                    items.add(new PharmacyOrderItem(
                            rsI.getString("id"),
                            orderId,
                            rsI.getString("remedy_id"),
                            rsI.getString("prescribed_remedy"),
                            rsI.getString("prescribed_potency"),
                            rsI.getString("prescribed_form"),
                            rsI.getString("instructions"),
                            rsI.getString("substituted_remedy"),
                            rsI.getString("substituted_potency"),
                            rsI.getString("substituted_form"),
                            rsI.getString("company"),
                            rsI.getInt("quantity"),
                            rsI.getDouble("unit_price_inr"),
                            rsI.getBoolean("dispensed")
                    ));
                }
            }
        }

        Timestamp compDate = rs.getTimestamp("completed_date");
        return new PharmacyOrder(
                orderId,
                rs.getString("order_number"),
                rs.getString("consultation_id"),
                rs.getString("patient_id"),
                rs.getString("patient_name"),
                rs.getString("doctor_id"),
                rs.getString("doctor_name"),
                PharmacyOrder.OrderStatus.valueOf(rs.getString("status")),
                rs.getString("substitution_notes"),
                rs.getString("pharmacist_agreed_by"),
                rs.getString("doctor_agreed_by"),
                rs.getTimestamp("order_date").toInstant(),
                compDate != null ? compDate.toInstant() : null,
                items
        );
    }

    // =========================================================================
    // MAPPERS
    // =========================================================================

    private Patient mapPatient(ResultSet rs) throws SQLException {
        Date dob = rs.getDate("dob");
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");

        return new Patient(
                rs.getString("id"),
                rs.getString("case_number"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                dob != null ? dob.toLocalDate() : null,
                rs.getString("gender"),
                rs.getString("occupation"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address"),
                rs.getString("blood_group"),
                rs.getString("constitutional_type"),
                rs.getString("miasmatic_background"),
                created != null ? created.toInstant() : Instant.now(),
                updated != null ? updated.toInstant() : Instant.now()
        );
    }

    private Consultation mapConsultation(ResultSet rs, Connection conn) throws SQLException {
        String id = rs.getString("id");
        List<Consultation.SelectedRubric> rubrics = new ArrayList<>();
        String sqlR = "SELECT * FROM consultation_rubrics WHERE consultation_id = ?";
        try (PreparedStatement psR = conn.prepareStatement(sqlR)) {
            psR.setString(1, id);
            try (ResultSet rsR = psR.executeQuery()) {
                while (rsR.next()) {
                    rubrics.add(new Consultation.SelectedRubric(
                            rsR.getString("rubric_id"),
                            rsR.getString("full_path"),
                            rsR.getInt("clipboard_index"),
                            rsR.getDouble("user_weight"),
                            rsR.getBoolean("is_eliminative")
                    ));
                }
            }
        }

        List<Prescription> prescriptions = new ArrayList<>();
        String sqlP = "SELECT * FROM prescriptions WHERE consultation_id = ?";
        try (PreparedStatement psP = conn.prepareStatement(sqlP)) {
            psP.setString(1, id);
            try (ResultSet rsP = psP.executeQuery()) {
                while (rsP.next()) {
                    prescriptions.add(new Prescription(
                            rsP.getString("id"),
                            id,
                            rsP.getString("remedy_id"),
                            rsP.getString("remedy_name"),
                            rsP.getString("potency"),
                            Prescription.PotencyScale.valueOf(rsP.getString("scale")),
                            rsP.getString("posology_instructions"),
                            rsP.getString("repetition_frequency"),
                            rsP.getDate("prescribed_date").toLocalDate()
                    ));
                }
            }
        }

        String doctorId = null;
        try {
            doctorId = rs.getString("doctor_id");
        } catch (SQLException ignored) {}

        return new Consultation(
                id,
                rs.getString("patient_id"),
                doctorId,
                rs.getTimestamp("consult_date").toInstant(),
                rs.getString("chief_complaint"),
                rs.getString("location_notes"),
                rs.getString("sensation_notes"),
                rs.getString("modality_notes"),
                rs.getString("concomitant_notes"),
                rs.getString("mind_disposition"),
                rs.getString("physical_generals"),
                rs.getString("herings_law_analysis"),
                rs.getString("doctor_notes"),
                rubrics,
                prescriptions
        );
    }

    private ClinicalImage mapImage(ResultSet rs, Connection conn) throws SQLException {
        String id = rs.getString("id");
        List<ImageAnnotation> annotations = new ArrayList<>();
        String sqlA = "SELECT * FROM image_annotations WHERE image_id = ?";
        try (PreparedStatement psA = conn.prepareStatement(sqlA)) {
            psA.setString(1, id);
            try (ResultSet rsA = psA.executeQuery()) {
                while (rsA.next()) {
                    annotations.add(new ImageAnnotation(
                            rsA.getString("id"),
                            id,
                            ImageAnnotation.AnnotationType.valueOf(rsA.getString("annotation_type")),
                            rsA.getString("geometry_json"),
                            rsA.getString("label"),
                            rsA.getString("color_hex")
                    ));
                }
            }
        }

        return new ClinicalImage(
                id,
                rs.getString("patient_id"),
                rs.getString("consultation_id"),
                rs.getString("content_hash"),
                rs.getString("file_format"),
                rs.getString("body_part"),
                rs.getString("color_profile"),
                rs.getObject("mean_lab_l") != null ? rs.getDouble("mean_lab_l") : null,
                rs.getObject("mean_lab_a") != null ? rs.getDouble("mean_lab_a") : null,
                rs.getObject("mean_lab_b") != null ? rs.getDouble("mean_lab_b") : null,
                rs.getObject("delta_e_baseline") != null ? rs.getDouble("delta_e_baseline") : null,
                rs.getString("notes"),
                rs.getTimestamp("captured_at").toInstant(),
                annotations
        );
    }
}
