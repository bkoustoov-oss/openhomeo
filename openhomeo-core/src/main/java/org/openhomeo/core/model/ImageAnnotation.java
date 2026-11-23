package org.openhomeo.core.model;

/**
 * Domain record for non-destructive clinical vector annotations overlaid on images.
 */
public record ImageAnnotation(
        String id,
        String imageId,
        AnnotationType type,    // RECTANGLE, POLYGON, ARROW, MEASUREMENT_LINE
        String geometryJson,    // JSON vector data: [{"x":120, "y":240}, ...]
        String label,           // e.g. "Primary lesion 14.2mm", "Thick white fur"
        String colorHex         // e.g. "#FF0000"
) {
    public enum AnnotationType {
        RECTANGLE,
        POLYGON,
        ARROW,
        MEASUREMENT_LINE
    }
}
