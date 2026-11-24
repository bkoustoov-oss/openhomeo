package org.openhomeo.core.model;

/**
 * An individual remedy monograph or chapter section within a Materia Medica treatise.
 */
public record MateriaMedicaSection(
        String id,
        String bookId,
        String bookTitle,
        String author,
        String remedyName,
        String sectionHeading,
        String bodyText,
        String pageOrVolRef
) {}
