package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Volume stock reorder requisition drafted by pharmacist and approved by Doctor or Admin.
 */
public record StockReorderRequest(
        String id,
        String requestNumber,        // e.g. "REO-2026-0012"
        String requestedByUser,      // Pharmacist username
        ReorderStatus status,
        String approvedByUser,       // Doctor or Admin username
        Instant approvalDate,
        double totalEstimatedInr,
        String notes,
        String itemsJson,            // Structured JSON list of reorder items (Remedy, Company, Form, Potency, Volume/Units)
        Instant createdAt
) {
    public enum ReorderStatus {
        PENDING_APPROVAL,            // Waiting for Doctor or Admin approval
        APPROVED,                    // Approved by Doctor / Admin
        REJECTED,                    // Rejected by Doctor / Admin
        ORDER_PLACED,                // Order placed with manufacturer/distributor
        RECEIVED                     // Consignment arrived, stock credited to inventory
    }
}
