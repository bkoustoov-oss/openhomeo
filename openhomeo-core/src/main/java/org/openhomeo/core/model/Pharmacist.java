package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record representing an enlisted pharmacist or dispensary specialist.
 */
public record Pharmacist(
        String id,
        String fullName,
        String registrationNo,
        String qualification,
        String dispensaryName,
        String address,
        String phone,
        String email,
        boolean isActive,
        Instant createdAt
) {
    public String displayTitle() {
        if (qualification != null && !qualification.isBlank()) {
            return fullName + ", " + qualification;
        }
        return fullName;
    }
}
