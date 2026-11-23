package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Dispensary Billing & Invoice Record.
 * Calculates Subtotal, Discount, Indian GST (5% on medicines), Payment Mode, and Status.
 */
public record PharmacyBill(
        String id,
        String billNumber,           // e.g. "INV-2026-0042"
        String orderId,              // Associated pharmacy order if applicable
        String patientId,
        String patientName,
        String doctorName,
        Instant billDate,
        double subtotalInr,
        double discountInr,
        double gstAmountInr,         // 5% GST (2.5% CGST + 2.5% SGST)
        double totalInr,
        String paymentMode,          // "CASH", "UPI / GPay / PhonePe", "CARD", "NET_BANKING"
        String paymentStatus,        // "PAID", "PENDING"
        String itemsJson             // Formatted list of billed items
) {
}
