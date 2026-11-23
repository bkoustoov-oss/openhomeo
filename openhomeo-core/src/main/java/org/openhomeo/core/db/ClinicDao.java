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
    // PHARMACISTS & DISPENSARY STAFF
    // =========================================================================

    public Pharmacist savePharmacist(Pharmacist p) throws SQLException {
        String sql = """
            INSERT INTO pharmacists (id, full_name, registration_no, qualification, dispensary_name, address, phone, email, is_active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.id());
            ps.setString(2, p.fullName());
            ps.setString(3, p.registrationNo());
            ps.setString(4, p.qualification());
            ps.setString(5, p.dispensaryName());
            ps.setString(6, p.address());
            ps.setString(7, p.phone());
            ps.setString(8, p.email());
            ps.setBoolean(9, p.isActive());
            ps.setTimestamp(10, Timestamp.from(p.createdAt()));
            ps.executeUpdate();
        }
        return p;
    }

    public void updatePharmacist(Pharmacist p) throws SQLException {
        String sql = """
            UPDATE pharmacists
            SET full_name = ?, registration_no = ?, qualification = ?, dispensary_name = ?,
                address = ?, phone = ?, email = ?, is_active = ?
            WHERE id = ?;
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.fullName());
            ps.setString(2, p.registrationNo());
            ps.setString(3, p.qualification());
            ps.setString(4, p.dispensaryName());
            ps.setString(5, p.address());
            ps.setString(6, p.phone());
            ps.setString(7, p.email());
            ps.setBoolean(8, p.isActive());
            ps.setString(9, p.id());
            ps.executeUpdate();
        }
    }

    public Optional<Pharmacist> getPharmacistById(String id) throws SQLException {
        if (id == null) return Optional.empty();
        String sql = "SELECT * FROM pharmacists WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPharmacist(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Pharmacist> getAllPharmacists() throws SQLException {
        List<Pharmacist> list = new ArrayList<>();
        String sql = "SELECT * FROM pharmacists ORDER BY is_active DESC, full_name ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapPharmacist(rs));
            }
        }
        return list;
    }

    public List<Pharmacist> getActivePharmacists() throws SQLException {
        List<Pharmacist> list = new ArrayList<>();
        String sql = "SELECT * FROM pharmacists WHERE is_active = TRUE ORDER BY full_name ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapPharmacist(rs));
            }
        }
        return list;
    }

    private Pharmacist mapPharmacist(ResultSet rs) throws SQLException {
        return new Pharmacist(
                rs.getString("id"),
                rs.getString("full_name"),
                rs.getString("registration_no"),
                rs.getString("qualification"),
                rs.getString("dispensary_name"),
                rs.getString("address"),
                rs.getString("phone"),
                rs.getString("email"),
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

    public List<Patient> searchPatientsFiltered(String searchTerm, String category, String gender,
                                                String ageGroup, String constitution, String miasm) throws SQLException {
        List<Patient> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM patients WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (searchTerm != null && !searchTerm.isBlank()) {
            sql.append(" AND (first_name LIKE ? OR last_name LIKE ? OR case_number LIKE ? OR phone LIKE ?)");
            String like = "%" + searchTerm.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("ALL")) {
            sql.append(" AND occupation LIKE ?");
            params.add("%" + category.trim() + "%");
        }

        if (gender != null && !gender.isBlank() && !gender.equalsIgnoreCase("ALL")) {
            sql.append(" AND LOWER(gender) = LOWER(?)");
            params.add(gender.trim());
        }

        if (ageGroup != null && !ageGroup.isBlank() && !ageGroup.equalsIgnoreCase("ALL")) {
            LocalDate today = LocalDate.now();
            if (ageGroup.toUpperCase().contains("PEDIATRIC") || ageGroup.contains("< 12")) {
                sql.append(" AND dob >= ?");
                params.add(Date.valueOf(today.minusYears(12)));
            } else if (ageGroup.toUpperCase().contains("ADOLESCENT") || ageGroup.contains("12-18")) {
                sql.append(" AND dob < ? AND dob >= ?");
                params.add(Date.valueOf(today.minusYears(12)));
                params.add(Date.valueOf(today.minusYears(18)));
            } else if (ageGroup.toUpperCase().contains("ADULT") || ageGroup.contains("18-60")) {
                sql.append(" AND dob < ? AND dob >= ?");
                params.add(Date.valueOf(today.minusYears(18)));
                params.add(Date.valueOf(today.minusYears(60)));
            } else if (ageGroup.toUpperCase().contains("GERIATRIC") || ageGroup.contains("> 60")) {
                sql.append(" AND dob < ?");
                params.add(Date.valueOf(today.minusYears(60)));
            }
        }

        if (constitution != null && !constitution.isBlank() && !constitution.equalsIgnoreCase("ALL")) {
            sql.append(" AND constitutional_type LIKE ?");
            params.add("%" + constitution.trim() + "%");
        }

        if (miasm != null && !miasm.isBlank() && !miasm.equalsIgnoreCase("ALL")) {
            sql.append(" AND miasmatic_background LIKE ?");
            params.add("%" + miasm.trim() + "%");
        }

        sql.append(" ORDER BY last_name ASC, first_name ASC LIMIT 200;");

        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
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

    public User createUser(String username, String password, String fullName, User.UserRole role) throws SQLException {
        String id = "usr-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        User u = new User(id, username.trim().toLowerCase(), password, fullName.trim(), role, now);
        createUser(u);
        return u;
    }

    public void updateUserRole(String userId, User.UserRole newRole) throws SQLException {
        String sql = "UPDATE users SET role = ? WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole.name());
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    public void updateUserPassword(String userId, String newPassword) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    public void deleteUser(String userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
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
    // MEDICINE CONSTITUENTS & CLINICAL REMARKS (PHARMACOLOGY & BIOCHEMISTRY)
    // =========================================================================

    public Optional<MedicineConstituent> getMedicineConstituent(String remedyId) throws SQLException {
        if (remedyId == null || remedyId.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM medicine_constituents WHERE LOWER(remedy_id) = LOWER(?)";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, remedyId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMedicineConstituent(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<MedicineConstituent> getAllMedicineConstituents() throws SQLException {
        List<MedicineConstituent> list = new ArrayList<>();
        String sql = "SELECT * FROM medicine_constituents ORDER BY remedy_name ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapMedicineConstituent(rs));
            }
        }
        return list;
    }

    public List<MedicineConstituent> searchMedicineConstituents(String query) throws SQLException {
        List<MedicineConstituent> list = new ArrayList<>();
        String sql = """
            SELECT * FROM medicine_constituents
            WHERE remedy_id LIKE ? OR remedy_name LIKE ? OR common_and_indian_name LIKE ?
               OR active_constituents LIKE ? OR pharmacological_action LIKE ?
            ORDER BY remedy_name ASC LIMIT 1000;
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + (query != null ? query.trim() : "") + "%";
            for (int i = 1; i <= 5; i++) {
                ps.setString(i, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMedicineConstituent(rs));
                }
            }
        }
        return list;
    }

    public void saveMedicineConstituent(MedicineConstituent mc) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO medicine_constituents (
                remedy_id, remedy_name, common_and_indian_name, active_constituents,
                pharmacological_action, organ_affinity, toxicology_and_antidotes, annexed_remarks, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mc.remedyId());
            ps.setString(2, mc.remedyName());
            ps.setString(3, mc.commonAndIndianName());
            ps.setString(4, mc.activeConstituents());
            ps.setString(5, mc.pharmacologicalAction());
            ps.setString(6, mc.organAffinity());
            ps.setString(7, mc.toxicologyAndAntidotes());
            ps.setString(8, mc.annexedRemarks());
            ps.setTimestamp(9, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    public MedicineConstituentRemark submitConstituentRemark(MedicineConstituentRemark r) throws SQLException {
        String sql = """
            INSERT INTO medicine_constituent_remarks (
                id, remedy_id, submitted_by_user_id, submitted_by_user_name, proposed_remark, status, reviewed_by_admin_id, submitted_at, reviewed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.id());
            ps.setString(2, r.remedyId());
            ps.setString(3, r.submittedByUserId());
            ps.setString(4, r.submittedByUserName());
            ps.setString(5, r.proposedRemark());
            ps.setString(6, r.status().name());
            ps.setString(7, r.reviewedByAdminId());
            ps.setTimestamp(8, Timestamp.from(r.submittedAt()));
            ps.setTimestamp(9, r.reviewedAt() != null ? Timestamp.from(r.reviewedAt()) : null);
            ps.executeUpdate();
        }
        return r;
    }

    public List<MedicineConstituentRemark> getPendingConstituentRemarks() throws SQLException {
        List<MedicineConstituentRemark> list = new ArrayList<>();
        String sql = "SELECT * FROM medicine_constituent_remarks WHERE status = 'PENDING' ORDER BY submitted_at ASC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapConstituentRemark(rs));
            }
        }
        return list;
    }

    public List<MedicineConstituentRemark> getAllConstituentRemarks() throws SQLException {
        List<MedicineConstituentRemark> list = new ArrayList<>();
        String sql = "SELECT * FROM medicine_constituent_remarks ORDER BY submitted_at DESC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapConstituentRemark(rs));
            }
        }
        return list;
    }

    public boolean approveConstituentRemark(String remarkId, String adminId) throws SQLException {
        String sql = "SELECT * FROM medicine_constituent_remarks WHERE id = ?";
        MedicineConstituentRemark remark = null;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, remarkId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    remark = mapConstituentRemark(rs);
                }
            }
        }

        if (remark == null) return false;

        Instant now = Instant.now();
        // Update remark status
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE medicine_constituent_remarks SET status = 'APPROVED', reviewed_by_admin_id = ?, reviewed_at = ? WHERE id = ?")) {
            ps.setString(1, adminId);
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setString(3, remarkId);
            ps.executeUpdate();
        }

        // Annex to medicine constituents
        Optional<MedicineConstituent> optMc = getMedicineConstituent(remark.remedyId());
        if (optMc.isPresent()) {
            MedicineConstituent mc = optMc.get();
            String existingRemarks = mc.annexedRemarks() != null ? mc.annexedRemarks() : "";
            String newAnnex = String.format("\n[Approved %s by Admin; Submitted by %s]: %s",
                    LocalDate.now(), remark.submittedByUserName(), remark.proposedRemark());
            String updatedRemarks = existingRemarks.isBlank() ? newAnnex.trim() : existingRemarks + "\n" + newAnnex.trim();

            MedicineConstituent updatedMc = new MedicineConstituent(
                    mc.remedyId(), mc.remedyName(), mc.commonAndIndianName(), mc.activeConstituents(),
                    mc.pharmacologicalAction(), mc.organAffinity(), mc.toxicologyAndAntidotes(),
                    updatedRemarks, now
            );
            saveMedicineConstituent(updatedMc);
        }
        return true;
    }

    public boolean rejectConstituentRemark(String remarkId, String adminId) throws SQLException {
        Instant now = Instant.now();
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE medicine_constituent_remarks SET status = 'REJECTED', reviewed_by_admin_id = ?, reviewed_at = ? WHERE id = ?")) {
            ps.setString(1, adminId);
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setString(3, remarkId);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================================
    // SECRET "REPORT OF FAITH" (ADMIN CLINICAL AUDIT DOSSIER)
    // =========================================================================

    public AdminFaithDossierEntry recordFaithDossierEntry(AdminFaithDossierEntry entry) throws SQLException {
        String sql = """
            INSERT INTO admin_faith_dossier (
                id, consultation_id, patient_id, patient_name, doctor_id, doctor_name,
                remedy_id, remedy_name, potency, repetition_frequency, violated_rule,
                rule_severity, doctor_explanation, ai_risk_assessment, predicted_complications,
                is_audited, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.id());
            ps.setString(2, entry.consultationId());
            ps.setString(3, entry.patientId());
            ps.setString(4, entry.patientName());
            ps.setString(5, entry.doctorId());
            ps.setString(6, entry.doctorName());
            ps.setString(7, entry.remedyId());
            ps.setString(8, entry.remedyName());
            ps.setString(9, entry.potency());
            ps.setString(10, entry.repetitionFrequency());
            ps.setString(11, entry.violatedRule());
            ps.setString(12, entry.ruleSeverity().name());
            ps.setString(13, entry.doctorExplanation());
            ps.setString(14, entry.aiRiskAssessment());
            ps.setString(15, entry.predictedComplications());
            ps.setBoolean(16, entry.isAudited());
            ps.setTimestamp(17, Timestamp.from(entry.createdAt()));
            ps.executeUpdate();
        }
        return entry;
    }

    public List<AdminFaithDossierEntry> getFaithDossierEntries() throws SQLException {
        List<AdminFaithDossierEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM admin_faith_dossier ORDER BY created_at DESC";
        try (Connection conn = dbManager.getClinicConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapFaithDossierEntry(rs));
            }
        }
        return list;
    }

    public void markFaithDossierAudited(String id) throws SQLException {
        String sql = "UPDATE admin_faith_dossier SET is_audited = TRUE WHERE id = ?";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // NETWORK & REMOTE DB CONFIGURATION
    // =========================================================================

    public NetworkConfig getNetworkConfig() throws SQLException {
        String sql = "SELECT settings_json FROM network_settings WHERE id = 'active_network_config'";
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String json = rs.getString(1);
                return parseNetworkConfigJson(json);
            }
        }
        return NetworkConfig.defaultLocal();
    }

    public void saveNetworkConfig(NetworkConfig config) throws SQLException {
        String json = serializeNetworkConfigJson(config);
        String sql = """
            INSERT OR REPLACE INTO network_settings (id, settings_json, updated_at)
            VALUES ('active_network_config', ?, CURRENT_TIMESTAMP);
        """;
        try (Connection conn = dbManager.getClinicConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, json);
            ps.executeUpdate();
        }
    }

    private String serializeNetworkConfigJson(NetworkConfig c) {
        return String.format(
            "{\"mode\":\"%s\",\"remoteHost\":\"%s\",\"remotePort\":%d,\"remoteDbName\":\"%s\"," +
            "\"dbUsername\":\"%s\",\"sshHost\":\"%s\",\"sshPort\":%d,\"sshUser\":\"%s\"," +
            "\"sshKeyPath\":\"%s\",\"reverseSshPort\":%d,\"autoSsh\":%b,\"lanDiscovery\":%b,\"status\":\"%s\"}",
            c.connectionMode().name(),
            escapeJson(c.remoteHost()), c.remotePort(), escapeJson(c.remoteDbName()),
            escapeJson(c.dbUsername()), escapeJson(c.sshHost()), c.sshPort(), escapeJson(c.sshUser()),
            escapeJson(c.sshKeyPath()), c.reverseSshPort(), c.autoSshEnabled(), c.lanDiscoveryActive(),
            escapeJson(c.lastConnectionStatus())
        );
    }

    private NetworkConfig parseNetworkConfigJson(String json) {
        if (json == null || json.isBlank()) return NetworkConfig.defaultLocal();
        try {
            NetworkConfig.ConnectionMode mode = NetworkConfig.ConnectionMode.valueOf(extractJsonField(json, "mode", "LOCAL_EMBEDDED"));
            String remoteHost = extractJsonField(json, "remoteHost", "127.0.0.1");
            int remotePort = Integer.parseInt(extractJsonField(json, "remotePort", "45824"));
            String remoteDbName = extractJsonField(json, "remoteDbName", "user_clinic.db");
            String dbUsername = extractJsonField(json, "dbUsername", "admin");
            String sshHost = extractJsonField(json, "sshHost", "127.0.0.1");
            int sshPort = Integer.parseInt(extractJsonField(json, "sshPort", "22"));
            String sshUser = extractJsonField(json, "sshUser", "homeo_admin");
            String sshKeyPath = extractJsonField(json, "sshKeyPath", "~/.ssh/id_rsa");
            int reverseSshPort = Integer.parseInt(extractJsonField(json, "reverseSshPort", "45824"));
            boolean autoSsh = Boolean.parseBoolean(extractJsonField(json, "autoSsh", "false"));
            boolean lanDiscovery = Boolean.parseBoolean(extractJsonField(json, "lanDiscovery", "true"));
            String status = extractJsonField(json, "status", "Configured");
            return new NetworkConfig(mode, remoteHost, remotePort, remoteDbName, dbUsername, "", sshHost, sshPort, sshUser, sshKeyPath, reverseSshPort, autoSsh, lanDiscovery, status, Instant.now());
        } catch (Exception e) {
            return NetworkConfig.defaultLocal();
        }
    }

    private String extractJsonField(String json, String field, String fallback) {
        String pattern = "\"" + field + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return fallback;
        int start = idx + pattern.length();
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end != -1 ? json.substring(start + 1, end) : fallback;
        } else {
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);
            return end != -1 ? json.substring(start, end).trim() : fallback;
        }
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private MedicineConstituent mapMedicineConstituent(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("updated_at");
        return new MedicineConstituent(
                rs.getString("remedy_id"),
                rs.getString("remedy_name"),
                rs.getString("common_and_indian_name"),
                rs.getString("active_constituents"),
                rs.getString("pharmacological_action"),
                rs.getString("organ_affinity"),
                rs.getString("toxicology_and_antidotes"),
                rs.getString("annexed_remarks"),
                ts != null ? ts.toInstant() : Instant.now()
        );
    }

    private MedicineConstituentRemark mapConstituentRemark(ResultSet rs) throws SQLException {
        Timestamp sub = rs.getTimestamp("submitted_at");
        Timestamp rev = rs.getTimestamp("reviewed_at");
        return new MedicineConstituentRemark(
                rs.getString("id"),
                rs.getString("remedy_id"),
                rs.getString("submitted_by_user_id"),
                rs.getString("submitted_by_user_name"),
                rs.getString("proposed_remark"),
                MedicineConstituentRemark.Status.valueOf(rs.getString("status")),
                rs.getString("reviewed_by_admin_id"),
                sub != null ? sub.toInstant() : Instant.now(),
                rev != null ? rev.toInstant() : null
        );
    }

    private AdminFaithDossierEntry mapFaithDossierEntry(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new AdminFaithDossierEntry(
                rs.getString("id"),
                rs.getString("consultation_id"),
                rs.getString("patient_id"),
                rs.getString("patient_name"),
                rs.getString("doctor_id"),
                rs.getString("doctor_name"),
                rs.getString("remedy_id"),
                rs.getString("remedy_name"),
                rs.getString("potency"),
                rs.getString("repetition_frequency"),
                rs.getString("violated_rule"),
                AdminFaithDossierEntry.RuleSeverity.valueOf(rs.getString("rule_severity")),
                rs.getString("doctor_explanation"),
                rs.getString("ai_risk_assessment"),
                rs.getString("predicted_complications"),
                rs.getBoolean("is_audited"),
                created != null ? created.toInstant() : Instant.now()
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
