package org.openhomeo.core.model;

import java.time.LocalDate;

/**
 * Domain record for remedy prescription and posology.
 */
public record Prescription(
        String id,
        String consultationId,
        String remedyId,
        String remedyName,
        String potency,                // "30C", "200C", "1M", "LM 0/1", "6X", etc.
        PotencyScale scale,            // CENTESIMAL, DECIMAL, FIFTY_MILLESIMAL
        String posologyInstructions,   // "3 globules dry on tongue", "Split water dose with 10 succussions"
        String repetitionFrequency,    // "Single dose", "TDS x 3 days", "Weekly"
        LocalDate prescribedDate
) {
    public enum PotencyScale {
        CENTESIMAL,       // Hahnemannian C (1:99)
        DECIMAL,          // Hering X/D (1:9)
        FIFTY_MILLESIMAL  // Hahnemannian LM/Q (1:50,000 Organon 6th Ed)
    }
}
