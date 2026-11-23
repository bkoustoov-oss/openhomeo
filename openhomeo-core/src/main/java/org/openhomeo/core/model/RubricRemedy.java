package org.openhomeo.core.model;

/**
 * Domain record representing the linkage of a remedy to a rubric with a specific gradation.
 */
public record RubricRemedy(
        String rubricId,
        String remedyId,
        int grade,              // 1 (plain), 2 (italic), 3 (bold), 4 (bold uppercase)
        String authorCitation   // 'k' (Kent), 'b' (Boericke), 'a' (Allen), 'c' (Clarke), 'user'
) {
    public RubricRemedy {
        if (grade < 1 || grade > 4) {
            throw new IllegalArgumentException("Grade must be between 1 and 4, got: " + grade);
        }
    }
}
