package org.openhomeo.core.ai;

import org.openhomeo.core.repertory.RepertorizationResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic Clinical Case Synthesis & Differential Diagnosis Assistant (Zero External LLM).
 * Formulates structured Hahnemannian L-S-M-C + Mind + Physical Generals breakdowns
 * from patient narratives and doctor-guided serial interview statements.
 */
public class CaseSynthesisService {

    public record ExtractedLsmc(
            String location,
            String sensation,
            String modalities,
            String concomitants,
            String mentalKeynotes
    ) {}

    public record CaseIntakeAnalysis(
            String synthesizedChiefComplaint,
            ExtractedLsmc extractedLsmc,
            String physicalGeneralsSummary,
            List<String> capturedSignals,
            List<String> missingClarifyingAreas,
            String doctorGuidanceReply,
            EntryPointSuggestionEngine.EntryPointAnalysisResult entryPointRecommendation
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

        String lower = narrative.toLowerCase(Locale.ROOT);

        // 1. Location & Anatomical Site (exact word-boundary matching so 'ear' doesn't match 'fear')
        Set<String> locs = new LinkedHashSet<>();
        if (lower.contains("forehead") || lower.contains("headache") || containsWord(lower, "head") || lower.contains("temple") || lower.contains("occiput") || lower.contains("migraine")) {
            locs.add("Head / Forehead");
        } else if (containsWord(lower, "eye") || containsWord(lower, "eyes") || lower.contains("vision") || containsWord(lower, "throat") || lower.contains("tonsil") || containsWord(lower, "ear") || containsWord(lower, "ears")) {
            locs.add("Head-Neck / EENT");
        } else if (lower.contains("stomach") || lower.contains("gastric") || lower.contains("epigast") || lower.contains("acid") || lower.contains("bloat") || lower.contains("abdomen")) {
            locs.add("Stomach / Epigastrium");
        } else if (lower.contains("liver") || lower.contains("hepat") || lower.contains("jaundice") || lower.contains("gall")) {
            locs.add("Hepatobiliary Axis");
        } else if (lower.contains("chest") || lower.contains("cough") || lower.contains("breath") || lower.contains("asthma") || containsWord(lower, "heart") || lower.contains("palpitat")) {
            locs.add("Chest / Cardiorespiratory");
        } else if (lower.contains("joint") || lower.contains("knee") || containsWord(lower, "back") || lower.contains("sciatica") || lower.contains("stiff") || lower.contains("rheumat")) {
            locs.add("Musculoskeletal / Joints");
        } else if (containsWord(lower, "arm") || containsWord(lower, "hand") || lower.contains("skin") || lower.contains("eczema") || lower.contains("rash") || lower.contains("itch") || lower.contains("ulcer")) {
            locs.add("Skin & Extremities");
        }
        if (locs.isEmpty()) {
            if (containsWord(lower, "right") || lower.contains("right side") || lower.contains("right-sided")) {
                locs.add("Right-sided");
            } else if (containsWord(lower, "left") || lower.contains("left side") || lower.contains("left-sided")) {
                locs.add("Left-sided");
            }
        }

        // 2. Sensation Character
        Set<String> sens = new LinkedHashSet<>();
        if (lower.contains("burning") || lower.contains("hot needle")) sens.add("Burning like fire / hot coals");
        if (lower.contains("pulsat") || lower.contains("throbbing") || lower.contains("hammer")) sens.add("Pulsating / Throbbing");
        if (lower.contains("stitch") || lower.contains("splinter") || lower.contains("sharp")) sens.add("Stitching / splinter-like pain");
        if (lower.contains("cramp") || lower.contains("colic") || lower.contains("spasm") || lower.contains("twisting")) sens.add("Cramping / Spasmodic");
        if (lower.contains("bruis") || lower.contains("sore") || lower.contains("beaten")) sens.add("Bruised sore aching");
        if (lower.contains("numb") || lower.contains("tingl")) sens.add("Tingling / Numbness");
        if (lower.contains("constrict") || lower.contains("tight band")) sens.add("Constriction / band-like tightness");

        // 3. Modalities (< worse / > better)
        Set<String> mods = new LinkedHashSet<>();
        if (lower.contains("cold") && (lower.contains("worse") || lower.contains("agg") || lower.contains("< cold"))) mods.add("< Cold air/drinks");
        if (lower.contains("warm") && (lower.contains("better") || lower.contains("amel") || lower.contains("> warm"))) mods.add("> Warmth & heat");
        if (lower.contains("heat") && (lower.contains("worse") || lower.contains("agg")) || lower.contains("warm room") && lower.contains("worse")) mods.add("< Warmth / heated room");
        if (lower.contains("open air") && (lower.contains("better") || lower.contains("amel") || lower.contains("craves")) || lower.contains("cool air") && lower.contains("better")) mods.add("> Cool open air");
        if (lower.contains("continued motion") && (lower.contains("better") || lower.contains("amel")) || lower.contains("limbering")) mods.add("< Initial motion, > continued motion");
        else if (lower.contains("motion") && (lower.contains("worse") || lower.contains("agg") || lower.contains("any movement"))) mods.add("< Motion");
        if (lower.contains("pressure") && (lower.contains("better") || lower.contains("amel"))) mods.add("> Firm pressure");
        if (lower.contains("midnight") || lower.contains("1 am") || lower.contains("2 am") || lower.contains("1-2 am")) mods.add("< Midnight / 1-2 AM");
        if (lower.contains("4 pm") || lower.contains("8 pm") || lower.contains("4-8")) mods.add("< 4 to 8 PM");
        if (lower.contains("wet") || lower.contains("rain") || lower.contains("damp")) mods.add("< Damp / wet weather");
        if (lower.contains("sun") || lower.contains("sunstroke")) mods.add("< Direct sun / radiant heat");
        if (lower.contains("morning") && (lower.contains("worse") || lower.contains("waking"))) mods.add("< Morning on waking");

        // 4. Concomitants
        Set<String> conc = new LinkedHashSet<>();
        if (lower.contains("small sips") || lower.contains("little and often")) conc.add("Thirst for small frequent sips");
        else if (lower.contains("large draught") || lower.contains("quarts") || lower.contains("large quantities")) conc.add("Unquenchable thirst for large draughts");
        else if (lower.contains("thirstless") || lower.contains("no thirst") || lower.contains("without thirst")) conc.add("Marked thirstlessness");
        else if (lower.contains("thirst")) conc.add("Thirst disturbance");
        if (lower.contains("sweat") || lower.contains("perspir")) conc.add("Cold perspiration / sweats easily");
        if (lower.contains("nausea") || lower.contains("vomit")) conc.add("Accompanying nausea/vomiting");
        if (lower.contains("bitter taste") || lower.contains("flatulen") || lower.contains("bloat")) conc.add("Digestive bloating / bitter taste");

        // 5. Mental Generals
        Set<String> mind = new LinkedHashSet<>();
        if (lower.contains("anxi") || lower.contains("fear") || lower.contains("panic") || lower.contains("death")) mind.add("Severe anxiety, fear of disease/death");
        if (lower.contains("restless") || lower.contains("pacing") || lower.contains("toss")) mind.add("Physical restlessness, pacing");
        if (lower.contains("grief") || lower.contains("sorrow") || lower.contains("weep") || lower.contains("cries") || lower.contains("sad")) mind.add("Emotional sensitivity, grief / weeping");
        if (lower.contains("anger") || lower.contains("irritable") || lower.contains("indign") || lower.contains("snappish")) mind.add("Irritability / suppressed anger");
        if (lower.contains("consol") && lower.contains("worse")) mind.add("Worse from consolation, prefers solitude");
        if (lower.contains("company") && (lower.contains("better") || lower.contains("desire"))) mind.add("Desires company / reassurance");

        return new ExtractedLsmc(
                String.join("; ", locs),
                String.join("; ", sens),
                String.join("; ", mods),
                String.join(", ", conc),
                String.join(", ", mind)
        );
    }

    /**
     * Synthesizes physical generals (Thermals, Thirst, Desires/Aversions, Sleep) cleanly from narrative.
     */
    public String extractPhysicalGeneralsSummary(String narrative) {
        if (narrative == null || narrative.isBlank()) return "";
        String lower = narrative.toLowerCase(Locale.ROOT);
        List<String> generals = new ArrayList<>();

        if (lower.contains("chilly") || lower.contains("cold agg") || lower.contains("warm wraps") || lower.contains("cold sensitive")) {
            generals.add("Thermals: Chilly (sensitive to cold)");
        } else if (lower.contains("hot patient") || lower.contains("kick off covers") || lower.contains("warmth agg") || lower.contains("open air amel")) {
            generals.add("Thermals: Warm-blooded / Heat-intolerant");
        }

        if (lower.contains("thirstless") || lower.contains("no thirst") || lower.contains("without thirst")) {
            generals.add("Thirst: Thirstless");
        } else if (lower.contains("small sips") || lower.contains("little and often")) {
            generals.add("Thirst: Small sips frequently");
        } else if (lower.contains("large") && lower.contains("water") || lower.contains("very thirsty")) {
            generals.add("Thirst: Intense for large cold draughts");
        }

        if (lower.contains("crav") || lower.contains("desires") || lower.contains("avers")) {
            List<String> cravings = new ArrayList<>();
            if (lower.contains("salt")) cravings.add("desires salt");
            if (lower.contains("sweet") || lower.contains("sugar")) cravings.add("desires sweets");
            if (lower.contains("warm drink") || lower.contains("warm food")) cravings.add("desires warm food/drinks");
            if (lower.contains("fat") && (lower.contains("averse") || lower.contains("agg"))) cravings.add("averse/worse fats");
            if (!cravings.isEmpty()) generals.add("Diet/Cravings: " + String.join(", ", cravings));
        }

        if (lower.contains("insomnia") || lower.contains("sleepless") || lower.contains("sleep") || lower.contains("wakes")) {
            generals.add("Sleep: Sleep pattern disturbance noted");
        }
        if (lower.contains("wet") || lower.contains("grief") || lower.contains("injury") || lower.contains("sun") || lower.contains("anger")) {
            generals.add("Etiology/Trigger noted in statement");
        }

        return String.join(" | ", generals);
    }

    /**
     * Multi-turn Doctor-Guided Clinical Interview Analyzer (Strictly deterministic, zero external LLM).
     * Combines serial patient statements logged by the doctor/admin, auto-structures L-S-M-C & Generals,
     * highlights missing Hahnemannian clinical criteria, and runs EntryPointSuggestionEngine.
     */
    public CaseIntakeAnalysis analyzeSerialStatements(
            List<String> serialStatements,
            EntryPointSuggestionEngine suggestionEngine,
            List<String> matchedRubricPaths
    ) {
        if (serialStatements == null || serialStatements.isEmpty()) {
            return new CaseIntakeAnalysis(
                    "",
                    new ExtractedLsmc("", "", "", "", ""),
                    "",
                    List.of(),
                    List.of("Chief Complaint & Duration", "Location / Lateralization", "Modalities", "Thermals & Thirst", "Mental Generals"),
                    "Doctor, enter the patient's first statement below so I can structure the case and check classical modalities.",
                    null
            );
        }

        String combinedNarrative = String.join(" | ", serialStatements);
        ExtractedLsmc lsmc = extractLsmcFromNotes(combinedNarrative);
        String physicals = extractPhysicalGeneralsSummary(combinedNarrative);

        List<String> captured = new ArrayList<>();
        if (!lsmc.location().isBlank()) captured.add("Location: " + lsmc.location());
        if (!lsmc.sensation().isBlank()) captured.add("Sensation: " + lsmc.sensation());
        if (!lsmc.modalities().isBlank()) captured.add("Modalities: " + lsmc.modalities());
        if (!lsmc.concomitants().isBlank()) captured.add("Concomitants: " + lsmc.concomitants());
        if (!lsmc.mentalKeynotes().isBlank()) captured.add("Mind: " + lsmc.mentalKeynotes());
        if (!physicals.isBlank()) captured.add("Generals: " + physicals);

        List<String> missing = new ArrayList<>();
        if (lsmc.location().isBlank()) missing.add("Exact Anatomical Location / Side Affinity (Left vs Right)");
        if (lsmc.sensation().isBlank()) missing.add("Characteristic Sensation (burning, throbbing, stitching, cramping, sore)");
        if (lsmc.modalities().isBlank()) missing.add("Time, Motion & Weather Modalities (< worse / > better)");
        if (!physicals.contains("Thermals")) missing.add("Thermal Preference (Chilly vs Warm-blooded / Open air)");
        if (!physicals.contains("Thirst")) missing.add("Thirst Pattern (Thirstless vs Large drafts vs Small sips)");
        if (lsmc.mentalKeynotes().isBlank()) missing.add("Mental State / Emotional Etiology (Ailments From grief, fright, anger)");

        EntryPointSuggestionEngine.EntryPointAnalysisResult suggestion = suggestionEngine != null
                ? suggestionEngine.analyze(combinedNarrative, lsmc.location(), lsmc.sensation(),
                        lsmc.modalities(), lsmc.concomitants(), lsmc.mentalKeynotes(), physicals,
                        matchedRubricPaths != null ? matchedRubricPaths : List.of())
                : null;

        StringBuilder reply = new StringBuilder();
        reply.append(String.format("📋 Clinical Analysis (Synthesized across %d patient statement%s):\n",
                serialStatements.size(), serialStatements.size() == 1 ? "" : "s"));

        if (!captured.isEmpty()) {
            reply.append("\n✓ Structured Elements Captured:\n");
            for (String item : captured) {
                reply.append("  • ").append(item).append("\n");
            }
        }

        if (matchedRubricPaths != null && !matchedRubricPaths.isEmpty()) {
            reply.append("\n📚 Semantic Repertory Rubrics Matched:\n");
            for (String p : matchedRubricPaths) {
                reply.append("  • ").append(p).append("\n");
            }
        }

        if (!missing.isEmpty()) {
            reply.append("\n❓ Recommended Clarifying Questions (For Doctor to Ask Patient):\n");
            for (String q : missing) {
                reply.append("  → Ask about: ").append(q).append("\n");
            }
        } else {
            reply.append("\n✅ Full Hahnemannian Totality Picture Achieved (All Core Generals & L-S-M-C Present).\n");
        }

        if (suggestion != null && suggestion.primarySuggestion() != null) {
            var pri = suggestion.primarySuggestion();
            reply.append(String.format(
                    "\n⚡ Current Entry-Point Medicine Recommendation:\n  ★ %s (%s) — Potency/Scale: %s | Match Score: %.0f%%\n  Rationale: %s\n",
                    pri.remedyName(), pri.remedyId(), pri.suggestedPotencyScale(), pri.confidenceScore(),
                    String.join("; ", pri.clinicalReasons())
            ));
            if (!suggestion.alternativeSuggestions().isEmpty()) {
                reply.append("  • Differentials to compare: ");
                List<String> altNames = new ArrayList<>();
                for (var alt : suggestion.alternativeSuggestions()) {
                    altNames.add(alt.remedyName() + " (" + String.format("%.0f%%", alt.confidenceScore()) + ")");
                }
                reply.append(String.join(", ", altNames)).append("\n");
            }
        }
        reply.append("\n⚕️ Physician Guidance Note: Review auto-filled fields and accept or supersede based on clinical judgment.");

        return new CaseIntakeAnalysis(
                combinedNarrative,
                lsmc,
                physicals,
                captured,
                missing,
                reply.toString(),
                suggestion
        );
    }

    private static boolean containsWord(String text, String word) {
        return text.matches(".*\\b" + word + "\\b.*");
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
