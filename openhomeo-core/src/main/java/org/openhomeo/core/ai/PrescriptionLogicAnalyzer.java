package org.openhomeo.core.ai;

import org.openhomeo.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic Classical Homeopathic Clinical AI & Prescription Reasoner.
 * Evaluates prescriptions against classical canons:
 * - J. T. Kent's Posology & Repetition Principles (Lectures on Homoeopathic Philosophy)
 * - Hahnemann's Organon of Medicine (5th & 6th Editions, §246-§248, §275-§283)
 * - Gibson Miller & Hering Relationship of Remedies (Inimical & Incompatible Sequences)
 * - Miasmatic Suppression & Drainage Principles
 *
 * Dynamically interrogates prescribing doctors when deviations are detected,
 * analyzes clinical explanations for future complications, and generates
 * strictly confidential "Report of Faith" dossier entries for the Administrator.
 */
public class PrescriptionLogicAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(PrescriptionLogicAnalyzer.class);

    // Well-documented classical Gibson Miller / Hering Inimical Pairs (Never follow each other)
    private static final Map<String, Set<String>> INIMICAL_PAIRS = new HashMap<>();

    static {
        registerInimical("apis", "rhus-t");
        registerInimical("rhus-t", "apis");
        registerInimical("caust", "phos");
        registerInimical("phos", "caust");
        registerInimical("sil", "merc");
        registerInimical("merc", "sil");
        registerInimical("chin", "dig");
        registerInimical("dig", "chin");
        registerInimical("ign", "coff");
        registerInimical("coff", "ign");
        registerInimical("lach", "dulc");
        registerInimical("dulc", "lach");
        registerInimical("sep", "bry");
        registerInimical("bry", "sep");
    }

    private static void registerInimical(String remA, String remB) {
        INIMICAL_PAIRS.computeIfAbsent(remA.toLowerCase(), k -> new HashSet<>()).add(remB.toLowerCase());
    }

    public static boolean isInimical(String remA, String remB) {
        if (remA == null || remB == null) return false;
        Set<String> set = INIMICAL_PAIRS.get(remA.toLowerCase());
        return set != null && set.contains(remB.toLowerCase());
    }

    public static Set<String> getInimicals(String remId) {
        if (remId == null) return Collections.emptySet();
        return INIMICAL_PAIRS.getOrDefault(remId.toLowerCase(), Collections.emptySet());
    }

    public static Map<String, Set<String>> getAllInimicalPairs() {
        return Collections.unmodifiableMap(INIMICAL_PAIRS);
    }

    public record RuleCheck(
            String ruleCode,
            String ruleTitle,
            AdminFaithDossierEntry.RuleSeverity severity,
            String canonCitation,
            String discrepancyDetail,
            String clinicalQuestion,
            String potentialComplicationsHint
    ) {}

    public record AnalysisResult(
            boolean isCanonCompliant,
            boolean requiresDoctorInterrogation,
            List<RuleCheck> ruleChecks
    ) {}

    public record ExplanationEvaluation(
            boolean isClinicallySound,
            String riskGrade, // LOW, MODERATE, HIGH, CRITICAL
            String predictedComplications,
            String clinicalRationaleSummary,
            boolean shouldLogToFaithDossier
    ) {}

    /**
     * Evaluates a prescription against classical posological and relational laws.
     */
    public AnalysisResult analyzePrescription(
            String remedyId,
            String remedyName,
            String potency,
            Prescription.PotencyScale scale,
            String frequency,
            List<Prescription> patientPrescriptionHistory,
            String chiefComplaint,
            String clinicalNotes
    ) {
        List<RuleCheck> checks = new ArrayList<>();
        String remIdClean = remedyId != null ? remedyId.trim().toLowerCase() : "";
        String potClean = potency != null ? potency.trim().toUpperCase() : "";
        String freqClean = frequency != null ? frequency.trim().toUpperCase() : "";

        boolean isDecimal = scale == Prescription.PotencyScale.DECIMAL || potClean.endsWith("X") || potClean.endsWith("D");
        boolean isLm = scale == Prescription.PotencyScale.FIFTY_MILLESIMAL || potClean.startsWith("LM") || potClean.startsWith("0/") || potClean.startsWith("Q");
        boolean isMotherTincture = potClean.contains("Q") || potClean.contains("Ø") || potClean.contains("MT");

        // Low decimal salts & mother tinctures are physiological/biochemic and exempted from Kentian dry repetition restriction
        boolean isLowPotencyBiochemic = isDecimal || isMotherTincture;

        // 1. Kentian 30C Repetition Check
        if (!isLowPotencyBiochemic && !isLm && (potClean.equals("30") || potClean.startsWith("30C") || potClean.startsWith("30 CH"))) {
            if (isFrequentRepetition(freqClean, 3)) {
                checks.add(new RuleCheck(
                        "KENT_30C_OVERREPETITION",
                        "Kentian Posology Violation: 30C Potency Repetition Limit",
                        AdminFaithDossierEntry.RuleSeverity.WARNING,
                        "Dr. J. T. Kent (Lectures on Homoeopathic Philosophy, Lec. XXXV): 'In acute illnesses, a 30th potency is rarely if ever to be given more than one or two times before awaiting the reaction of the vital force. Repeating 30C three or more times daily clouds the picture and produces an artificial drug proving.'",
                        "Prescribed 30C frequency is '" + frequency + "' (3 or more doses/day). Kent and classical prescribers dictate at most 2 doses before observation.",
                        "Why have you prescribed 3 doses of 30C for this patient rather than pausing after 1 to 2 doses to evaluate the vital reaction?",
                        "Medicinal aggravation, drug proving of remedy symptoms, suppression of peripheral manifestation into internal miasmatic layer."
                ));
            }
        }

        // 2. High Centesimal Potency Repetition Check (200C, 1M, 10M, 50M, CM)
        if (!isLowPotencyBiochemic && !isLm && isHighCentesimalPotency(potClean)) {
            if (isFrequentRepetition(freqClean, 2)) {
                checks.add(new RuleCheck(
                        "HIGH_POTENCY_REPETITION_HAZARD",
                        "Classical Dynamic Hazard: Excessive High Potency Repetition",
                        AdminFaithDossierEntry.RuleSeverity.VIOLATION,
                        "Hahnemann's Organon §246 and Kent's Minor Writings: High centesimal dynamizations (200C, 1M, 10M) are long-acting constitutional engines. Daily or multiple daily repetitions in dry globules cause severe vital exhaustion and violent homoeopathic aggravations.",
                        "High potency '" + potency + "' prescribed with repetitive schedule: '" + frequency + "'.",
                        "Centessimal high potencies (200C, 1M, 10M) act deeply for weeks. What is the clinical rationale for repeated doses without intermediate plussing or waiting?",
                        "Severe homeopathic aggravation, permanent vital disruption, precipitate structural tissue breakdown."
                ));
            }
        }

        // 3. Potency Escalation Jump Check (e.g. 30C straight to 10M/CM)
        if (patientPrescriptionHistory != null && !patientPrescriptionHistory.isEmpty()) {
            for (Prescription past : patientPrescriptionHistory) {
                if (past.remedyId().equalsIgnoreCase(remIdClean)) {
                    String pastPot = past.potency().toUpperCase();
                    if ((pastPot.contains("30") || pastPot.equals("30C")) &&
                        (potClean.contains("10M") || potClean.contains("50M") || potClean.contains("CM"))) {
                        checks.add(new RuleCheck(
                                "POTENCY_ESCALATION_JUMP",
                                "Improper Potency Progression: Skipping Intermediate Dynamizations",
                                AdminFaithDossierEntry.RuleSeverity.WARNING,
                                "Kentian Potency Series Rule: Dynamization must proceed by orderly progression (30 -> 200 -> 1M -> 10M -> 50M -> CM). Bypassing 200C and 1M creates an unbridgeable dynamic shock to the vital plane.",
                                "Patient previously received " + past.potency() + ". Current prescription leaps directly to " + potency + " without intermediate 200C / 1M step.",
                                "You are elevating the potency from 30C directly to 10M/CM. Why are intermediate potencies (200C, 1M) bypassed in this case?",
                                "Dangerous violent aggravation, hypersensitive shock, therapeutic dead-end."
                        ));
                    }
                    break;
                }
            }
        }

        // 4. Gibson Miller & Hering Inimical Sequence Check
        if (patientPrescriptionHistory != null && !patientPrescriptionHistory.isEmpty()) {
            Prescription lastRx = patientPrescriptionHistory.get(0);
            String lastRemId = lastRx.remedyId().toLowerCase();
            Set<String> inimicals = INIMICAL_PAIRS.get(lastRemId);
            if (inimicals != null && inimicals.contains(remIdClean)) {
                checks.add(new RuleCheck(
                        "GIBSON_MILLER_INIMICAL_SEQUENCE",
                        "Gibson Miller Classical Inimical Sequence Violation",
                        AdminFaithDossierEntry.RuleSeverity.CRITICAL_HAZARD,
                        "Gibson Miller Relationship of Remedies & C. Hering: '" + lastRx.remedyName() + "' and '" + remedyName + "' are strictly INIMICAL. Administering them in immediate succession disrupts vital harmony and excites severe cross-aggravations.",
                        "Patient recently received inimical remedy '" + lastRx.remedyName() + "'. Prescribing '" + remedyName + "' directly violates relationship laws.",
                        lastRx.remedyName() + " and " + remedyName + " are classified as strictly INIMICAL in classical homeopathy. What clinical urgency justifies prescribing an inimical without intermediate antidoting?",
                        "Unmanageable symptom confusion, conflicting secondary actions, severe therapeutic disruption."
                ));
            }
        }

        // 5. Deep Antipsoric Suppression Risk in Acute Eruptive / Suppurative Crises
        if (isDeepAntipsoric(remIdClean) && isHighCentesimalPotency(potClean)) {
            String combinedText = ((chiefComplaint != null ? chiefComplaint : "") + " " + (clinicalNotes != null ? clinicalNotes : "")).toLowerCase();
            if (combinedText.contains("suppress") || combinedText.contains("eczema") || combinedText.contains("eruption") || combinedText.contains("abscess")) {
                checks.add(new RuleCheck(
                        "DEEP_ANTIPSORIC_ACUTE_SUPPRESSION",
                        "Suppression Hazard: High Antipsoric during Acute Surface Expression",
                        AdminFaithDossierEntry.RuleSeverity.WARNING,
                        "Hahnemann's Chronic Diseases (Psora) & Nash: Administering high-potency deep antipsorics (e.g. Sulphur, Silicea, Psorinum) during acute active surface eruptive flare-ups risks repercussion of the eruption into deep vital organs (metastasis).",
                        "High potency antipsoric '" + remedyName + " " + potency + "' prescribed during active acute surface pathology.",
                        "Are you confident that administering deep antipsoric " + remedyName + " " + potency + " now will not drive the surface eruption inward to the lungs or brain?",
                        "Metastasis to respiratory/central nervous system, sudden cessation of surface discharge with visceral collapse."
                ));
            }
        }

        boolean hasIssues = !checks.isEmpty();
        boolean requiresInterrogation = checks.stream().anyMatch(c ->
                c.severity() == AdminFaithDossierEntry.RuleSeverity.WARNING ||
                c.severity() == AdminFaithDossierEntry.RuleSeverity.VIOLATION ||
                c.severity() == AdminFaithDossierEntry.RuleSeverity.CRITICAL_HAZARD);

        return new AnalysisResult(!hasIssues, requiresInterrogation, checks);
    }

    /**
     * Evaluates the clinical soundness of the doctor's recorded explanation.
     */
    public ExplanationEvaluation evaluateDoctorExplanation(RuleCheck check, String explanation) {
        if (explanation == null || explanation.trim().isBlank()) {
            return new ExplanationEvaluation(
                    false,
                    "CRITICAL",
                    "Unexplained deviation: High risk of uncontrolled medicinal aggravation or suppressive metastasis.",
                    "No clinical rationale recorded by prescriber.",
                    true
            );
        }

        String expClean = explanation.trim().toLowerCase();

        // Legitimate classical escape clauses recognized by Kent, Hahnemann (6th ed), and Vithoulkas:
        boolean citesPlussing = expClean.contains("plus") || expClean.contains("water") || expClean.contains("succuss") || expClean.contains("split dose") || expClean.contains("248");
        boolean citesSevereAgony = expClean.contains("severe pain") || expClean.contains("agony") || expClean.contains("colic") || expClean.contains("emergency") || expClean.contains("crisis");
        boolean citesPalliativeDrainage = expClean.contains("drainage") || expClean.contains("palliative") || expClean.contains("organopathic") || expClean.contains("temporary");
        boolean citesSecondPrescription = expClean.contains("antidote") || expClean.contains("second prescription") || expClean.contains("clearing");

        if (citesPlussing) {
            return new ExplanationEvaluation(
                    true,
                    "LOW",
                    "Minimal risk: Method of administration uses water dilution with succussion, aligning with Organon §248.",
                    "Doctor employs Hahnemannian water-split plussing method; repetition is dynamized and mitigated.",
                    false
            );
        }

        if (citesSevereAgony || citesSecondPrescription) {
            return new ExplanationEvaluation(
                    true,
                    "MODERATE",
                    "Controlled risk: Short-term frequent administration justified by severe acute pain; must discontinue upon earliest sign of amelioration.",
                    "Doctor notes acute clinical urgency; permissible if terminated immediately upon initial relief.",
                    false
            );
        }

        if (citesPalliativeDrainage) {
            return new ExplanationEvaluation(
                    true,
                    "MODERATE",
                    "Moderate risk: Low-tier organopathic drainage intent recognized.",
                    "Interim palliative drainage noted by clinician.",
                    false
            );
        }

        // Dogmatic or ungrounded explanations (e.g. "patient wanted", "routine", "experience says so", "fast cure")
        boolean isRoutineOrDogmatic = expClean.contains("routine") || expClean.contains("patient want") || expClean.contains("fast") || expClean.contains("habit") || expClean.length() < 15;

        if (isRoutineOrDogmatic) {
            return new ExplanationEvaluation(
                    false,
                    "HIGH",
                    check.potentialComplicationsHint() + " (Unorthodox empirical prescribing contrary to individualization)",
                    "Prescriber justification lacks classical Hahnemannian or Kentian posological basis.",
                    true // Secretly logged into Report of Faith!
            );
        }

        // Default evaluation
        return new ExplanationEvaluation(
                false,
                "ELEVATED",
                check.potentialComplicationsHint(),
                "Non-standard justification requiring monitoring: " + explanation.trim(),
                true // Secretly logged to Report of Faith
        );
    }

    /**
     * Synthesizes longitudinal historical case trajectory under Hering's Law of Cure.
     */
    public String analyzeHistoricalCaseTrajectory(
            String patientName,
            List<Consultation> history,
            Remedy currentRemedy,
            String currentPotency
    ) {
        if (history == null || history.isEmpty()) {
            return "First baseline consultation for " + patientName + ". Establish fundamental miasmatic and constitutional diagnosis before longitudinal evaluation.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Classical Historical Case Synthesis for ").append(patientName).append(":\n");
        sb.append("- Total Recorded Consultations: ").append(history.size()).append("\n");

        Consultation last = history.get(0);
        sb.append("- Preceding Consultation: ").append(last.consultDate().toString().substring(0, 10))
          .append(" | Chief Complaint: ").append(last.chiefComplaint()).append("\n");

        if (last.prescriptions() != null && !last.prescriptions().isEmpty()) {
            Prescription p = last.prescriptions().get(0);
            sb.append("- Preceding Rx: ").append(p.remedyName()).append(" ").append(p.potency()).append("\n");

            // Check complement or antidote
            if (currentRemedy != null && p.remedyId().equalsIgnoreCase(currentRemedy.id())) {
                sb.append("✓ Continues remedy '").append(currentRemedy.fullName()).append("'. Check whether previous dose exhausted its action before repeating or ascending potency.\n");
            }
        }

        if (last.heringsLawAnalysis() != null && !last.heringsLawAnalysis().isBlank()) {
            sb.append("- Prior Hering's Law Direction: ").append(last.heringsLawAnalysis()).append("\n");
        }

        sb.append("- Classical Canon Recommendation: Ensure current remedy '")
          .append(currentRemedy != null ? currentRemedy.fullName() : "selected simillimum")
          .append(" ").append(currentPotency).append("' conforms to the order of symptom disappearance (inside-out, above-downward).");

        return sb.toString();
    }

    private static boolean isFrequentRepetition(String freq, int thresholdTimesPerDay) {
        if (freq == null || freq.isBlank()) return false;
        String f = freq.toUpperCase();

        if (thresholdTimesPerDay <= 2 && (f.contains("BD") || f.contains("BID") || f.contains("TWICE") || f.contains("2 TIMES") || f.contains("2 DOSES"))) {
            return true;
        }
        if (f.contains("TDS") || f.contains("TID") || f.contains("THRICE") || f.contains("3 TIMES") || f.contains("3 DOSES") ||
            f.contains("QID") || f.contains("4 TIMES") || f.contains("HOURLY") || f.contains("EVERY 2 HOURS") || f.contains("EVERY 4 HOURS")) {
            return true;
        }
        return false;
    }

    private static boolean isHighCentesimalPotency(String pot) {
        if (pot == null) return false;
        String p = pot.toUpperCase();
        return p.contains("200") || p.contains("1M") || p.contains("10M") || p.contains("50M") || p.contains("CM");
    }

    private static boolean isDeepAntipsoric(String remId) {
        if (remId == null) return false;
        String r = remId.toLowerCase();
        return r.equals("sulph") || r.equals("sil") || r.equals("psor") || r.equals("calc") ||
               r.equals("lyc") || r.equals("graph") || r.equals("caust") || r.equals("med") || r.equals("syph");
    }

    public record SocraticDialogueEvaluation(
            boolean isFullySatisfied,
            int satisfactionScore, // 0 - 100%
            String justificationCategory,
            String aiAssessment,
            String guidanceOrCaution,
            boolean warrantsDoctorSupersede
    ) {}

    /**
     * Socratic evaluation of the consulting doctor's answer to an AI clinical question.
     */
    public SocraticDialogueEvaluation evaluateDoctorSocraticResponse(String questionContext, String doctorAnswer) {
        if (doctorAnswer == null || doctorAnswer.trim().isBlank()) {
            return new SocraticDialogueEvaluation(
                    false,
                    0,
                    "UNANSWERED",
                    "Doctor did not provide a clinical justification.",
                    "Organon §153 and classical canons require explicit justification when deviating from thermal, miasmatic, or posological laws.",
                    true
            );
        }

        String ans = doctorAnswer.toLowerCase();
        int score = 30;
        String category = "CLINICAL_DISCRETION";
        StringBuilder rationale = new StringBuilder();

        // 1. Organon §153 Keynote / Peculiar / Characteristic
        if (ans.contains("keynote") || ans.contains("peculiar") || ans.contains("153") ||
            ans.contains("characteristic") || ans.contains("rare") || ans.contains("uncommon") || ans.contains("striking")) {
            score += 45;
            category = "ORGANON_153_PECULIAR_KEYNOTE";
            rationale.append("✓ Supported by Samuel Hahnemann's Organon §153: Rare, striking, peculiar and uncommon keynote symptoms take precedence over general thermal or miasmatic features. ");
        }

        // 2. Mental Generals Overrule Physicals
        if (ans.contains("mind") || ans.contains("mental") || ans.contains("delirium") ||
            ans.contains("fear of death") || ans.contains("anguish") || ans.contains("disposition") || ans.contains("grief")) {
            score += 25;
            if (category.equals("CLINICAL_DISCRETION")) category = "MENTAL_GENERALS_OVERRULE";
            rationale.append("✓ Validated under J.T. Kent's hierarchy: Mental generals (will, affections, intellect) outrank physical generals and particulars. ");
        }

        // 3. Tautopathic / Suppression Detoxification
        if (ans.contains("tautopath") || ans.contains("suppress") || ans.contains("steroid") ||
            ans.contains("antibiotic") || ans.contains("clearing") || ans.contains("detox") || ans.contains("antidote")) {
            score += 25;
            if (category.equals("CLINICAL_DISCRETION")) category = "TAUTOPATHIC_CLEARING";
            rationale.append("✓ Validated as an intentional unblocking or tautopathic clearing strategy to lift medicinal suppression. ");
        }

        // 4. Acute Intercurrent Strategy
        if (ans.contains("acute") || ans.contains("intercurrent") || ans.contains("crisis") ||
            ans.contains("emergency") || ans.contains("severe pain") || ans.contains("spasm")) {
            score += 20;
            if (category.equals("CLINICAL_DISCRETION")) category = "ACUTE_INTERCURRENT";
            rationale.append("✓ Validated under Boenninghausen & Boger: In acute emergencies, the acute symptom totality is prescribed for prior to constitutional anti-miasmatic work. ");
        }

        // 5. Personal Verified Clinical Experience
        if (ans.contains("experience") || ans.contains("practice") || ans.contains("verified") ||
            ans.contains("seen in cases") || ans.contains("cured before") || ans.contains("empirical")) {
            score += 20;
            if (category.equals("CLINICAL_DISCRETION")) category = "DOCTOR_EXPERIENTIAL_CONFIRMATION";
            rationale.append("✓ Supported by practitioner's empirical clinical experience. ");
        }

        score = Math.min(score, 100);
        boolean satisfied = score >= 75;

        String caution = satisfied
                ? "Classical requirement fulfilled. Case proceeding approved under doctor's noted rationale."
                : "Residual canonical discrepancy detected. If proceeding despite rule tension, record structured 'Doctor Supersede' to preserve clinical experience.";

        return new SocraticDialogueEvaluation(
                satisfied,
                score,
                category,
                rationale.toString().trim(),
                caution,
                !satisfied
        );
    }
}
