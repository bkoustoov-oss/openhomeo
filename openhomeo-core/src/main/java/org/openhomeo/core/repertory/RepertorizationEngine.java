package org.openhomeo.core.repertory;

import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.Remedy;
import org.openhomeo.core.model.Rubric;
import org.openhomeo.core.model.RubricRemedy;

import java.sql.SQLException;
import java.util.*;

/**
 * Mathematical Repertorization Engine implementing:
 * 1. Classical Kentian Method (Sum of Symptoms & Degrees)
 * 2. Boenninghausen Polarity Analysis (Modality Contraindications)
 * 3. Eliminative Rubric Filters
 * 4. Kingdom, Botanical Family, and Miasmatic Taxonomy Projections
 */
public class RepertorizationEngine {

    public enum Algorithm {
        KENTIAN_SUM_OF_DEGREES,
        BOENNINGHAUSEN_POLARITY,
        QUALITATIVE_SYMPTOM_COUNT
    }

    public record Options(
            Algorithm algorithm,
            String kingdomFilter,   // e.g. "Plant", "Mineral", "Animal"
            String familyFilter,    // e.g. "Solanaceae", "Ranunculaceae"
            String miasmFilter,     // e.g. "PSORA", "SYCOSIS", "SYPHILIS"
            boolean applyEliminative
    ) {
        public static Options defaultOptions() {
            return new Options(Algorithm.KENTIAN_SUM_OF_DEGREES, null, null, null, true);
        }

        public static Options polarityAnalysis() {
            return new Options(Algorithm.BOENNINGHAUSEN_POLARITY, null, null, null, true);
        }
    }

    public RepertorizationResult calculate(
            List<SymptomClipboard.ClipboardItem> items,
            MasterKnowledgeDao dao,
            Options options
    ) throws SQLException {
        if (items == null || items.isEmpty()) {
            return new RepertorizationResult(List.of(), 0, false, false);
        }

        // Cache all remedies metadata
        Map<String, Remedy> remedyMap = new HashMap<>();
        for (Remedy r : dao.getAllRemedies()) {
            remedyMap.put(r.id().toLowerCase(), r);
        }

        // Fetch full rubric details with remedy gradations
        List<FullRubricItem> fullItems = new ArrayList<>();
        Set<String> eliminativeRubricIds = new HashSet<>();

        for (SymptomClipboard.ClipboardItem it : items) {
            Rubric rWithRemedies = dao.getRubricWithRemedies(it.rubric().id());
            if (rWithRemedies != null) {
                fullItems.add(new FullRubricItem(rWithRemedies, it.userWeight(), it.isEliminative()));
                if (options.applyEliminative() && it.isEliminative()) {
                    eliminativeRubricIds.add(rWithRemedies.id());
                }
            }
        }

        // Pre-fetch opposite polar rubrics if Polarity Analysis is active
        Map<String, Rubric> oppositeRubricsMap = new HashMap<>();
        if (options.algorithm() == Algorithm.BOENNINGHAUSEN_POLARITY) {
            for (FullRubricItem fri : fullItems) {
                if (fri.rubric.isPolar() && fri.rubric.oppositeRubricId() != null) {
                    Rubric opp = dao.getRubricWithRemedies(fri.rubric.oppositeRubricId());
                    if (opp != null) {
                        oppositeRubricsMap.put(fri.rubric.id(), opp);
                    }
                }
            }
        }

        // Aggregate scores per remedy
        Map<String, RemedyAccumulator> accumulators = new HashMap<>();

        for (FullRubricItem fri : fullItems) {
            String rubricId = fri.rubric.id();
            double multiplier = fri.userWeight;

            for (RubricRemedy rr : fri.rubric.remedies()) {
                String remId = rr.remedyId().toLowerCase();
                Remedy remedy = remedyMap.get(remId);
                if (remedy == null) continue;

                // Taxonomy filter checks
                if (options.kingdomFilter() != null && !options.kingdomFilter().equalsIgnoreCase(remedy.kingdom())) {
                    continue;
                }
                if (options.familyFilter() != null && !options.familyFilter().equalsIgnoreCase(remedy.family())) {
                    continue;
                }
                if (options.miasmFilter() != null && !options.miasmFilter().equalsIgnoreCase(remedy.miasm())) {
                    continue;
                }

                RemedyAccumulator acc = accumulators.computeIfAbsent(remId, k -> new RemedyAccumulator(remedy));
                acc.coveredCount++;
                acc.totalWeightedScore += (rr.grade() * multiplier);
                acc.rubricGrades.put(rubricId, rr.grade());

                if (fri.rubric.isPolar()) {
                    acc.positivePolarDegrees += rr.grade();
                }
            }
        }

        // Check opposite polar rubrics for contraindications
        for (FullRubricItem fri : fullItems) {
            Rubric opp = oppositeRubricsMap.get(fri.rubric.id());
            if (opp != null) {
                for (RubricRemedy oppRr : opp.remedies()) {
                    String remId = oppRr.remedyId().toLowerCase();
                    RemedyAccumulator acc = accumulators.get(remId);
                    if (acc != null) {
                        acc.contraindicatedPolarDegrees += oppRr.grade();
                        acc.contraindicationCount++;
                        if (oppRr.grade() >= 3) {
                            acc.isContraindicated = true;
                        }
                    }
                }
            }
        }

        // Apply eliminative filter: remedy must cover every eliminative rubric
        List<RepertorizationResult.RemedyScore> scores = new ArrayList<>();
        for (RemedyAccumulator acc : accumulators.values()) {
            if (!eliminativeRubricIds.isEmpty()) {
                boolean coversAllEliminative = true;
                for (String elimRubricId : eliminativeRubricIds) {
                    if (!acc.rubricGrades.containsKey(elimRubricId)) {
                        coversAllEliminative = false;
                        break;
                    }
                }
                if (!coversAllEliminative) {
                    continue; // Discard remedy
                }
            }

            int polarityDiff = acc.positivePolarDegrees - acc.contraindicatedPolarDegrees;

            scores.add(new RepertorizationResult.RemedyScore(
                    acc.remedy.id(),
                    acc.remedy.fullName(),
                    acc.remedy.abbr(),
                    acc.coveredCount,
                    acc.totalWeightedScore,
                    polarityDiff,
                    acc.contraindicationCount,
                    acc.isContraindicated,
                    acc.remedy.kingdom(),
                    acc.remedy.family(),
                    acc.remedy.miasm(),
                    acc.rubricGrades
            ));
        }

        // Sorting based on selected algorithm
        scores.sort((a, b) -> {
            if (options.algorithm() == Algorithm.BOENNINGHAUSEN_POLARITY) {
                // Polarity mode: rank by Polarity Difference (Delta P) descending, then symptoms
                int cmp = Integer.compare(b.polarityDifference(), a.polarityDifference());
                if (cmp != 0) return cmp;
                return Integer.compare(b.coveredSymptomsCount(), a.coveredSymptomsCount());
            } else if (options.algorithm() == Algorithm.QUALITATIVE_SYMPTOM_COUNT) {
                int cmp = Integer.compare(b.coveredSymptomsCount(), a.coveredSymptomsCount());
                if (cmp != 0) return cmp;
                return Double.compare(b.totalScore(), a.totalScore());
            } else {
                // Classical Kentian: Symptoms count descending, then total score descending
                int cmp = Integer.compare(b.coveredSymptomsCount(), a.coveredSymptomsCount());
                if (cmp != 0) return cmp;
                return Double.compare(b.totalScore(), a.totalScore());
            }
        });

        return new RepertorizationResult(
                scores,
                fullItems.size(),
                !eliminativeRubricIds.isEmpty(),
                options.algorithm() == Algorithm.BOENNINGHAUSEN_POLARITY
        );
    }

    private record FullRubricItem(
            Rubric rubric,
            double userWeight,
            boolean isEliminative
    ) {}

    private static class RemedyAccumulator {
        final Remedy remedy;
        int coveredCount = 0;
        double totalWeightedScore = 0.0;
        int positivePolarDegrees = 0;
        int contraindicatedPolarDegrees = 0;
        int contraindicationCount = 0;
        boolean isContraindicated = false;
        final Map<String, Integer> rubricGrades = new HashMap<>();

        RemedyAccumulator(Remedy remedy) {
            this.remedy = remedy;
        }
    }
}
