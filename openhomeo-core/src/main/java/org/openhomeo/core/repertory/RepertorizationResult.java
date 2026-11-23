package org.openhomeo.core.repertory;

import java.util.List;
import java.util.Map;

/**
 * Result of a repertorization calculation containing ranked remedies
 * and their detailed symptom-grade matrix.
 */
public record RepertorizationResult(
        List<RemedyScore> rankedRemedies,
        int totalRubricsEvaluated,
        boolean eliminativeFilterApplied,
        boolean polarityAnalysisActive
) {
    public record RemedyScore(
            String remedyId,
            String remedyName,
            String remedyAbbr,
            int coveredSymptomsCount,
            double totalScore,
            int polarityDifference,      // Delta P = Positive sum - Contraindicated sum
            int contraindicationCount,   // Number of polar opposite rubrics where remedy appears
            boolean isContraindicated,   // True if strongly contraindicated in polar opposite modality
            String kingdom,
            String family,
            String miasm,
            Map<String, Integer> rubricGrades // rubricId -> grade (1 to 4)
    ) {}
}
