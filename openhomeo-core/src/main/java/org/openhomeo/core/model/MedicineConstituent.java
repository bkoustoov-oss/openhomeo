package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record for readymade phytochemical, biochemical, and pharmacological
 * constituent information for homeopathic medicines, with annexed clinical remarks.
 */
public record MedicineConstituent(
        String remedyId,
        String remedyName,
        String commonAndIndianName,
        String activeConstituents,
        String pharmacologicalAction,
        String organAffinity,
        String toxicologyAndAntidotes,
        String annexedRemarks,
        Instant updatedAt
) {
    public static MedicineConstituent of(
            String remedyId,
            String remedyName,
            String commonAndIndianName,
            String activeConstituents,
            String pharmacologicalAction,
            String organAffinity,
            String toxicologyAndAntidotes
    ) {
        return new MedicineConstituent(
                remedyId,
                remedyName,
                commonAndIndianName,
                activeConstituents,
                pharmacologicalAction,
                organAffinity,
                toxicologyAndAntidotes,
                "",
                Instant.now()
        );
    }
}
