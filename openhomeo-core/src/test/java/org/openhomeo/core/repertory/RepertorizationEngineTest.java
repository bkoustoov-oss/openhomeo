package org.openhomeo.core.repertory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.Rubric;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RepertorizationEngineTest {

    private static MasterKnowledgeDao dao;
    private static RepertorizationEngine engine;

    @BeforeAll
    public static void setup(@TempDir Path tempDir) {
        DatabaseManager dbManager = new DatabaseManager(tempDir.toString());
        dao = new MasterKnowledgeDao(dbManager);
        engine = new RepertorizationEngine();
    }

    @Test
    public void testClassicalKentianRepertorization() throws Exception {
        Rubric r1 = dao.getRubricWithRemedies("mind_anxiety");
        Rubric r2 = dao.getRubricWithRemedies("stomach_thirst_large_quantities");
        Rubric r3 = dao.getRubricWithRemedies("gen_cold_agg");

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);

        SymptomClipboard clipboard = new SymptomClipboard();
        clipboard.addRubric(r1);
        clipboard.addRubric(r2);
        clipboard.addRubric(r3);

        RepertorizationResult result = engine.calculate(
                clipboard.getActiveItems(), dao, RepertorizationEngine.Options.defaultOptions()
        );

        assertNotNull(result);
        assertEquals(3, result.totalRubricsEvaluated());
        assertFalse(result.rankedRemedies().isEmpty());

        // Top remedy should cover symptoms with high degree
        RepertorizationResult.RemedyScore top = result.rankedRemedies().get(0);
        assertTrue(top.coveredSymptomsCount() >= 2, "Top remedy should cover at least 2 symptoms");
        assertTrue(top.totalScore() >= 6.0, "Top remedy should have significant weighted score");
    }

    @Test
    public void testEliminativeRubricFilter() throws Exception {
        Rubric r1 = dao.getRubricWithRemedies("mind_anxiety"); // Ars, Acon, Phos, Calc, Puls, Rhus-t, Bell, Lyc, Nux-v
        Rubric r2 = dao.getRubricWithRemedies("stomach_desire_sweets"); // Lyc (4), Arg-n (4), Sulph (4), Chin (2)

        SymptomClipboard clipboard = new SymptomClipboard();
        // Mark anxiety as eliminative: only remedies covering anxiety should survive
        clipboard.addRubricToClipboard(1, r1, 1.0, true);
        clipboard.addRubricToClipboard(1, r2, 1.0, false);

        RepertorizationResult result = engine.calculate(
                clipboard.getActiveItems(), dao, RepertorizationEngine.Options.defaultOptions()
        );

        assertTrue(result.eliminativeFilterApplied());
        for (RepertorizationResult.RemedyScore score : result.rankedRemedies()) {
            assertTrue(score.rubricGrades().containsKey(r1.id()),
                    "Remedy " + score.remedyName() + " must contain eliminative rubric " + r1.id());
        }
    }

    @Test
    public void testBoenninghausenPolarityAnalysis() throws Exception {
        // gen_cold_agg is polar opposite to gen_warm_agg
        Rubric rColdAgg = dao.getRubricWithRemedies("gen_cold_agg");
        assertNotNull(rColdAgg);
        assertTrue(rColdAgg.isPolar());
        assertEquals("gen_warm_agg", rColdAgg.oppositeRubricId());

        SymptomClipboard clipboard = new SymptomClipboard();
        clipboard.addRubric(rColdAgg);

        RepertorizationResult result = engine.calculate(
                clipboard.getActiveItems(), dao, RepertorizationEngine.Options.polarityAnalysis()
        );

        assertTrue(result.polarityAnalysisActive());

        // Arsenicum is prominent in cold agg (grade 4), so Delta-P should be positive
        var arsScore = result.rankedRemedies().stream()
                .filter(r -> r.remedyId().equals("ars"))
                .findFirst();
        assertTrue(arsScore.isPresent());
        assertTrue(arsScore.get().polarityDifference() > 0, "Arsenicum should have positive Delta-P for cold agg");

        // Pulsatilla is grade 4 in warm agg (contraindicated for cold agg)
        var pulsScore = result.rankedRemedies().stream()
                .filter(r -> r.remedyId().equals("puls"))
                .findFirst();
        if (pulsScore.isPresent()) {
            assertTrue(pulsScore.get().isContraindicated(), "Pulsatilla should be flagged as contraindicated for cold agg");
        }
    }

    @Test
    public void testTaxonomyKingdomFilter() throws Exception {
        Rubric r1 = dao.getRubricWithRemedies("mind_anxiety");
        SymptomClipboard clipboard = new SymptomClipboard();
        clipboard.addRubric(r1);

        RepertorizationEngine.Options plantOnly = new RepertorizationEngine.Options(
                RepertorizationEngine.Algorithm.KENTIAN_SUM_OF_DEGREES, "Plant", null, null, false
        );

        RepertorizationResult result = engine.calculate(clipboard.getActiveItems(), dao, plantOnly);
        for (RepertorizationResult.RemedyScore score : result.rankedRemedies()) {
            assertEquals("Plant", score.kingdom(), "Filtered remedy must belong to Plant kingdom");
        }
    }
}
