package org.openhomeo.core.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.model.*;
import org.openhomeo.core.security.SecurityService;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerAndDaoTest {

    @Test
    public void testFullDataLayerAndSecurity(@TempDir Path tempDir) throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.toString());
        dbManager.initializeClinicSchema();

        MasterKnowledgeDao masterDao = new MasterKnowledgeDao(dbManager);
        ClinicDao clinicDao = new ClinicDao(dbManager);
        SecurityService secService = new SecurityService();

        // 1. Verify Master Knowledge Base (Remedies & FTS)
        List<Remedy> remedies = masterDao.getAllRemedies();
        assertTrue(remedies.size() >= 30, "Should load master remedies from bundled seed");

        Optional<Remedy> lycOpt = masterDao.getRemedyById("lyc");
        assertTrue(lycOpt.isPresent(), "Lycopodium should be present");
        assertEquals("Lycopodium clavatum", lycOpt.get().fullName());

        List<MateriaMedicaEntry> mmList = masterDao.getMateriaMedica("lyc");
        assertFalse(mmList.isEmpty(), "Materia medica for Lycopodium should not be empty");

        List<Rubric> ftsRubrics = masterDao.searchRubricsFts("cold");
        assertFalse(ftsRubrics.isEmpty(), "FTS5 search for 'cold' should return rubrics");

        // 2. User Creation & Authentication
        String passHash = secService.hashPassword("MasterDoctor2026!");
        User admin = new User(UUID.randomUUID().toString(), "dr_koustoov", passHash, "Dr. Koustoov", User.UserRole.ADMIN, Instant.now());
        clinicDao.createUser(admin);

        Optional<User> fetchedUser = clinicDao.findUserByUsername("dr_koustoov");
        assertTrue(fetchedUser.isPresent());
        assertTrue(secService.verifyPassword("MasterDoctor2026!", fetchedUser.get().passwordHash()));
        assertFalse(secService.verifyPassword("WrongPassword", fetchedUser.get().passwordHash()));

        SecurityService.UserSession session = secService.startSession(fetchedUser.get());
        assertTrue(session.canViewPsychiatricNotes());
        assertTrue(secService.isAuthenticated());

        // 3. Patient Creation & Querying
        Patient p = clinicDao.createPatient(
                "CASE-TEST-001", "Synthetic_Subject", "Test_Alpha",
                LocalDate.of(1985, 5, 22), "Male", "Researcher",
                "+91 00000 00000", "test@clinic.local", "Test Facility Premises",
                "O+", "Carbo-nitrogenoid", "PSORA / SYCOSIS"
        );
        assertNotNull(p.id());

        List<Patient> patients = clinicDao.listPatients("Synthetic_Subject");
        assertEquals(1, patients.size());
        assertEquals("CASE-TEST-001", patients.get(0).caseNumber());

        // 4. Consultation & Prescriptions
        Consultation.SelectedRubric sr = new Consultation.SelectedRubric(
                "gen_cold_agg", "GENERALITIES - COLD - in general - agg.", 1, 2.0, false
        );
        Prescription pres = new Prescription(
                UUID.randomUUID().toString(), null, "ars", "Arsenicum album",
                "200C", Prescription.PotencyScale.CENTESIMAL,
                "3 globules dry on tongue", "Single dose", LocalDate.now()
        );
        Consultation consult = new Consultation(
                UUID.randomUUID().toString(), p.id(), Instant.now(),
                "Chronic eczema and severe anxiety worse midnight",
                "Right forearm and flexures", "Burning itching pain",
                "Aggravated cold air, better warm wraps", "Restless thirst for sips",
                "Fastidious, fear of disease", "Chilly patient",
                "Curative progression moving from chest outward to skin",
                "Deep psoric flare", List.of(sr), List.of(pres)
        );
        clinicDao.saveConsultation(consult);

        List<Consultation> consultHistory = clinicDao.getConsultationsForPatient(p.id());
        assertEquals(1, consultHistory.size());
        assertEquals(1, consultHistory.get(0).selectedRubrics().size());
        assertEquals(1, consultHistory.get(0).prescriptions().size());

        // 5. Clinical Color Images & Annotations
        ImageAnnotation ann = new ImageAnnotation(
                UUID.randomUUID().toString(), null, ImageAnnotation.AnnotationType.MEASUREMENT_LINE,
                "[{\"x\":100, \"y\":150}, {\"x\":200, \"y\":150}]", "Plaque Diameter: 24.5mm", "#FF0000"
        );
        ClinicalImage img = new ClinicalImage(
                UUID.randomUUID().toString(), p.id(), consult.id(),
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "PNG", "Right Forearm", "D65_Standardized",
                58.4, 38.2, 18.5, 0.0, "Baseline erythematous plaque",
                Instant.now(), List.of(ann)
        );
        clinicDao.saveClinicalImage(img);

        List<ClinicalImage> patientImages = clinicDao.getImagesForPatient(p.id());
        assertEquals(1, patientImages.size());
        assertEquals(1, patientImages.get(0).annotations().size());
        assertEquals("Plaque Diameter: 24.5mm", patientImages.get(0).annotations().get(0).label());
    }
}
