package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record representing an enlisted homeopathic physician / doctor.
 */
public record Doctor(
        String id,
        String fullName,
        String registrationNo,
        String qualification,
        String designation,
        String clinicName,
        String address,
        String phone,
        String email,
        String signatureNotes,
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
