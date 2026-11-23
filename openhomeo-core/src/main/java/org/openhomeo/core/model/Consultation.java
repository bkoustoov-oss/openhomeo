package org.openhomeo.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Domain record representing an encounter / case-taking consultation.
 */
public record Consultation(
        String id,
        String patientId,
        String doctorId,               // Enlisted consulting doctor
        Instant consultDate,
        String chiefComplaint,         // Primary reason for encounter
        String locationNotes,          // L (Location & lateralization)
        String sensationNotes,         // S (Sensations & character of pain)
        String modalityNotes,          // M (Aggravation & Amelioration factors)
        String concomitantNotes,       // C (Accompanying symptoms)
        String mindDisposition,        // Mental & emotional portrait
        String physicalGenerals,       // Thermals, thirst, sleep, dreams, appetite
        String heringsLawAnalysis,     // Follow-up direction evaluation
        String doctorNotes,            // Private clinical impression
        List<SelectedRubric> selectedRubrics,
        List<Prescription> prescriptions
) {
    public Consultation(String id, String patientId, Instant consultDate, String chiefComplaint,
                        String locationNotes, String sensationNotes, String modalityNotes,
                        String concomitantNotes, String mindDisposition, String physicalGenerals,
                        String heringsLawAnalysis, String doctorNotes, List<SelectedRubric> selectedRubrics,
                        List<Prescription> prescriptions) {
        this(id, patientId, null, consultDate, chiefComplaint, locationNotes, sensationNotes,
                modalityNotes, concomitantNotes, mindDisposition, physicalGenerals,
                heringsLawAnalysis, doctorNotes, selectedRubrics, prescriptions);
    }

    public record SelectedRubric(
            String rubricId,
            String fullPath,
            int clipboardIndex,        // 1 to 5
            double userWeight,         // 1.0, 2.0, 3.0, 4.0
            boolean isEliminative      // Must-have rubric
    ) {}
}
