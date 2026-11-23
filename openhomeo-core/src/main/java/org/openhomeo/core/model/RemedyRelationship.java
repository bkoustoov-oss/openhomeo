package org.openhomeo.core.model;

/**
 * Domain record representing clinical relationships between remedies
 * (Gibson Miller, Boenninghausen, and Clarke concordances).
 */
public record RemedyRelationship(
        String id,
        String remedyId,
        String relatedRemedyId,
        RelationType relationType,
        String notes
) {
    public enum RelationType {
        COMPLEMENTARY,  // Completes the curative action (e.g. Belladonna -> Calcarea carb)
        ANTIDOTE,       // Neutralizes or antidotes action (e.g. Camphora antidotes plant remedies)
        FOLLOWS_WELL,   // Clinically succeeds well in chronological succession
        INIMICAL        // Incompatible / adverse when administered sequentially (e.g. Phos & Caust)
    }

    public static RemedyRelationship of(String remedyId, String relatedRemedyId, RelationType type) {
        String id = remedyId.toLowerCase() + "_" + type.name().toLowerCase() + "_" + relatedRemedyId.toLowerCase();
        return new RemedyRelationship(id, remedyId.toLowerCase(), relatedRemedyId.toLowerCase(), type, null);
    }
}
