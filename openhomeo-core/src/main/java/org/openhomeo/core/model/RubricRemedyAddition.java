package org.openhomeo.core.model;

/**
 * OpenSynthesis addition record representing an expansion to a classical rubric.
 * Connects a remedy to a rubric with specific degree (1-4), author citation, and reference.
 */
public record RubricRemedyAddition(
        String rubricId,
        String remedyId,
        int degree,             // 1 = plain (1), 2 = italics (2), 3 = bold (3), 4 = bold underlined (4)
        String authorCode,      // e.g. k1 (Kent), b1 (Boenninghausen), a1 (Allen), c1 (Clarke), boer1 (Boericke), h1 (Hahnemann), ccrh24 (CCRH)
        String authorName,      // Full authoritative name
        String citationRef,     // Specific monograph or repertory reference
        boolean isVerified
) {}
