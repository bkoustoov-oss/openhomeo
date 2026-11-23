package org.openhomeo.core.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EntryPointSuggestionEngineTest {

    private final EntryPointSuggestionEngine engine = new EntryPointSuggestionEngine();

    @Test
    public void testEtiologyAndMotionWettingPoints() {
        var result = engine.analyze(
                "Joint pains and severe stiffness after being caught in heavy rain",
                "Right knee and lumbar spine",
                "Tearing, aching stiffness",
                "< initial motion, > continued motion, < damp cold weather",
                "Restless tossing in bed",
                "Anxious, irritable",
                "Chilly, desires warm drinks",
                List.of("GENERALITIES - COLD - in general - agg.")
        );

        assertNotNull(result);
        assertNotNull(result.primarySuggestion());
        assertEquals("rhus-t", result.primarySuggestion().remedyId());
        assertTrue(result.primarySuggestion().confidenceScore() >= 60.0);
        assertTrue(result.detectedEntryPoints().stream().anyMatch(p -> p.contains("getting wet")));
    }

    @Test
    public void testIndianRemedyClinicalFocusDiabetes() {
        var result = engine.analyze(
                "High urinary sugar and extreme debility",
                "Pancreatic and urinary system",
                "Burning along urethra",
                "< night, < sweets",
                "Extreme prostration",
                "Melancholy",
                "Unquenchable thirst for large quantities of water, profuse polyuria",
                List.of("ABDOMEN - DIABETES MELLITUS - glycosuria", "STOMACH - THIRST - large quantities")
        );

        assertNotNull(result);
        assertNotNull(result.primarySuggestion());
        assertTrue(result.primarySuggestion().remedyId().equals("syzyg-j") || result.primarySuggestion().remedyId().equals("bry"));
    }

    @Test
    public void testMechanicalTraumaEntryPoints() {
        var result = engine.analyze(
                "Blunt injury and contusion to left shoulder from a fall",
                "Left shoulder",
                "Bruised, sore, bed feels too hard",
                "< slightest touch, < motion",
                "Says nothing is wrong with him",
                "Claims he is healthy",
                "Hot head with cold limbs",
                List.of()
        );

        assertNotNull(result);
        assertEquals("arn", result.primarySuggestion().remedyId());
        assertTrue(result.detectedEntryPoints().stream().anyMatch(p -> p.contains("trauma")));
    }
}
