package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record representing the clinic/institution registration details,
 * official letterhead credentials, address, and prescribing guidelines.
 */
public record ClinicInstitutionProfile(
        String id,
        String institutionName,
        String tagline,
        String registrationNumber,
        String address,
        String phone,
        String email,
        String website,
        String chiefConsultantName,
        String chiefConsultantQualifications,
        String chiefConsultantRegNo,
        String defaultCurrency,
        String defaultRegimenInstructions,
        Instant updatedAt
) {
    public static ClinicInstitutionProfile defaultProfile() {
        return new ClinicInstitutionProfile(
                "inst-default",
                "HAHNEMANNIAN CLASSICAL HOMEOPATHIC CLINIC",
                "Advanced Classical Posology & Miasmatic Practice",
                "REG-CLINIC-2026/01",
                "Central Healthcare Facility Premises, Medical Enclave",
                "+91 00000 00000",
                "clinic@openhomeo.local",
                "https://openhomeo.org",
                "Dr. Consulting Physician",
                "B.H.M.S., M.D. (Homoeopathy)",
                "REG-HOM-2026/01",
                "INR (₹)",
                "• Avoid strong smelling substances (raw onion, garlic, menthol, camphor, eucalyptus, strong coffee) 30 mins before & after dose.\n• Take globules dry on a clean tongue or dissolved in fresh water as instructed.",
                Instant.now()
        );
    }
}
