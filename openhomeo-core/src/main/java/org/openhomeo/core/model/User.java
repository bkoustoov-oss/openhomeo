package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record for authenticated practitioners and clinic staff.
 */
public record User(
        String id,
        String username,
        String passwordHash,    // Argon2id hash string
        String fullName,
        UserRole role,
        Instant createdAt
) {
    public enum UserRole {
        ADMIN,          // Full administrative, clinical, and pharmacy system control
        DOCTOR,         // Consulting Homeopath / Physician (Case-taking, Prescriptions, Repertory, Stock view & Reorder approvals)
        PHARMACIST,     // Pharmacy & Dispensary Specialist (Stock register, batch entry, internal consultation, billing)
        HOMEOPATH,      // Backward-compatible alias for DOCTOR
        ASSISTANT,      // Patient registration, appointments
        STUDENT         // Read-only access to Repertory & Materia Medica
    }
}
