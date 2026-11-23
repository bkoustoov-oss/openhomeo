package org.openhomeo.core.ai;

import org.openhomeo.core.model.AdminFaithDossierEntry;
import org.openhomeo.core.model.Prescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PrescriptionLogicAnalyzerTest {

    private PrescriptionLogicAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PrescriptionLogicAnalyzer();
    }

    @Test
    void testKent30cOverRepetitionFlagged() {
        // Kentian posology rule: 30C should rarely if ever be given 3+ times a day in dry doses
        PrescriptionLogicAnalyzer.AnalysisResult result = analyzer.analyzePrescription(
                "lyc", "Lycopodium clavatum", "30C", Prescription.PotencyScale.CENTESIMAL,
                "TDS (3 times daily) for 7 days", List.of(), "Gastric bloating", ""
        );

        assertFalse(result.isCanonCompliant());
        assertTrue(result.requiresDoctorInterrogation());
        assertTrue(result.ruleChecks().stream().anyMatch(c -> c.ruleCode().equals("KENT_30C_OVERREPETITION")));

        PrescriptionLogicAnalyzer.RuleCheck check = result.ruleChecks().stream()
                .filter(c -> c.ruleCode().equals("KENT_30C_OVERREPETITION")).findFirst().orElseThrow();
        assertEquals(AdminFaithDossierEntry.RuleSeverity.WARNING, check.severity());
        assertTrue(check.clinicalQuestion().contains("Why have you prescribed 3 doses of 30C"));
    }

    @Test
    void testHighPotencyRepetitionFlagged() {
        // 200C given twice daily in dry form
        PrescriptionLogicAnalyzer.AnalysisResult result = analyzer.analyzePrescription(
                "ars", "Arsenicum album", "200C", Prescription.PotencyScale.CENTESIMAL,
                "BD (Twice daily)", List.of(), "Anxiety and midnight restlessness", ""
        );

        assertFalse(result.isCanonCompliant());
        assertTrue(result.requiresDoctorInterrogation());
        assertTrue(result.ruleChecks().stream().anyMatch(c -> c.ruleCode().equals("HIGH_POTENCY_REPETITION_HAZARD")));
    }

    @Test
    void testGibsonMillerInimicalSequenceFlagged() {
        // Patient recently received Apis mellifica; Doctor prescribes Rhus tox
        Prescription pastRx = new Prescription(
                "rx-1", "cons-1", "apis", "Apis mellifica", "30C",
                Prescription.PotencyScale.CENTESIMAL, "Single dose", "Once", LocalDate.now().minusDays(5)
        );

        PrescriptionLogicAnalyzer.AnalysisResult result = analyzer.analyzePrescription(
                "rhus-t", "Rhus toxicodendron", "200C", Prescription.PotencyScale.CENTESIMAL,
                "Single dose", List.of(pastRx), "Joint stiffness", ""
        );

        assertFalse(result.isCanonCompliant());
        assertTrue(result.ruleChecks().stream().anyMatch(c -> c.ruleCode().equals("GIBSON_MILLER_INIMICAL_SEQUENCE")));
        PrescriptionLogicAnalyzer.RuleCheck check = result.ruleChecks().stream()
                .filter(c -> c.ruleCode().equals("GIBSON_MILLER_INIMICAL_SEQUENCE")).findFirst().orElseThrow();
        assertEquals(AdminFaithDossierEntry.RuleSeverity.CRITICAL_HAZARD, check.severity());
    }

    @Test
    void testPotencyEscalationJumpFlagged() {
        // Patient was on 30C, current Rx jumps to 10M
        Prescription pastRx = new Prescription(
                "rx-1", "cons-1", "nux-v", "Nux vomica", "30C",
                Prescription.PotencyScale.CENTESIMAL, "2 doses", "Twice", LocalDate.now().minusDays(10)
        );

        PrescriptionLogicAnalyzer.AnalysisResult result = analyzer.analyzePrescription(
                "nux-v", "Nux vomica", "10M", Prescription.PotencyScale.CENTESIMAL,
                "Single dose", List.of(pastRx), "Spastic dyspepsia", ""
        );

        assertFalse(result.isCanonCompliant());
        assertTrue(result.ruleChecks().stream().anyMatch(c -> c.ruleCode().equals("POTENCY_ESCALATION_JUMP")));
    }

    @Test
    void testBiochemicSaltAndMotherTinctureExempted() {
        // Ferrum phos 6X repeated 3 times a day is standard biochemic practice
        PrescriptionLogicAnalyzer.AnalysisResult result = analyzer.analyzePrescription(
                "ferr-p", "Ferrum phosphoricum", "6X", Prescription.PotencyScale.DECIMAL,
                "TDS (3 times daily)", List.of(), "Early febrile congestion", ""
        );

        assertTrue(result.isCanonCompliant());
        assertFalse(result.requiresDoctorInterrogation());
    }

    @Test
    void testDoctorExplanationEvaluation() {
        PrescriptionLogicAnalyzer.RuleCheck check = new PrescriptionLogicAnalyzer.RuleCheck(
                "KENT_30C_OVERREPETITION", "Limit", AdminFaithDossierEntry.RuleSeverity.WARNING,
                "Kent Citation", "Detail", "Question", "Drug proving complication"
        );

        // Sound justification using Hahnemannian split water plussing method
        PrescriptionLogicAnalyzer.ExplanationEvaluation goodEval = analyzer.evaluateDoctorExplanation(
                check, "Administered dissolved in 4 oz of water, 1 teaspoonful split dose with 5 vigorous succussions prior to each sip (Organon §248 plussing method)."
        );
        assertTrue(goodEval.isClinicallySound());
        assertEquals("LOW", goodEval.riskGrade());
        assertFalse(goodEval.shouldLogToFaithDossier());

        // Shallow/Routine justification that violates individualization
        PrescriptionLogicAnalyzer.ExplanationEvaluation badEval = analyzer.evaluateDoctorExplanation(
                check, "Routine habit for fast action"
        );
        assertFalse(badEval.isClinicallySound());
        assertEquals("HIGH", badEval.riskGrade());
        assertTrue(badEval.shouldLogToFaithDossier());
    }
}
