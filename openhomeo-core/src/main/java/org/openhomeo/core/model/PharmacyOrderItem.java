package org.openhomeo.core.model;

/**
 * Line item in a doctor prescription order sent to the pharmacy.
 */
public record PharmacyOrderItem(
        String id,
        String orderId,
        String remedyId,
        String prescribedRemedy,
        String prescribedPotency,
        String prescribedForm,       // e.g. "Globules No. 30", "Liquid Dilution 30ml", "Biochemic 6X"
        String instructions,         // Posology & repetition
        String substitutedRemedy,    // Populated if pharmacist and doctor agree on an alternative
        String substitutedPotency,
        String substitutedForm,
        String company,              // Manufacturer dispensed (e.g. "SBL", "Dr. Reckeweg")
        int quantity,
        double unitPriceInr,
        boolean dispensed
) {
}
