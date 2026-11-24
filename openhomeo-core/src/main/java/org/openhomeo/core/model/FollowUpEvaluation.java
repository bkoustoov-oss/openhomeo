package org.openhomeo.core.model;

import java.time.Instant;
import java.util.List;

/**
 * WinChip longitudinal consultation comparison and Kent's 12 Observations evaluation record.
 */
public record FollowUpEvaluation(
        String consultationId,
        String patientId,
        String previousConsultationId,
        int kentObservationNumber,       // 1 - 12
        String kentObservationTitle,
        String prognosisSummary,
        String recommendedAction,
        double heringsLawScore,          // 0.0 - 100.0%
        String heringsDirectionStatus,   // FULLY_ALIGNED, PARTIALLY_ALIGNED, SUPPRESSION_WARNING
        List<SymptomReaction> reactions,
        String clinicalEvaluationNotes,
        Instant evaluatedAt
) {
    public record SymptomReaction(
            String symptomText,
            ReactionType reactionType,
            String notes
    ) {}

    public enum ReactionType {
        AMELIORATED_MAJOR("++ Significant Amelioration (>75%)"),
        IMPROVED("+ Noticeable Improvement"),
        UNCHANGED("0 Static / No Change"),
        AGGRAVATED("- Aggravation / Intensified"),
        OLD_SYMPTOM_RETURNED("Old Fav - Historical Symptom Reappeared (Kent Obs #11)"),
        NEW_SYMPTOM_APPEARED("New - Foreign Symptom (Kent Obs #10 / Accessory)");

        private final String displayLabel;

        ReactionType(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }
    }
}
