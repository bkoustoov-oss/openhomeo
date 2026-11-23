package org.openhomeo.core.db;

import org.openhomeo.core.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MedicineConstituentsAndFaithDossierTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;
    private ClinicDao clinicDao;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = new DatabaseManager(tempDir.getAbsolutePath());
        dbManager.initializeClinicSchema();
        clinicDao = new ClinicDao(dbManager);
    }

    @Test
    void testMedicineConstituentsSeededAndRetrieved() throws SQLException {
        // Test Calendula (oleanolic acid / fibroblast proliferation)
        Optional<MedicineConstituent> calen = clinicDao.getMedicineConstituent("calen");
        assertTrue(calen.isPresent());
        assertTrue(calen.get().activeConstituents().toLowerCase().contains("oleanolic acid"));
        assertTrue(calen.get().pharmacologicalAction().toLowerCase().contains("fibroblast proliferation"));

        // Test Arnica (helenalin / platelet aggregation)
        Optional<MedicineConstituent> arn = clinicDao.getMedicineConstituent("arn");
        assertTrue(arn.isPresent());
        assertTrue(arn.get().activeConstituents().toLowerCase().contains("helenalin"));
        assertTrue(arn.get().pharmacologicalAction().toLowerCase().contains("platelet aggregation"));

        // Test Hypericum (hypericin / neurogenic pain)
        Optional<MedicineConstituent> hyper = clinicDao.getMedicineConstituent("hyper");
        assertTrue(hyper.isPresent());
        assertTrue(hyper.get().activeConstituents().toLowerCase().contains("hypericin"));
        assertTrue(hyper.get().pharmacologicalAction().toLowerCase().contains("neurogenic"));

        // Test Indian remedy: Rauwolfia (reserpine / sarpagandha)
        Optional<MedicineConstituent> rauw = clinicDao.getMedicineConstituent("rauw");
        assertTrue(rauw.isPresent());
        assertTrue(rauw.get().commonAndIndianName().toLowerCase().contains("sarpagandha"));
        assertTrue(rauw.get().activeConstituents().toLowerCase().contains("reserpine"));

        // Test Indian remedy: Terminalia arjuna (arjunic acid / CoQ10)
        Optional<MedicineConstituent> arjun = clinicDao.getMedicineConstituent("arjun");
        assertTrue(arjun.isPresent());
        assertTrue(arjun.get().commonAndIndianName().toLowerCase().contains("arjuna"));
        assertTrue(arjun.get().activeConstituents().toLowerCase().contains("coenzyme q10") || arjun.get().activeConstituents().toLowerCase().contains("arjunic"));

        // Test search
        List<MedicineConstituent> searchResults = clinicDao.searchMedicineConstituents("fibroblast");
        assertFalse(searchResults.isEmpty());
        assertEquals("calen", searchResults.get(0).remedyId());

        // Verify exhaustive superset count (880+ remedies from combined Indian Pharmacopoeia and world polychrests)
        List<MedicineConstituent> allConstituents = clinicDao.getAllMedicineConstituents();
        assertTrue(allConstituents.size() >= 880, "Master medicine constituent database must contain >= 880 remedies, found: " + allConstituents.size());
    }

    @Test
    void testClassicalAndIndianClinicalRhymes() {
        // Polychrests
        String aconRhyme = org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes.getRhyme("acon");
        assertNotNull(aconRhyme);
        assertTrue(aconRhyme.contains("Aconite calms the tempest cry"));

        String arnRhyme = org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes.getRhyme("arn");
        assertNotNull(arnRhyme);
        assertTrue(arnRhyme.contains("Arnica brings the healing true"));

        String nuxRhyme = org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes.getRhyme("nux-v");
        assertNotNull(nuxRhyme);
        assertTrue(nuxRhyme.contains("restores the digestive rule"));

        // Indian indigenous remedies
        String neemRhyme = org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes.findRhyme("azad-i", "Azadirachta indica", "Neem / Margosa Tree / Nimba");
        assertNotNull(neemRhyme);
        assertTrue(neemRhyme.contains("Neem"));

        String arjunRhyme = org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes.findRhyme("arjun", "Terminalia arjuna", "Arjuna");
        assertNotNull(arjunRhyme);
        assertTrue(arjunRhyme.contains("Arjuna"));

        String giloyRhyme = org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes.findRhyme("tinos-c", "Tinospora cordifolia", "Giloy / Guduchi");
        assertNotNull(giloyRhyme);
        assertTrue(giloyRhyme.contains("Giloy"));
    }

    @Test
    void testDoctorRemarkSubmissionAndAdminAnnexationWorkflow() throws SQLException {
        // 1. Doctor submits proposed remark on Arnica
        MedicineConstituentRemark remark = new MedicineConstituentRemark(
                UUID.randomUUID().toString(),
                "arn",
                "usr-doc",
                "Dr. S. K. Mukherjee",
                "Clinical observation: Highly synergistic with Bellis perennis in deep pelvic hematoma post-caesarean.",
                MedicineConstituentRemark.Status.PENDING,
                null,
                Instant.now(),
                null
        );
        clinicDao.submitConstituentRemark(remark);

        // 2. Remark appears in pending list
        List<MedicineConstituentRemark> pending = clinicDao.getPendingConstituentRemarks();
        assertFalse(pending.isEmpty());
        assertTrue(pending.stream().anyMatch(r -> r.id().equals(remark.id())));

        // 3. Admin approves remark
        boolean approved = clinicDao.approveConstituentRemark(remark.id(), "usr-admin");
        assertTrue(approved);

        // 4. Verify remark is no longer pending
        List<MedicineConstituentRemark> pendingAfter = clinicDao.getPendingConstituentRemarks();
        assertFalse(pendingAfter.stream().anyMatch(r -> r.id().equals(remark.id())));

        // 5. Verify remark is annexed into the main medicine constituent record with timestamp and user
        Optional<MedicineConstituent> updatedArn = clinicDao.getMedicineConstituent("arn");
        assertTrue(updatedArn.isPresent());
        assertTrue(updatedArn.get().annexedRemarks().contains("Dr. S. K. Mukherjee"));
        assertTrue(updatedArn.get().annexedRemarks().contains("Bellis perennis"));
    }

    @Test
    void testSecretReportOfFaithDossierRecording() throws SQLException {
        // Secret Report of Faith entry
        AdminFaithDossierEntry entry = new AdminFaithDossierEntry(
                UUID.randomUUID().toString(),
                "cons-test-99",
                "pat-test-1",
                "Test Patient",
                "doc-test-1",
                "Dr. Test Prescriber",
                "lyc",
                "Lycopodium clavatum",
                "30C",
                "TDS x 7 days",
                "KENT_30C_OVERREPETITION",
                AdminFaithDossierEntry.RuleSeverity.WARNING,
                "Routine prescription for immediate effect",
                "HIGH_HAZARD",
                "Medicinal proving and persistent aggravation",
                false,
                Instant.now()
        );

        clinicDao.recordFaithDossierEntry(entry);

        List<AdminFaithDossierEntry> dossier = clinicDao.getFaithDossierEntries();
        assertFalse(dossier.isEmpty());
        AdminFaithDossierEntry retrieved = dossier.get(0);
        assertEquals("KENT_30C_OVERREPETITION", retrieved.violatedRule());
        assertFalse(retrieved.isAudited());

        // Mark audited
        clinicDao.markFaithDossierAudited(retrieved.id());
        List<AdminFaithDossierEntry> dossierAfter = clinicDao.getFaithDossierEntries();
        assertTrue(dossierAfter.get(0).isAudited());
    }

    @Test
    void testFilteredPatientSearch() throws SQLException {
        // Create diverse test patients
        clinicDao.createPatient("CASE-001", "Aarav", "Patel", LocalDate.of(2018, 5, 12), "Male", "Student", "9990001111", "", "", "O+", "Carbonic", "PSORA");
        clinicDao.createPatient("CASE-002", "Sunita", "Banerjee", LocalDate.of(1985, 3, 20), "Female", "Professor", "9990002222", "", "", "A+", "Phosphoric", "SYCOSIS");
        clinicDao.createPatient("CASE-003", "Harish", "Chopra", LocalDate.of(1950, 11, 4), "Male", "Retired", "9990003333", "", "", "B+", "Fluoric", "SYPHILIS");

        // Filter by gender Female
        List<Patient> females = clinicDao.searchPatientsFiltered("", "ALL", "Female", "ALL", "ALL", "ALL");
        assertEquals(1, females.size());
        assertEquals("Banerjee", females.get(0).lastName());

        // Filter by pediatric age (< 12 yrs)
        List<Patient> peds = clinicDao.searchPatientsFiltered("", "ALL", "ALL", "Pediatric (< 12 yrs)", "ALL", "ALL");
        assertEquals(1, peds.size());
        assertEquals("Aarav", peds.get(0).firstName());

        // Filter by Geriatric age (> 60 yrs)
        List<Patient> geriatrics = clinicDao.searchPatientsFiltered("", "ALL", "ALL", "Geriatric (> 60 yrs)", "ALL", "ALL");
        assertEquals(1, geriatrics.size());
        assertEquals("Harish", geriatrics.get(0).firstName());

        // Filter by Miasm SYCOSIS
        List<Patient> sycotic = clinicDao.searchPatientsFiltered("", "ALL", "ALL", "ALL", "ALL", "SYCOSIS");
        assertEquals(1, sycotic.size());
        assertEquals("Sunita", sycotic.get(0).firstName());
    }

    @Test
    void testPharmacistAndUserManagement() throws SQLException {
        // Enlist Pharmacist
        Pharmacist p = new Pharmacist(
                "pharma-new", "B. K. Sen", "PH-9912", "B.Pharm (Hom.)", "South Kolkata Branch Dispensary",
                "Kolkata", "9830099999", "bksen@clinic.org", true, Instant.now()
        );
        clinicDao.savePharmacist(p);

        Optional<Pharmacist> fetchedP = clinicDao.getPharmacistById("pharma-new");
        assertTrue(fetchedP.isPresent());
        assertEquals("B. K. Sen", fetchedP.get().fullName());

        // User management
        User newUser = clinicDao.createUser("dr_roy", "royPass123", "Dr. B. C. Roy, M.D.", User.UserRole.DOCTOR);
        assertEquals("dr_roy", newUser.username());
        assertEquals(User.UserRole.DOCTOR, newUser.role());

        clinicDao.updateUserRole(newUser.id(), User.UserRole.ADMIN);
        List<User> users = clinicDao.getAllUsers();
        User updated = users.stream().filter(u -> u.id().equals(newUser.id())).findFirst().orElseThrow();
        assertEquals(User.UserRole.ADMIN, updated.role());
    }
}
