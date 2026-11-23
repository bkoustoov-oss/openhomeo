package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record for proposed corrections or clinical remarks submitted by doctors or pharmacists,
 * awaiting Admin approval before being annexed into the master medicine constituent data.
 */
public record MedicineConstituentRemark(
        String id,
        String remedyId,
        String submittedByUserId,
        String submittedByUserName,
        String proposedRemark,
        Status status,
        String reviewedByAdminId,
        Instant submittedAt,
        Instant reviewedAt
) {
    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }
}
