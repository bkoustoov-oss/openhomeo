package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record for prior medications, suppressive treatments, and drug miasm history.
 */
public record PastMedication(
        String id,
        String consultationId,
        String patientId,
        String drugName,
        String category,           // e.g. "Allopathic / Steroid", "Antibiotics", "NSAID / Painkiller", "PPI / Antacid", "Previous Homeopathy", "Ayurvedic"
        String duration,           // e.g. "6 months", "2 years continuous"
        String dosage,             // e.g. "10mg OD", "As needed"
        String suppressedSymptoms, // e.g. "Skin eruption vanished, onset of chronic bronchial asthma"
        String antidoteRemedy,     // e.g. "Nux vomica, Thuja, Sulphur"
        Instant recordedAt
) {
}
