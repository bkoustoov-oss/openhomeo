package org.openhomeo.core.repertory;

import org.openhomeo.core.model.Rubric;

import java.util.*;

/**
 * Manages multiple symptom clipboards (1 to 5) for collecting and weighting rubrics
 * during patient case taking (RadarOpus / Synergy style).
 */
public class SymptomClipboard {
    private final Map<Integer, List<ClipboardItem>> clipboards = new HashMap<>();
    private int activeClipboardIndex = 1;

    public record ClipboardItem(
            Rubric rubric,
            double userWeight,       // 1.0, 2.0, 3.0, 4.0
            boolean isEliminative    // Absolute must-have symptom
    ) {
        public ClipboardItem withWeight(double weight) {
            return new ClipboardItem(rubric, weight, isEliminative);
        }

        public ClipboardItem withEliminative(boolean eliminative) {
            return new ClipboardItem(rubric, userWeight, eliminative);
        }
    }

    public SymptomClipboard() {
        for (int i = 1; i <= 5; i++) {
            clipboards.put(i, new ArrayList<>());
        }
    }

    public void setActiveClipboardIndex(int index) {
        if (index < 1 || index > 5) throw new IllegalArgumentException("Clipboard index must be 1-5");
        this.activeClipboardIndex = index;
    }

    public int getActiveClipboardIndex() {
        return activeClipboardIndex;
    }

    public synchronized void addRubric(Rubric rubric) {
        addRubricToClipboard(activeClipboardIndex, rubric, 1.0, false);
    }

    public synchronized void addRubricToClipboard(int clipboardIndex, Rubric rubric, double weight, boolean isEliminative) {
        List<ClipboardItem> items = clipboards.get(clipboardIndex);
        // Avoid duplicate rubric in same clipboard
        for (ClipboardItem it : items) {
            if (it.rubric().id().equals(rubric.id())) {
                return;
            }
        }
        items.add(new ClipboardItem(rubric, weight, isEliminative));
    }

    public synchronized void removeRubric(int clipboardIndex, String rubricId) {
        List<ClipboardItem> items = clipboards.get(clipboardIndex);
        items.removeIf(item -> item.rubric().id().equals(rubricId));
    }

    public synchronized void updateItem(int clipboardIndex, String rubricId, double weight, boolean isEliminative) {
        List<ClipboardItem> items = clipboards.get(clipboardIndex);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).rubric().id().equals(rubricId)) {
                items.set(i, new ClipboardItem(items.get(i).rubric(), weight, isEliminative));
                return;
            }
        }
    }

    public synchronized void clear(int clipboardIndex) {
        clipboards.get(clipboardIndex).clear();
    }

    public synchronized List<ClipboardItem> getItems(int clipboardIndex) {
        return Collections.unmodifiableList(new ArrayList<>(clipboards.get(clipboardIndex)));
    }

    public synchronized List<ClipboardItem> getActiveItems() {
        return getItems(activeClipboardIndex);
    }
}
