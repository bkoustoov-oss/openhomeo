package org.openhomeo.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhomeo.core.ai.FollowUpEvaluationEngine;
import org.openhomeo.core.ai.PrescriptionLogicAnalyzer;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.*;
import org.openhomeo.core.model.FollowUpEvaluation.ReactionType;
import org.openhomeo.core.model.FollowUpEvaluation.SymptomReaction;
import org.openhomeo.core.repertory.FamilyTaxonomyService;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SynthesisFamiliesAndWinChipTest {

    private File tempDir;
    private DatabaseManager dbManager;
    private MasterKnowledgeDao masterDao;
    private ClinicDao clinicDao;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "openhomeo-test-" + UUID.randomUUID());
        tempDir.mkdirs();

        dbManager = new DatabaseManager(tempDir.getAbsolutePath());
        dbManager.initializeClinicSchema();
        masterDao = new MasterKnowledgeDao(dbManager);
        clinicDao = new ClinicDao(dbManager);
    }

    @AfterEach
    void tearDown() {
        if (tempDir != null && tempDir.exists()) {
            deleteRecursively(tempDir);
        }
    }

    private void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File child : files) deleteRecursively(child);
            }
        }
        f.delete();
    }

    @Test
    void testFamiliesTaxonomySeedingAndService() throws SQLException {
        List<FamilyTaxonomy> families = masterDao.getAllFamilies();
        assertFalse(families.isEmpty(), "Families table should contain seeded taxonomies");

        // Verify Aconitum in Ranunculaceae
        Optional<FamilyTaxonomy> acon = masterDao.getFamilyTaxonomy("acon");
        assertTrue(acon.isPresent(), "Aconitum taxonomy should be present");
        assertEquals("Plant", acon.get().kingdom());
        assertEquals("Ranunculaceae", acon.get().familyOrStage());
        assertEquals("ACUTE", acon.get().miasmPrimary());

        // Verify Arsenicum in Mineral Series 4 Ferrum Stage 15
        Optional<FamilyTaxonomy> ars = masterDao.getFamilyTaxonomy("ars");
        assertTrue(ars.isPresent(), "Arsenicum taxonomy should be present");
        assertEquals("Mineral", ars.get().kingdom());
        assertEquals("Stage 15 (Arsenic)", ars.get().familyOrStage());
        assertEquals("Series 4 (Ferrum)", ars.get().orderOrSeries());

        // Verify Indigenous Indian medicine (Azadirachta indica / Neem)
        Optional<FamilyTaxonomy> neem = masterDao.getFamilyTaxonomy("azad-i");
        assertTrue(neem.isPresent(), "Neem should be present");
        assertEquals("Meliaceae", neem.get().familyOrStage());

        // Test FamilyTaxonomyService distribution
        FamilyTaxonomyService service = new FamilyTaxonomyService(masterDao);
        FamilyTaxonomyService.FamilyDistribution dist = service.calculateDistribution(List.of("acon", "bell", "arn", "ars", "lach", "psor"));
        assertEquals(6, dist.totalRemediesEvaluated());
        assertFalse(dist.kingdomDistribution().isEmpty());
        assertFalse(dist.familyOrStageDistribution().isEmpty());
        assertFalse(dist.miasmDistribution().isEmpty());
    }

    @Test
    void testSynthesisAdditionsAndCrossReferences() throws SQLException {
        // Test cross references
        List<RubricCrossReference> refs = masterDao.getRubricCrossReferences("mind-fear-death");
        assertFalse(refs.isEmpty(), "Rubric cross references should be seeded for fear of death");
        assertEquals("mind-anx-health", refs.get(0).targetRubricId());
        assertEquals(RubricCrossReference.RelationshipType.SEE_ALSO, refs.get(0).relationshipType());

        // Test additions with author citations
        List<RubricRemedyAddition> additions = masterDao.getRubricAdditions("mind-fear-death");
        assertFalse(additions.isEmpty(), "Rubric additions should exist with author citations");
        boolean hasKent = additions.stream().anyMatch(a -> a.authorCode().equals("k1"));
        assertTrue(hasKent, "Kent author citation (k1) should be present");
    }

    @Test
    void testMateriaMedicaDigitalLibraryFts() throws SQLException {
        List<MateriaMedicaBook> books = masterDao.getAllMateriaMedicaBooks();
        assertFalse(books.isEmpty(), "Materia Medica books should be indexed");
        assertTrue(books.stream().anyMatch(b -> b.title().contains("Organon")), "Hahnemann Organon should be indexed");
        assertTrue(books.stream().anyMatch(b -> b.title().contains("Kent") || b.author().contains("Kent")), "Kent Lectures should be indexed");
        assertTrue(books.stream().anyMatch(b -> b.title().contains("Boericke") || b.author().contains("Boericke")), "Boericke should be indexed");

        // Test Full-Text Search (FTS5 BM25)
        List<MateriaMedicaSearchResult> results = masterDao.searchMateriaMedicaFts("burning heat amel");
        assertFalse(results.isEmpty(), "FTS5 query 'burning heat amel' should return results");
        MateriaMedicaSearchResult first = results.get(0);
        assertNotNull(first.bookTitle());
        assertNotNull(first.remedyName());
        assertNotNull(first.snippet());
    }

    @Test
    void testWinChipFollowUpKentObservations() {
        FollowUpEvaluationEngine engine = new FollowUpEvaluationEngine();

        // 1. Observation 3: Short mild agg followed by rapid cure
        List<SymptomReaction> reactionsObs3 = List.of(
                new SymptomReaction("Violent headache", ReactionType.AGGRAVATED, "Slightly intensified on Day 1"),
                new SymptomReaction("Violent headache", ReactionType.AMELIORATED_MAJOR, "Completely disappeared on Day 3"),
                new SymptomReaction("General vitality", ReactionType.IMPROVED, "Sleep and appetite restored")
        );
        FollowUpEvaluationEngine.EvaluationOutcome outcome3 = engine.evaluateFollowUp(reactionsObs3, true, false, false, 7);
        assertEquals(3, outcome3.kentObservationNumber(), "Should detect Kent's 3rd Observation");
        assertTrue(outcome3.heringsLawScore() >= 90.0);
        assertEquals("FULLY_ALIGNED", outcome3.heringsDirectionStatus());

        // 2. Observation 11: When old symptoms return
        List<SymptomReaction> reactionsObs11 = List.of(
                new SymptomReaction("Bronchial asthma", ReactionType.AMELIORATED_MAJOR, "Breathing completely clear"),
                new SymptomReaction("Childhood eczema on elbow", ReactionType.OLD_SYMPTOM_RETURNED, "Eczema from 5 years ago returned")
        );
        FollowUpEvaluationEngine.EvaluationOutcome outcome11 = engine.evaluateFollowUp(reactionsObs11, true, true, false, 28);
        assertEquals(11, outcome11.kentObservationNumber(), "Should detect Kent's 11th Observation");
        assertEquals(100.0, outcome11.heringsLawScore(), "Old symptoms returning is 100% Hering compliant");
        assertTrue(outcome11.recommendedAction().contains("DO NOT INTERVENE"));

        // 3. Observation 12: Wrong direction (Suppression warning)
        FollowUpEvaluationEngine.EvaluationOutcome outcome12 = engine.evaluateFollowUp(reactionsObs11, false, false, true, 14);
        assertEquals(12, outcome12.kentObservationNumber(), "Should detect Kent's 12th Observation (Suppression)");
        assertEquals("SUPPRESSION_WARNING", outcome12.heringsDirectionStatus());
    }

    @Test
    void testSocraticDoctorInterrogationAndExperienceCapture() throws SQLException {
        PrescriptionLogicAnalyzer analyzer = new PrescriptionLogicAnalyzer();

        // Doctor response citing Organon §153 peculiar keynote
        String doctorAnswerKeynote = "Prescribed because patient presents an uncommon, striking peculiar keynote under Organon §153: burning pains relieved by warm applications despite a warm environment.";
        PrescriptionLogicAnalyzer.SocraticDialogueEvaluation eval = analyzer.evaluateDoctorSocraticResponse("THERMAL_CONTRADICTION", doctorAnswerKeynote);

        assertTrue(eval.isFullySatisfied(), "Doctor citing §153 peculiar keynote should logically satisfy the AI reasoner");
        assertTrue(eval.satisfactionScore() >= 75);
        assertEquals("ORGANON_153_PECULIAR_KEYNOTE", eval.justificationCategory());

        // Doctor Clinical Experience Capture persistence in clinicDao
        DoctorClinicalExperience exp = new DoctorClinicalExperience(
                UUID.randomUUID().toString(),
                "doc-test",
                "Dr. Consulting Physician",
                "pat-123",
                "CASE-9901",
                "ars",
                "Arsenicum album",
                "Thermal Polarity: Prescribed Chilly remedy for Hot patient",
                DoctorClinicalExperience.ExperienceType.VERIFIED_CLINICAL_KEYNOTE,
                "Observed in over 15 tropical clinical cases that burning relieved by warmth supersedes the general thermal reaction.",
                "Burning heat amel in tropical climate",
                "VERIFIED_IN_PRACTICE",
                Instant.now()
        );

        clinicDao.saveDoctorClinicalExperience(exp);

        List<DoctorClinicalExperience> savedList = clinicDao.getExperiencesForRemedy("ars");
        assertFalse(savedList.isEmpty(), "Saved clinical experience should be retrievable from the vault");
        assertEquals("doc-test", savedList.get(0).doctorId());
        assertEquals("Arsenicum album", savedList.get(0).remedyName());
        assertEquals(DoctorClinicalExperience.ExperienceType.VERIFIED_CLINICAL_KEYNOTE, savedList.get(0).experienceType());
    }
}
