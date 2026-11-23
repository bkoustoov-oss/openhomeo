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
