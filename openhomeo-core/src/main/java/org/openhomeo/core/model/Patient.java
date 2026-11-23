package org.openhomeo.core.model;

import java.time.LocalDate;
import java.time.Instant;

/**
 * Domain record representing a patient in the clinic EMR.
 */
public record Patient(
        String id,
        String caseNumber,
        String firstName,
        String lastName,
        LocalDate dob,
        String gender,
        String occupation,
        String phone,
        String email,
        String address,
        String bloodGroup,
        String constitutionalType,    // e.g. Hydrogenoid, Oxygenoid, Carbo-nitrogenoid, Phosphoric
        String miasmaticBackground,   // Primary and secondary miasms
        Instant createdAt,
        Instant updatedAt
) {
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
