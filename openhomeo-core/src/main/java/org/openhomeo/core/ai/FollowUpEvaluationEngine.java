package org.openhomeo.core.ai;

import org.openhomeo.core.model.FollowUpEvaluation;
import org.openhomeo.core.model.FollowUpEvaluation.ReactionType;
import org.openhomeo.core.model.FollowUpEvaluation.SymptomReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Deterministic Clinical Evaluation Engine implementing:
 * 1. J. T. Kent's 12 Prognostic Observations (Lectures on Homoeopathic Philosophy)
 * 2. Constantine Hering's Law of Directional Cure
 * 3. Longitudinal Consultation Follow-up Reaction Scoring
 */
public class FollowUpEvaluationEngine {
    private static final Logger log = LoggerFactory.getLogger(FollowUpEvaluationEngine.class);

    public record EvaluationOutcome(
            int kentObservationNumber,
            String kentObservationTitle,
            String prognosisSummary,
            String recommendedAction,
            double heringsLawScore,
            String heringsDirectionStatus,
            String clinicalRationale
    ) {}

    /**
     * Evaluates symptom reactions between Visit N and Visit N-1.
     */
    public EvaluationOutcome evaluateFollowUp(
            List<SymptomReaction> reactions,
            boolean hasMentalAmelioration,
            boolean hasPhysicalSkinDischarge,
            boolean hasWrongDirectionSuppression,
            int daysSincePreviousDose
    ) {
        int countAmeliorated = 0;
        int countAggravated = 0;
        int countOldReturned = 0;
        int countNewAppeared = 0;
        int countUnchanged = 0;

        for (SymptomReaction r : reactions) {
            if (r.reactionType() == ReactionType.AMELIORATED_MAJOR || r.reactionType() == ReactionType.IMPROVED) {
                countAmeliorated++;
            } else if (r.reactionType() == ReactionType.AGGRAVATED) {
                countAggravated++;
            } else if (r.reactionType() == ReactionType.OLD_SYMPTOM_RETURNED) {
                countOldReturned++;
            } else if (r.reactionType() == ReactionType.NEW_SYMPTOM_APPEARED) {
                countNewAppeared++;
            } else {
                countUnchanged++;
            }
        }

        // 1. Check for Kent's 12th Observation: Symptoms Take a Wrong Direction (Suppression)
        if (hasWrongDirectionSuppression) {
            return new EvaluationOutcome(
                    12,
                    "Kent's 12th Observation: Symptoms Take a Wrong Direction (Suppression)",
                    "CRITICAL WARNING: The disease has been driven inwards from less vital to more vital organs (e.g. skin eruption disappeared, asthma/depression appeared).",
                    "IMMEDIATE ACTION: Cease current remedy. Do NOT repeat. Antidote the suppression or prescribe an intercurrent anti-miasmatic to restore the external manifestation.",
                    15.0,
                    "SUPPRESSION_WARNING",
                    "Hering's Law is violently inverted. Disappearance of superficial peripheral symptoms accompanied by visceral or psychic deterioration proves dangerous suppression."
            );
        }

        // 2. Check for Kent's 11th Observation: When Old Symptoms Return
        if (countOldReturned > 0) {
            return new EvaluationOutcome(
                    11,
                    "Kent's 11th Observation: When Old Symptoms Return",
                    "EXCELLENT PROGNOSIS: Disease is retreating in exact reverse order of appearance, validating Constantine Hering's Law of Cure.",
                    "GOLDEN CANON: STRICTLY DO NOT INTERVENE! Do NOT change the remedy and do NOT repeat the dose while old symptoms are active. Wait and watch.",
                    100.0,
                    "FULLY_ALIGNED",
                    "The reappearance of previously suppressed or forgotten symptoms confirms that the vital force is discharging the chronic miasm from within outwards."
            );
        }

        // 3. Check for Kent's 10th Observation: New Symptoms Appearing After Dose
        if (countNewAppeared > countAmeliorated && countNewAppeared >= 2) {
            return new EvaluationOutcome(
                    10,
                    "Kent's 10th Observation: New Symptoms Appearing After Remedy",
                    "UNFAVORABLE PROGNOSIS: The prescription was non-homeopathic or an uncurative dissimilar remedy. The patient is producing medicinal drug provings.",
                    "ACTION: Wait until new drug symptoms subside, or antidote if severe. Re-take the case thoroughly from the foundation without prejudice.",
                    25.0,
                    "NON_ALIGNED",
                    "New, foreign symptoms appearing after a prescription indicate that the remedy did not match the center of the vital disorder."
            );
        }

        // 4. Check for Kent's 7th Observation: Full Amelioration of Symptoms Without Relief of Patient
        if (countAmeliorated > 0 && !hasMentalAmelioration && (countAggravated > 0 || hasPhysicalSkinDischarge == false)) {
            // If particulars improved but patient feels depressed / worse mentally
            if (!hasMentalAmelioration && countAggravated > 0) {
                return new EvaluationOutcome(
                        7,
                        "Kent's 7th Observation: Full Amelioration of Symptoms But No Relief of the Patient",
                        "GUARDED PROGNOSIS: Superficial organ improvement while mental/vital energy worsens indicates deep structural pathology with low curative reserve.",
                        "ACTION: Avoid high centesimals. Re-evaluate using 50-Millesimal (LM) potencies or gentle drainage remedies.",
                        40.0,
                        "PARTIALLY_ALIGNED",
                        "Kent warns that when a patient's physical symptoms disappear yet the mind loses cheerfulness and stamina drops, cure is not occurring."
                );
            }
        }

        // 5. Check for Kent's 3rd Observation: Short, Mild Aggravation Followed by Rapid Long-Lasting Improvement
        if (countAggravated > 0 && countAmeliorated > countAggravated && daysSincePreviousDose <= 14) {
            return new EvaluationOutcome(
                    3,
                    "Kent's 3rd Observation: Short Mild Aggravation Followed by Rapid Cure",
                    "IDEAL PROGNOSIS: The simillimum matched the dynamic vitality with precision. A brief homeopathic aggravation (§280) sparked rapid restorative reaction.",
                    "ACTION: Strictly wait! Allow the curative wave to exhaust itself completely before considering any repetition.",
                    95.0,
                    "FULLY_ALIGNED",
                    "The vital force was slightly heightened by the simillimum and promptly initiated lasting constitutional recovery."
            );
        }

        // 6. Check for Kent's 4th Observation: Rapid Cure Without Any Aggravation
        if (countAmeliorated > 0 && countAggravated == 0 && countNewAppeared == 0) {
            return new EvaluationOutcome(
                    4,
                    "Kent's 4th Observation: Rapid Recovery Without Any Aggravation",
                    "PERFECT PROGNOSIS: Highest degree of remedy suitability and exact potency match. True constitutional simillimum.",
                    "ACTION: Placebo (Sac Lac) administration only. Do NOT repeat as long as improvement continues unhindered.",
                    98.0,
                    "FULLY_ALIGNED",
                    "No organic destruction existed; the remedy was so finely dynamized that vital balance was restored without functional disturbance."
            );
        }

        // 7. Check for Kent's 1st Observation: Prolonged Aggravation, Then Slow Decline
        if (countAggravated > countAmeliorated && daysSincePreviousDose > 21) {
            return new EvaluationOutcome(
                    1,
                    "Kent's 1st Observation: Prolonged Aggravation Then Slow Decline",
                    "GRAVE PROGNOSIS: Structural tissue destruction or advanced irreversible disease where remedy was given in too high a potency or repeated too deeply.",
                    "ACTION: Antidote if possible. Switch to low organopathic tinctures or palliative LM1 water doses. Never give high centesimals.",
                    20.0,
                    "POOR_RESERVE",
                    "The vitality was insufficient to mount a curative reaction, causing vital exhaustion under deep dynamic stimulation."
            );
        }

        // 8. Default: Observation 5 (Partial amelioration / Needs closer observation)
        return new EvaluationOutcome(
                5,
                "Kent's 5th Observation: Partial Amelioration / Transitional State",
                "STABLE PROGNOSIS: Moderate improvement with static residual complaints. Remedy acted partially.",
                "ACTION: If improvement ceases and symptom totality remains unchanged, repeat with succussed water dose (§248) or ascend potency (e.g. 30C to 200C).",
                70.0,
                "PARTIALLY_ALIGNED",
                "General progression is favorable but case requires careful watching to confirm whether complementary remedies will be required."
        );
    }
}
