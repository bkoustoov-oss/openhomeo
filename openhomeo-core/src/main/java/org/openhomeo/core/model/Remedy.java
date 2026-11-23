package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Immutable domain record representing a homeopathic medicine / remedy.
 */
public record Remedy(
        String id,              // Canonical ID (lowercase, e.g. "lyc", "ars", "phos")
        String abbr,            // Standard abbreviation (e.g. "Lyc.", "Ars.", "Phos.")
        String fullName,        // Full Latin binomial (e.g. "Lycopodium clavatum")
        String commonName,      // Vernacular name (e.g. "Club moss")
        String kingdom,         // Plant, Mineral, Animal, Nosode, Sarcode, Imponderable
        String family,          // Botanical family or chemical group
        Integer periodicStage,  // Stage 1-18 for mineral remedies (Scholten/Sankaran)
        Integer periodicSeries, // Series 1-7 for mineral remedies
        String miasm,           // PSORA, SYCOSIS, SYPHILIS, TUBERCULAR, CANCER
        Thermal thermal,        // CHILLY, HOT, AMBITHERMAL, UNKNOWN
        Thirst thirst,          // THIRSTLESS, THIRSTY, EXTREME, UNKNOWN
        Instant createdAt
) {
    public enum Thermal { CHILLY, HOT, AMBITHERMAL, UNKNOWN }
    public enum Thirst { THIRSTLESS, THIRSTY, EXTREME, UNKNOWN }

    public static Remedy of(String id, String abbr, String fullName, String commonName, 
                             String kingdom, String family, String miasm, Thermal thermal, Thirst thirst) {
        return new Remedy(id.toLowerCase(), abbr, fullName, commonName, kingdom, family, null, null, miasm, thermal, thirst, Instant.now());
    }
}
