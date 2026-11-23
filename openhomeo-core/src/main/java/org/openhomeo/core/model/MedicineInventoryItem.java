package org.openhomeo.core.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain record for pharmacy stock items across all dosage forms, potencies, and manufacturers.
 * Covers Indian and global manufacturers: SBL, Dr. Reckeweg, Schwabe, Bakson, Wheezal, Medisynth, etc.
 */
public record MedicineInventoryItem(
        String id,
        String remedyId,
        String medicineName,
        String companyName,        // SBL, Dr. Reckeweg, Schwabe India (WSI), Bakson, Wheezal, Medisynth, Lord's, Haslab, Adel
        String form,               // Globules No. 30, Globules No. 40, Liquid Dilution (30ml/100ml/450ml), Mother Tincture Q, Biochemic Tablets, Ointment/Cream
        String potency,            // Q, 3X, 6X, 12X, 30C, 200C, 1M, 10M, 50M, CM, 0/1 LM, 0/3 LM, 0/6 LM
        String batchNumber,
        LocalDate expiryDate,
        int currentStockQty,       // Quantity available in stock (units, phials, bottles, boxes)
        int reorderLevel,          // Low stock alert threshold
        double unitPriceInr,       // MRP in Indian Rupees
        String rackLocation,       // Physical dispensary shelf/rack (e.g. "Rack B-2, Shelf 3")
        Instant updatedAt
) {
    public boolean isLowStock() {
        return currentStockQty <= reorderLevel;
    }

    public boolean isOutOfStock() {
        return currentStockQty <= 0;
    }
}
