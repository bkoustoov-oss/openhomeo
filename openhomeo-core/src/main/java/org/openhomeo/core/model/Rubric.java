package org.openhomeo.core.model;

import java.util.List;

/**
 * Domain record representing a hierarchical rubric in the Repertory.
 */
public record Rubric(
        String id,                  // Unique path hash or slug
        String chapter,             // e.g. "MIND", "HEAD", "GENERALITIES"
        String parentId,            // null for top-level rubrics
        String rubricText,          // e.g. "bed, in"
        String fullPath,            // e.g. "MIND - ANXIETY - evening - bed, in"
        int depth,                  // 0 = top-level in chapter, 1 = sub-rubric, etc.
        boolean isPolar,            // True if polar symptom for Boenninghausen analysis
        String oppositeRubricId,    // Reference to opposite rubric for polarity contraindication
        List<RubricRemedy> remedies // Remedies exhibiting this rubric
) {
    public Rubric withRemedies(List<RubricRemedy> remediesList) {
        return new Rubric(id, chapter, parentId, rubricText, fullPath, depth, isPolar, oppositeRubricId, remediesList);
    }
}
