package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record capturing clinical instances where a doctor supersedes or nullifies
 * an algorithmic/entry-point remedy suggestion.
 * Builds an evidence database for machine learning, case studies, and clinical analysis.
 */
public record RemedySupersedeAudit(
        String id,
        String consultationId,
        String patientId,
        String doctorId,
        String suggestedRemedyId,
        String suggestedRemedyName,
        String prescribedRemedyId,
        String prescribedRemedyName,
        String nullificationReason,    // Why the doctor rejected/superseded the algorithm's choice
        String clinicalCaseNotes,      // In-depth case study notes and differentiators
        String caseStudyTag,           // e.g. "Keynote Dominance", "Etiology over Modality", "Suppression Antidote"
        Instant createdAt
) {
}
