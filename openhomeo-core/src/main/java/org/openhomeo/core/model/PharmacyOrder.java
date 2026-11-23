package org.openhomeo.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Doctor prescription order transmitted to the pharmacy dispensary.
 * Supports bilateral internal consultation and substitution workflow.
 */
public record PharmacyOrder(
        String id,
        String orderNumber,          // e.g. "ORD-2026-0042"
        String consultationId,
        String patientId,
        String patientName,
        String doctorId,
        String doctorName,
        OrderStatus status,
        String substitutionNotes,    // Notes exchanged between pharmacist & doctor regarding available brands/forms
        String pharmacistAgreedBy,
        String doctorAgreedBy,
        Instant orderDate,
        Instant completedDate,
        List<PharmacyOrderItem> items
) {
    public enum OrderStatus {
        ORDERED,                     // Doctor sent order to pharmacy
        CONSULTATION_REQUESTED,      // Pharmacist flagged out-of-stock and suggested substitution
        AGREED_SUBSTITUTION,         // Doctor and pharmacist agreed on substitution
        REJECTED_BY_DOCTOR,          // Doctor rejected proposed substitution
        DISPENSED,                   // Medicine prepared and delivered to patient
        CANCELLED                    // Order cancelled
    }
}
