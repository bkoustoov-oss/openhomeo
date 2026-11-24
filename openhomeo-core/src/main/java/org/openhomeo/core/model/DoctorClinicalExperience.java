package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Structured repository record capturing the consulting doctor's hard-won clinical experience
 * when superseding deterministic AI canon checks or discovering verified keynotes.
 */
public record DoctorClinicalExperience(
        String id,
        String doctorId,
        String doctorName,
        String patientId,
        String caseNumber,
        String remedyId,
        String remedyName,
        String overriddenRule,
        ExperienceType experienceType,
        String clinicalObservation,
        String verifiedKeynote,
        String outcomeStatus, // PENDING_VERIFICATION, VERIFIED_CURE, RELIEVED, PARTIAL
        Instant createdAt
) {
    public enum ExperienceType {
        VERIFIED_CLINICAL_KEYNOTE("Verified Clinical Keynote (Empirical confirmation)"),
        PECULIAR_MODALITY_DISCOVERY("Peculiar Modality Observation (§153)"),
        INTERCURRENT_BLOCKED_CASE("Intercurrent Case Unblocker"),
        INDO_ASIAN_CLIMATIC_ADAPTATION("Regional / Tropical Climatic Adaptation"),
        FAMILY_CLINICAL_CONFIRMATION("Taxonomical / Family Affinity Confirmation"),
        TAUTOPATHIC_CLEARING("Tautopathic Drug Clearing & Detoxification");

        private final String label;

        ExperienceType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
