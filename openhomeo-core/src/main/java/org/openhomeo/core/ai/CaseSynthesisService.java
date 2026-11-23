package org.openhomeo.core.ai;

import org.openhomeo.core.model.Consultation;
import org.openhomeo.core.model.Remedy;
import org.openhomeo.core.repertory.RepertorizationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Clinical Case Synthesis & Differential Diagnosis Assistant.
 * Formulates structured L-S-M-C breakdowns from raw patient case notes
 * and prepares differential analysis summaries between top candidate remedies.
 * Compatible with local llama.cpp / GGUF offline models via Project Panama FFM.
 */
public class CaseSynthesisService {

    public record ExtractedLsmc(
            String location,
            String sensation,
            String modalities,
            String concomitants,
            String mentalKeynotes
    ) {}

    public record DifferentialAnalysis(
            String primaryRemedyId,
            String secondaryRemedyId,
            String comparisonSummary,
            List<String> distinguishingKeynotes
    ) {}

    /**
     * Extracts and structures L-S-M-C elements from free-flowing patient narratives.
     */
    public ExtractedLsmc extractLsmcFromNotes(String narrative) {
        if (narrative == null || narrative.isBlank()) {
            return new ExtractedLsmc("", "", "", "", "");
        }

        String lower = narrative.toLowerCase();

        String loc = "";
        if (lower.contains("forehead") || lower.contains("head") || lower.contains("temple")) loc = "Head / Forehead";
        else if (lower.contains("stomach") || lower.contains("abdomen")) loc = "Stomach / Epigastrium";
        else if (lower.contains("arm") || lower.contains("hand") || lower.contains("skin")) loc = "Skin & Extremities";

        String sens = "";
        if (lower.contains("burning")) sens = "Burning like fire / hot coals";
        else if (lower.contains("pulsat") || lower.contains("throbbing")) sens = "Pulsating / Throbbing";
        else if (lower.contains("stitch")) sens = "Stitching pain";
        else if (lower.contains("cramp")) sens = "Cramping / Spasmodic";

        String mod = "";
        if (lower.contains("cold") && (lower.contains("worse") || lower.contains("agg"))) mod = "< Cold air/drinks";
        else if (lower.contains("warm") && (lower.contains("better") || lower.contains("amel"))) mod = "> Warmth & heat";
        else if (lower.contains("motion") && lower.contains("worse")) mod = "< Motion";
        else if (lower.contains("midnight")) mod = "< Midnight / 1-2 AM";

        String conc = "";
        if (lower.contains("thirst")) conc = "Thirst disturbance";
        if (lower.contains("sweat")) conc += (conc.isEmpty() ? "" : ", ") + "Cold perspiration";

        String mind = "";
        if (lower.contains("anxi") || lower.contains("fear")) mind = "Severe anxiety, fear of disease/death";
        if (lower.contains("restless")) mind += (mind.isEmpty() ? "" : ", ") + "Physical restlessness, pacing";

        return new ExtractedLsmc(loc, sens, mod, conc, mind);
    }

    /**
     * Synthesizes a differential diagnosis between the top 2 ranked remedies
     * from the active repertorization matrix.
     */
    public DifferentialAnalysis generateDifferential(RepertorizationResult result) {
        if (result == null || result.rankedRemedies().size() < 2) {
            return new DifferentialAnalysis("", "", "Insufficient remedy data for differential diagnosis", List.of());
        }

        var top1 = result.rankedRemedies().get(0);
        var top2 = result.rankedRemedies().get(1);

        List<String> keynotes = new ArrayList<>();
        keynotes.add(String.format("%s: Total score %.1f covering %d rubrics (%s kingdom, %s miasm)",
                top1.remedyName(), top1.totalScore(), top1.coveredSymptomsCount(), top1.kingdom(), top1.miasm()));
        keynotes.add(String.format("%s: Total score %.1f covering %d rubrics (%s kingdom, %s miasm)",
                top2.remedyName(), top2.totalScore(), top2.coveredSymptomsCount(), top2.kingdom(), top2.miasm()));

        if (top1.isContraindicated()) {
            keynotes.add("WARNING: " + top1.remedyName() + " has strong modality contraindications in polar opposite symptoms!");
        }

        String summary = String.format("Differential analysis between #1 %s and #2 %s. " +
                "Examine emotional keynotes and thermal reactions to confirm Simillimum.",
                top1.remedyName(), top2.remedyName());

        return new DifferentialAnalysis(top1.remedyId(), top2.remedyId(), summary, keynotes);
    }
}
