package org.openhomeo.core.model;

/**
 * Domain record representing an authoritative section from a classical Materia Medica text
 * (Boericke, Allen, Kent, Clarke, Hering, Hahnemann).
 */
public record MateriaMedicaEntry(
        String id,
        String remedyId,
        String author,       // "William Boericke", "H.C. Allen", "J.T. Kent", etc.
        String bookTitle,    // "Pocket Manual of Homeopathic Materia Medica"
        String section,      // "MIND", "HEAD", "STOMACH", "MODALITIES", "RELATIONSHIP", etc.
        String bodyText
) {
    public static MateriaMedicaEntry of(String remedyId, String author, String bookTitle, String section, String text) {
        String id = remedyId.toLowerCase() + "_" + author.replaceAll("\\s+", "").toLowerCase() + "_" + section.toLowerCase();
        return new MateriaMedicaEntry(id, remedyId.toLowerCase(), author, bookTitle, section.toUpperCase(), text);
    }
}
