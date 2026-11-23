package org.openhomeo.core.ai;

import org.openhomeo.core.model.Remedy;

import java.util.*;

/**
 * Classical Homeopathic Entry-Point Suggestion Engine.
 * Evaluates patient symptoms against fundamental clinical entry points:
 * 1. Etiology & Ailments From (Causa Occasionalis)
 * 2. Thermals & General Modalities (< Cold / < Heat)
 * 3. Thirst Dynamics (Thirstless / Large Drafts / Small Sips)
 * 4. Speed of Onset (Sudden Violent vs. Gradual Insidious)
 * 5. Lateralization / Side Affinity (Right vs. Left vs. Diagonal)
 * 6. Predominant Mental Generals & Disposition
 */
public class EntryPointSuggestionEngine {

    public record SuggestedRemedy(
            String remedyId,
            String remedyName,
            double confidenceScore,
            String primaryEntryPoint,
            List<String> clinicalReasons,
            String suggestedPotencyScale
    ) {}

    public record EntryPointAnalysisResult(
            SuggestedRemedy primarySuggestion,
            List<SuggestedRemedy> alternativeSuggestions,
            List<String> detectedEntryPoints,
            String clinicalRationaleSummary
    ) {}

    public EntryPointAnalysisResult analyze(
            String chiefComplaint,
            String location,
            String sensation,
            String modalities,
            String concomitants,
            String mind,
            String physicalGenerals,
            List<String> rubricTexts
    ) {
        String combined = ((chiefComplaint != null ? chiefComplaint : "") + " " +
                (location != null ? location : "") + " " +
                (sensation != null ? sensation : "") + " " +
                (modalities != null ? modalities : "") + " " +
                (concomitants != null ? concomitants : "") + " " +
                (mind != null ? mind : "") + " " +
                (physicalGenerals != null ? physicalGenerals : "") + " " +
                (rubricTexts != null ? String.join(" ", rubricTexts) : "")
        ).toLowerCase(Locale.ROOT);

        List<String> detectedPoints = new ArrayList<>();
        Map<String, RemedyScore> scores = new HashMap<>();

        // Flexible scorer
        interface ScoreFn {
            void score(String code, String reason, double pts);
        }
        ScoreFn addScore = (code, reason, pts) -> {
            scores.computeIfAbsent(code, k -> new RemedyScore(code)).addReason(reason, pts);
        };

        // 1. ETIOLOGY / AILMENTS FROM (Highest clinical grade: 40 pts)
        if (combined.contains("wet") || combined.contains("rain") || combined.contains("damp") || combined.contains("bathing")) {
            detectedPoints.add("Etiology: Ailments from getting wet / damp weather");
            addScore.score("rhus-t", "Premier remedy for ailments from getting wet while perspiring", 45.0);
            addScore.score("dulc", "Aggravation from sudden change from dry to damp cold weather", 25.0);
            addScore.score("nat-s", "Hydrogenoid constitution worse living near water / damp basements", 25.0);
        }
        if (combined.contains("injury") || combined.contains("trauma") || combined.contains("bruise") || combined.contains("fall") || combined.contains("sprain")) {
            detectedPoints.add("Etiology: Mechanical trauma / Physical shock");
            addScore.score("arn", "Trauma of soft tissues, extravasation, bruised soreness", 45.0);
            addScore.score("hyper", "Nerve-rich puncture wounds, hyper-sensitiveness", 25.0);
            addScore.score("ruta", "Periosteal and tendon injuries, bruised stiffness", 20.0);
            addScore.score("symph", "Specific for bone fracture knitting and blunt eye injuries", 20.0);
        }
        if (combined.contains("hard") || combined.contains("bed feels") || combined.contains("says nothing is wrong")) {
            addScore.score("arn", "Bed feels too hard; claims to be healthy when dangerously ill", 30.0);
        }
        if (combined.contains("grief") || combined.contains("sorrow") || combined.contains("bereavement") || combined.contains("disappointment")) {
            detectedPoints.add("Etiology: Silent grief and emotional shock");
            addScore.score("ign", "Acute grief, sighing, rapid paradoxical emotional swings", 40.0);
            addScore.score("nat-m", "Chronic inveterate grief, brooding in isolation, worse consolation", 35.0);
            addScore.score("ph-ac", "Emotional apathy, indifferent to loved ones from sorrow", 30.0);
        }
        if (combined.contains("fright") || combined.contains("fear") || combined.contains("terror") || combined.contains("panic")) {
            detectedPoints.add("Etiology: Sudden fright, panic and fear of death");
            addScore.score("acon", "Sudden storm-like acute panic, intense fear of death with restlessness", 40.0);
            addScore.score("gels", "Paralyzing fright, motor trembling, anticipation of ordeals", 30.0);
            addScore.score("op", "Stupor following severe fright, retained trauma", 30.0);
        }
        if (combined.contains("anger") || combined.contains("mortification") || combined.contains("indignation")) {
            detectedPoints.add("Etiology: Suppressed anger and indignation");
            addScore.score("staph", "Ill-effects of unexpressed anger, trembling with indignation", 40.0);
            addScore.score("coloc", "Violent abdominal colic from suppressed rage, bent double", 30.0);
            addScore.score("cham", "Unbearable anger and pain, uncivil, demands to be carried", 30.0);
        }
        if (combined.contains("sun") || combined.contains("sunstroke") || combined.contains("heat of sun")) {
            detectedPoints.add("Etiology: Exposure to direct sun and overheated air");
            addScore.score("glon", "Violent throbbing congestive sun-headache, carotid pulsations", 40.0);
            addScore.score("bell", "Cerebral hyperaemia, flushed face, hot dry skin from sun", 30.0);
            addScore.score("nat-m", "Periodical midday headache brought on by summer sun", 25.0);
        }

        // 2. THERMAL DYNAMICS (10 pts)
        if (combined.contains("chilly") || combined.contains("cold agg") || combined.contains("< cold") || combined.contains("warmth amel") || combined.contains("warm room amel")) {
            detectedPoints.add("Thermals: Marked Chilly / Aggravated by Cold");
            addScore.score("ars", "Chilly, wrapped warmly, burning pains relieved by heat", 12.0);
            addScore.score("sil", "Extremely chilly, hugs fire, worse draught of air", 12.0);
            addScore.score("hepar", "Extreme sensitiveness to slightest cold air or uncovering", 12.0);
            addScore.score("calc", "Cold damp feet, chilly, sour perspiration on head", 10.0);
            addScore.score("nux-v", "Chilly, must be covered in all stages of fever", 10.0);
        }
        if (combined.contains("hot") || combined.contains("warmth agg") || combined.contains("< warm") || combined.contains("open air amel") || combined.contains("uncovering amel")) {
            detectedPoints.add("Thermals: Marked Hot Patient / Aggravated by Heat");
            addScore.score("sulph", "Heat in vertex, burning palms and soles, kicks off blankets", 15.0);
            addScore.score("puls", "Craves cool fresh air, intolerant of warm stuffy room", 15.0);
            addScore.score("apis", "Severe heat aggravation, thirstless, stinging edema", 15.0);
            addScore.score("nat-m", "Worse warmth, sea-bathing, warm summer sun", 10.0);
            addScore.score("iod", "Ravenous appetite with emaciation, extreme heat aggravation", 10.0);
        }

        // 3. THIRST DYNAMICS (15 pts)
        if (combined.contains("thirstless") || combined.contains("no thirst") || combined.contains("without thirst")) {
            detectedPoints.add("Thirst: Complete Thirstlessness");
            addScore.score("apis", "Thirstlessness in acute dropsical conditions and fever", 20.0);
            addScore.score("puls", "Dry mouth without thirst, aversion to fatty food", 20.0);
            addScore.score("gels", "Dull, drowsy, dizzy with complete absence of thirst", 15.0);
        }
        if (combined.contains("large quantities") || combined.contains("quarts") || combined.contains("deep long drinks")) {
            detectedPoints.add("Thirst: Great thirst for large draughts at long intervals");
            addScore.score("bry", "Excessive dryness of mucous membranes with thirst for large draughts", 25.0);
            addScore.score("syzyg-j", "Diabetic polydipsia, drinks quarts of water constantly", 35.0);
            addScore.score("acon", "Burning unquenchable thirst for cold water in acute fever", 20.0);
        }
        if (combined.contains("small sips") || combined.contains("little and often") || combined.contains("frequent sips")) {
            detectedPoints.add("Thirst: Small sips of cold water at frequent intervals");
            addScore.score("ars", "Drinks little and often; stomach intolerant of cold water", 20.0);
            addScore.score("chin", "Thirst during chill, sips water to moisten dry tongue", 15.0);
        }

        // 4. MOTION MODALITIES
        if (combined.contains("motion agg") || combined.contains("< motion") || combined.contains("slightest motion")) {
            detectedPoints.add("Modality: Severe Aggravation from Motion (< Motion)");
            addScore.score("bry", "Absolute aggravation from motion; relief from absolute rest and pressure", 25.0);
            addScore.score("bell", "Cannot bear jar of bed or motion of room", 15.0);
        }
        if (combined.contains("continued motion") || combined.contains("limbering up") || (combined.contains("< initial motion") && combined.contains("> continued motion"))) {
            detectedPoints.add("Modality: Relief from Continued Motion / Limbering Up");
            addScore.score("rhus-t", "Stiffness worse initial motion, relieved by limbering up and continued motion", 35.0);
        }
        if (combined.contains("restless pacing") || combined.contains("midnight restless")) {
            addScore.score("ars", "Prostration with intense mental and physical restlessness, tosses about", 20.0);
        }

        // 5. LATERALIZATION / ORGAN AFFINITY
        if (combined.contains("right") || combined.contains("right side") || combined.contains("liver") || combined.contains("hepat")) {
            detectedPoints.add("Affinity: Right-Sided / Hepatobiliary Axis");
            addScore.score("lyc", "Right-to-left progression, 4-8 PM aggravation, liver sluggishness", 20.0);
            addScore.score("andro-p", "Kalmegh (HPI) - Sluggish liver, jaundice, bilious bitter taste", 30.0);
            addScore.score("chel", "Pain under right inferior angle of scapula, liver tenderness", 25.0);
        }
        if (combined.contains("left") || combined.contains("left side") || combined.contains("heart") || combined.contains("cardiac")) {
            detectedPoints.add("Affinity: Left-Sided / Cardiovascular Axis");
            addScore.score("lach", "Left-sided, cannot bear constriction around throat or waist", 25.0);
            addScore.score("term-a", "Arjuna (HPI) - Cardiovascular organic and functional cardiac tonic", 35.0);
            addScore.score("spig", "Violent left-sided neuralgias and cardiac palpitations", 20.0);
        }

        // 6. SPECIAL INDIAN CONDITIONS
        if (combined.contains("sugar") || combined.contains("diabetes") || combined.contains("polyuria")) {
            detectedPoints.add("Clinical Focus: Glycosuria / Diabetes Mellitus");
            addScore.score("syzyg-j", "Premier Indian remedy for prompt reduction of urinary sugar", 45.0);
            addScore.score("gymn-s", "Gurmar (HPI) - Destroys sweet craving and normalizes metabolic tone", 30.0);
        }
        if (combined.contains("blood pressure") || combined.contains("hypertension") || combined.contains("insomnia")) {
            detectedPoints.add("Clinical Focus: Hypertension with Neuropsychiatric Insomnia");
            addScore.score("rauw-s", "Sarpagandha (HPI) - Lowers systemic vascular resistance, restores sleep", 45.0);
        }
        if (combined.contains("skin") || combined.contains("eczema") || combined.contains("boil") || combined.contains("blood purify")) {
            detectedPoints.add("Clinical Focus: Cutaneous Eruptions & Blood Dyscrasia");
            addScore.score("azad-i", "Neem (HPI) - Invaluable alterative blood purifier and anti-scabious", 35.0);
            addScore.score("sulph", "Chronic psoric eruptions, pruritus worse heat of bed", 20.0);
        }

        // If no specific points matched, provide fallback polychrest suggestions
        if (scores.isEmpty()) {
            addScore.score("sulph", "Constitutional Psora clearing remedy", 15.0);
            addScore.score("puls", "Mild, gentle emotional state", 15.0);
            addScore.score("nux-v", "Sedentary lifestyle and gastric over-sensitiveness", 15.0);
            detectedPoints.add("Default: Constitutional Survey");
        }

        // Sort remedies by score
        List<RemedyScore> sorted = new ArrayList<>(scores.values());
        sorted.sort((a, b) -> Double.compare(b.score, a.score));

        List<SuggestedRemedy> suggestions = new ArrayList<>();
        for (RemedyScore sc : sorted) {
            String fullName = resolveRemedyName(sc.code);
            String potency = resolveSuggestedPotency(sc.code, sc.reasons);
            suggestions.add(new SuggestedRemedy(
                    sc.code,
                    fullName,
                    Math.min(98.0, 50.0 + sc.score),
                    sc.reasons.isEmpty() ? "Constitutional Totality" : sc.reasons.get(0),
                    sc.reasons,
                    potency
            ));
        }

        SuggestedRemedy primary = suggestions.get(0);
        List<SuggestedRemedy> alternatives = suggestions.size() > 1 ? suggestions.subList(1, Math.min(suggestions.size(), 6)) : List.of();

        String summary = "Primary clinical entry point suggests " + primary.remedyName() + " (" + primary.remedyId() +
                ") based on " + String.join("; ", detectedPoints) + ".";

        return new EntryPointAnalysisResult(primary, alternatives, detectedPoints, summary);
    }

    private static String resolveRemedyName(String code) {
        return switch (code) {
            case "acon" -> "Aconitum napellus";
            case "arn" -> "Arnica montana";
            case "ars" -> "Arsenicum album";
            case "bell" -> "Belladonna";
            case "bry" -> "Bryonia alba";
            case "calc" -> "Calcarea carbonica";
            case "dulc" -> "Dulcamara";
            case "gels" -> "Gelsemium sempervirens";
            case "glon" -> "Glonoinum";
            case "hepar" -> "Hepar sulphuris";
            case "hyper" -> "Hypericum perforatum";
            case "ign" -> "Ignatia amara";
            case "lach" -> "Lachesis muta";
            case "lyc" -> "Lycopodium clavatum";
            case "nat-m" -> "Natrum muriaticum";
            case "nat-s" -> "Natrum sulphuricum";
            case "nux-v" -> "Nux vomica";
            case "phos" -> "Phosphorus";
            case "puls" -> "Pulsatilla nigricans";
            case "rhus-t" -> "Rhus toxicodendron";
            case "ruta" -> "Ruta graveolens";
            case "sil" -> "Silicea";
            case "staph" -> "Staphysagria";
            case "sulph" -> "Sulphur";
            case "symph" -> "Symphytum officinale";
            case "azad-i" -> "Azadirachta indica (Neem - HPI)";
            case "ocim-s" -> "Ocimum sanctum (Tulsi - HPI)";
            case "term-a" -> "Terminalia arjuna (Arjuna - HPI)";
            case "syzyg-j" -> "Syzygium jambolanum (Jamun - HPI)";
            case "andro-p" -> "Andrographis paniculata (Kalmegh - HPI)";
            case "rauw-s" -> "Rauwolfia serpentina (Sarpagandha - HPI)";
            case "gymn-s" -> "Gymnema sylvestre (Gurmar - HPI)";
            default -> code.toUpperCase();
        };
    }

    private static String resolveSuggestedPotency(String code, List<String> reasons) {
        if (code.equals("term-a") || code.equals("syzyg-j") || code.equals("andro-p") || code.equals("rauw-s") || code.equals("azad-i") || code.equals("ocim-s")) {
            return "Q (Mother Tincture) 10-15 drops in 1/4 cup water TID";
        }
        if (code.equals("acon") || code.equals("bell") || code.equals("arn") || code.equals("rhus-t")) {
            return "200CH, 4 globules TDS or every 4 hours";
        }
        return "30CH or 200CH single dose or TDS";
    }

    private static class RemedyScore {
        final String code;
        double score = 0;
        final List<String> reasons = new ArrayList<>();

        RemedyScore(String code) {
            this.code = code;
        }

        void addReason(String reason, double pts) {
            this.score += pts;
            this.reasons.add(reason);
        }
    }
}
