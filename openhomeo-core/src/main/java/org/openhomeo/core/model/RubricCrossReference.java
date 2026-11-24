package org.openhomeo.core.model;

/**
 * Cross-reference link between rubrics in the Synthesis knowledge graph.
 * Supports synonym mapping, see-also guidance, and polar opposite contrasts.
 */
public record RubricCrossReference(
        String sourceRubricId,
        String targetRubricId,
        String targetFullPath,
        RelationshipType relationshipType
) {
    public enum RelationshipType {
        SYNONYM,
        SEE_ALSO,
        POLAR_OPPOSITE,
        CONTRAST
    }
}
