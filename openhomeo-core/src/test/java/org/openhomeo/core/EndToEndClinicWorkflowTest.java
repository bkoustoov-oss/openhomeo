package org.openhomeo.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.ai.CaseSynthesisService;
import org.openhomeo.core.ai.SemanticSearchService;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.image.ClinicalColorAnalyzer;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.*;
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.RepertorizationResult;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.openhomeo.core.security.SecurityService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Clinical Case Simulation Test.
 * Simulates an entire practitioner workflow from login to discharge.
 */
public class EndToEndClinicWorkflowTest {

    @Test
    public void testCompletePractitionerClinicalWorkflow(@TempDir Path tempDir) throws Exception {
        // 1. Initialize Databases & Services
        DatabaseManager dbManager = new DatabaseManager(tempDir.toString());
        dbManager.initializeClinicSchema();

        MasterKnowledgeDao masterDao = new MasterKnowledgeDao(dbManager);
        ClinicDao clinicDao = new ClinicDao(dbManager);
        SecurityService securityService = new SecurityService();
        ImageVaultService imageVault = new ImageVaultService(tempDir.resolve("vault").toString());
        SymptomClipboard clipboard = new SymptomClipboard();
        RepertorizationEngine repEngine = new RepertorizationEngine();
        SemanticSearchService semanticSearch = new SemanticSearchService(masterDao);
        CaseSynthesisService caseSynthesis = new CaseSynthesisService();

        // 2. Authenticate Homeopath
        String passHash = securityService.hashPassword("ClinicSecret2026!");
        User doctor = new User(UUID.randomUUID().toString(), "dr_homeopath", passHash, "Dr. Hahnemannian", User.UserRole.HOMEOPATH, Instant.now());
        clinicDao.createUser(doctor);

        assertTrue(securityService.verifyPassword("ClinicSecret2026!", doctor.passwordHash()));
        SecurityService.UserSession session = securityService.startSession(doctor);
        assertTrue(session.canViewPsychiatricNotes());

        // 3. Register New Patient
        Patient patient = clinicDao.createPatient(
                "CASE-TEST-002", "Synthetic_Subject", "Test_Beta",
                LocalDate.of(1992, 8, 14), "Female", "Educator",
                "+91 00000 00000", "test@clinic.local", "Test Facility Premises",
                "A+", "Phosphoric", "PSORA / TUBERCULAR"
        );
        assertNotNull(patient.id());

        // 4. Clinical Case Taking: Unstructured Raw Notes -> L-S-M-C Extraction
        String narrative = "Patient complains of intense anxiety in the evening in bed, severe burning headache right side forehead worse motion and cold air. Great physical restlessness and fear of death.";
        CaseSynthesisService.ExtractedLsmc lsmc = caseSynthesis.extractLsmcFromNotes(narrative);
        assertEquals("Head / Forehead", lsmc.location());
        assertTrue(lsmc.sensation().contains("Burning"));
        assertTrue(lsmc.modalities().contains("Cold"));

        // 5. Semantic Rubric Search & Repertorization
        List<SemanticSearchService.SemanticMatch> matches = semanticSearch.searchSemantic(narrative, 5);
        assertFalse(matches.isEmpty(), "Semantic search should discover rubrics");

        // Add rubrics to clipboard
        Rubric rAnxiety = masterDao.getRubricWithRemedies("mind_anxiety_evening_bed");
        Rubric rHeadache = masterDao.getRubricWithRemedies("head_pain_cold_air_agg");
        Rubric rColdAgg = masterDao.getRubricWithRemedies("gen_cold_agg");

        assertNotNull(rAnxiety);
        assertNotNull(rHeadache);
        assertNotNull(rColdAgg);

        clipboard.addRubric(rAnxiety);
        clipboard.addRubric(rHeadache);
        clipboard.addRubric(rColdAgg);

        // Run Repertorization Matrix (Polarity Mode)
        RepertorizationResult repResult = repEngine.calculate(
                clipboard.getActiveItems(), masterDao, RepertorizationEngine.Options.polarityAnalysis()
        );
        assertFalse(repResult.rankedRemedies().isEmpty());

        RepertorizationResult.RemedyScore simillimum = repResult.rankedRemedies().get(0);
        assertNotNull(simillimum.remedyName());
        assertTrue(simillimum.coveredSymptomsCount() >= 2);

        // Differential Analysis between top 2 remedies
        CaseSynthesisService.DifferentialAnalysis diff = caseSynthesis.generateDifferential(repResult);
        assertNotNull(diff.comparisonSummary());

        // 6. Clinical Photography & CIE-L*a*b* Baseline Analysis
        BufferedImage baselineImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = baselineImg.createGraphics();
        g1.setColor(new Color(235, 45, 45)); // Bright red acute dermatitis
        g1.fillRect(0, 0, 100, 100);
        g1.dispose();

        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        ImageIO.write(baselineImg, "PNG", baos1);
        ImageVaultService.StoreResult vRes1 = imageVault.storeImage(baos1.toByteArray(), "PNG");

        ClinicalColorAnalyzer.LabStats baseStats = ClinicalColorAnalyzer.analyzeRegion(baselineImg, null);

        ImageAnnotation ann1 = new ImageAnnotation(
                UUID.randomUUID().toString(), null, ImageAnnotation.AnnotationType.MEASUREMENT_LINE,
                "[{\"x\":10, \"y\":20}, {\"x\":80, \"y\":20}]", "Lesion Width: 32mm", "#FF0000"
        );

        ClinicalImage imgVisit1 = new ClinicalImage(
                UUID.randomUUID().toString(), patient.id(), null,
                vRes1.contentHash(), "PNG", "Face / Cheek",
                "D65_Standardized", baseStats.meanL(), baseStats.meanA(), baseStats.meanB(), 0.0,
                "Baseline acute flare", Instant.now(), List.of(ann1)
        );
        clinicDao.saveClinicalImage(imgVisit1);

        // 7. Save Visit #1 Consultation & Prescription
        String consult1Id = UUID.randomUUID().toString();
        Prescription rx1 = new Prescription(
                UUID.randomUUID().toString(), consult1Id, simillimum.remedyId(), simillimum.remedyName(),
                "200C", Prescription.PotencyScale.CENTESIMAL,
                "3 pellets dissolved in 100ml water, take 1 teaspoon", "Single dose, observe 4 weeks",
                LocalDate.now()
        );

        Consultation consult1 = new Consultation(
                consult1Id, patient.id(), Instant.now(),
                narrative, lsmc.location(), lsmc.sensation(), lsmc.modalities(),
                lsmc.concomitants(), lsmc.mentalKeynotes(), "Chilly, thirsty for sips",
                "Baseline visit: starting chronic homeopathic treatment", "Severe anxiety and rash",
                List.of(new Consultation.SelectedRubric(rAnxiety.id(), rAnxiety.fullPath(), 1, 1.0, false)),
                List.of(rx1)
        );
        clinicDao.saveConsultation(consult1);

        // 8. Follow-Up Visit Simulation (6 Weeks Later)
        // Lesion healed: redness dropped from (235, 45, 45) to normal skin tone (215, 170, 150)
        BufferedImage followUpImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = followUpImg.createGraphics();
        g2.setColor(new Color(215, 170, 150));
        g2.fillRect(0, 0, 100, 100);
        g2.dispose();

        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        ImageIO.write(followUpImg, "PNG", baos2);
        ImageVaultService.StoreResult vRes2 = imageVault.storeImage(baos2.toByteArray(), "PNG");

        ClinicalColorAnalyzer.LabStats followUpStats = ClinicalColorAnalyzer.analyzeRegion(followUpImg, null);
        ClinicalColorAnalyzer.ComparisonResult comp = ClinicalColorAnalyzer.compareProgression(baseStats, followUpStats);

        // Verify objective inflammation reduction
        assertTrue(comp.deltaE00() > 10.0, "Delta-E must confirm perceptible clinical color shift");
        assertTrue(comp.erythemaDeltaA() < -10.0, "Redness on a* axis must drop significantly upon cure");
        assertTrue(comp.clinicalInterpretation().contains("reduction in erythema"));

        ClinicalImage imgVisit2 = new ClinicalImage(
                UUID.randomUUID().toString(), patient.id(), null,
                vRes2.contentHash(), "PNG", "Face / Cheek",
                "D65_Standardized", followUpStats.meanL(), followUpStats.meanA(), followUpStats.meanB(),
                comp.deltaE00(), "Follow-up visit: lesion resolved", Instant.now(), List.of()
        );
        clinicDao.saveClinicalImage(imgVisit2);

        // Record Follow-Up Consultation with Hering's Law Confirmation
        String consult2Id = UUID.randomUUID().toString();
        Consultation consult2 = new Consultation(
                consult2Id, patient.id(), Instant.now(),
                "Follow-up: Headache completely gone. Anxiety improved 90%. Facial rash cleared.",
                "Face cleared", "No burning", "> Warmth", "Calm",
                "Mental calm and confident", "Appetite normalized",
                "Hering's Law Confirmed: Symptoms cured from within outward, head to extremities, mind to skin.",
                "Curative response to Simillimum. Placebo (Sac Lac) given.",
                List.of(), List.of()
        );
        clinicDao.saveConsultation(consult2);

        // 9. Verify Patient Clinical History
        List<Consultation> fullHistory = clinicDao.getConsultationsForPatient(patient.id());
        assertEquals(2, fullHistory.size(), "Should have exactly 2 consultations recorded");

        List<ClinicalImage> patientImages = clinicDao.getImagesForPatient(patient.id());
        assertEquals(2, patientImages.size(), "Should have exactly 2 clinical photographs in vault");
    }
}
