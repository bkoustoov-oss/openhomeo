package org.openhomeo.core.model;

/**
 * Metadata record for a digital Materia Medica book / treatise in the 1,500-volume library.
 */
public record MateriaMedicaBook(
        String id,
        String title,
        String author,
        String era,
        String category, // CLASSICAL_PROVINGS, CLINICAL_THERAPEUTICS, REPERTORY_SYNOPTIC, REGIONAL_INDIGENOUS, PHILOSOPHY
        String description,
        int totalSections
) {}
