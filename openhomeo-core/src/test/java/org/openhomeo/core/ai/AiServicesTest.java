package org.openhomeo.core.ai;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.Rubric;
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.RepertorizationResult;
import org.openhomeo.core.repertory.SymptomClipboard;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiServicesTest {

    private static MasterKnowledgeDao dao;
    private static SemanticSearchService semanticSearch;
    private static CaseSynthesisService caseSynthesis;

    @BeforeAll
    public static void setup(@TempDir Path tempDir) {
        DatabaseManager dbManager = new DatabaseManager(tempDir.toString());
        dao = new MasterKnowledgeDao(dbManager);
        semanticSearch = new SemanticSearchService(dao);
        caseSynthesis = new CaseSynthesisService();
    }

    @Test
    public void testSemanticSearchMatching() throws Exception {
        String patientNarrative = "Sudden intense anxiety in the evening when lying in bed";
        List<SemanticSearchService.SemanticMatch> matches = semanticSearch.searchSemantic(patientNarrative, 5);

        assertNotNull(matches);
        assertFalse(matches.isEmpty(), "Semantic search should return candidate rubrics");

        SemanticSearchService.SemanticMatch topMatch = matches.get(0);
        assertTrue(topMatch.similarityScore() > 0.3f, "Top match should have high similarity score");
        assertTrue(topMatch.rubric().fullPath().contains("ANXIETY") || topMatch.rubric().fullPath().contains("bed"),
                "Top match should be clinically relevant to query: " + topMatch.rubric().fullPath());
    }

    @Test
    public void testLsmcExtractionAndDifferential() throws Exception {
        String rawNotes = "Patient has severe burning pain in forehead worse with motion and cold air. Great physical restlessness and anxiety.";
        CaseSynthesisService.ExtractedLsmc lsmc = caseSynthesis.extractLsmcFromNotes(rawNotes);

        assertNotNull(lsmc);
        assertTrue(lsmc.location().contains("Head"));
        assertTrue(lsmc.sensation().contains("Burning"));
        assertTrue(lsmc.modalities().contains("Cold"));
        assertTrue(lsmc.mentalKeynotes().contains("anxiety"));

        // Test Differential Analysis
        Rubric r1 = dao.getRubricWithRemedies("mind_anxiety");
        Rubric r2 = dao.getRubricWithRemedies("gen_cold_agg");

        SymptomClipboard clipboard = new SymptomClipboard();
        clipboard.addRubric(r1);
        clipboard.addRubric(r2);

        RepertorizationEngine engine = new RepertorizationEngine();
        RepertorizationResult result = engine.calculate(clipboard.getActiveItems(), dao, RepertorizationEngine.Options.defaultOptions());

        CaseSynthesisService.DifferentialAnalysis diff = caseSynthesis.generateDifferential(result);
        assertNotNull(diff);
        assertFalse(diff.primaryRemedyId().isEmpty());
        assertFalse(diff.secondaryRemedyId().isEmpty());
        assertFalse(diff.distinguishingKeynotes().isEmpty());
        assertTrue(diff.comparisonSummary().contains("Differential analysis"));
    }
}
