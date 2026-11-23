package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record for the strictly confidential "Report of Faith" (Clinical Discrepancy & Audit Dossier).
 * Accessible solely to the System Administrator to detect unorthodox, repetitive,
 * dangerous, or questionable prescription patterns evaluated by the Classical Logic AI.
 */
public record AdminFaithDossierEntry(
        String id,
        String consultationId,
        String patientId,
        String patientName,
        String doctorId,
        String doctorName,
        String remedyId,
        String remedyName,
        String potency,
        String repetitionFrequency,
        String violatedRule,
        RuleSeverity ruleSeverity,
        String doctorExplanation,
        String aiRiskAssessment,
        String predictedComplications,
        boolean isAudited,
        Instant createdAt
) {
    public enum RuleSeverity {
        ADVISORY,
        WARNING,
        VIOLATION,
        CRITICAL_HAZARD
    }
}
