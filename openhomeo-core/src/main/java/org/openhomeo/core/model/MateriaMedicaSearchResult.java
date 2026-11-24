package org.openhomeo.core.model;

/**
 * High-performance FTS5 BM25 search result across the 1,500-book Materia Medica digital library.
 */
public record MateriaMedicaSearchResult(
        String bookId,
        String bookTitle,
        String author,
        String remedyName,
        String sectionHeading,
        String snippet,
        double score
) {}
