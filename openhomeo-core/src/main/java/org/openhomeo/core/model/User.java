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
        ADMIN,          // Full administrative and system control
        HOMEOPATH,      // Full clinical access (patients, repertory, prescriptions)
        ASSISTANT,      // Patient registration, appointments (no psychiatric/mind access)
        STUDENT         // Read-only access to Repertory & Materia Medica
    }
}
