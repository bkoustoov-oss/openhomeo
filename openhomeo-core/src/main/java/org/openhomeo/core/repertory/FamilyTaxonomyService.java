package org.openhomeo.core.repertory;

import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.FamilyTaxonomy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;

/**
 * Service providing taxonomical hierarchy resolution, kingdom/family grouping,
 * and distribution calculation across repertorization results.
 */
public class FamilyTaxonomyService {
    private static final Logger log = LoggerFactory.getLogger(FamilyTaxonomyService.class);

    private final MasterKnowledgeDao masterDao;
    private final Map<String, FamilyTaxonomy> cache = new HashMap<>();
    private boolean isLoaded = false;

    public record GroupCount(String name, int count, double percentage) {}

    public record FamilyDistribution(
            int totalRemediesEvaluated,
            List<GroupCount> kingdomDistribution,
            List<GroupCount> familyOrStageDistribution,
            List<GroupCount> miasmDistribution
    ) {}

    public FamilyTaxonomyService(MasterKnowledgeDao masterDao) {
        this.masterDao = masterDao;
    }

    public synchronized void reload() {
        cache.clear();
        try {
            List<FamilyTaxonomy> list = masterDao.getAllFamilies();
            for (FamilyTaxonomy ft : list) {
                cache.put(ft.remedyId().toLowerCase(), ft);
            }
            isLoaded = true;
            log.info("FamilyTaxonomyService indexed {} taxonomical entries.", cache.size());
        } catch (SQLException e) {
            log.error("Failed to load family taxonomies", e);
        }
    }

    private void ensureLoaded() {
        if (!isLoaded) {
            reload();
        }
    }

    public Optional<FamilyTaxonomy> getTaxonomy(String remedyId) {
        if (remedyId == null) return Optional.empty();
        ensureLoaded();
        FamilyTaxonomy ft = cache.get(remedyId.toLowerCase());
        if (ft != null) return Optional.of(ft);

        // Fallback to database lookup if not in memory cache
        try {
            return masterDao.getFamilyTaxonomy(remedyId);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public List<FamilyTaxonomy> getAllTaxonomies() {
        ensureLoaded();
        return new ArrayList<>(cache.values());
    }

    /**
     * Computes the distribution breakdown of Kingdoms, Families/Stages, and Miasms
     * across a given list of candidate remedy abbreviations/IDs.
     */
    public FamilyDistribution calculateDistribution(List<String> remedyIds) {
        ensureLoaded();
        if (remedyIds == null || remedyIds.isEmpty()) {
            return new FamilyDistribution(0, List.of(), List.of(), List.of());
        }

        Map<String, Integer> kingdomMap = new HashMap<>();
        Map<String, Integer> familyMap = new HashMap<>();
        Map<String, Integer> miasmMap = new HashMap<>();

        int matchedCount = 0;
        for (String id : remedyIds) {
            String clean = id.toLowerCase().trim();
            FamilyTaxonomy ft = cache.get(clean);
            if (ft != null) {
                matchedCount++;
                kingdomMap.merge(ft.kingdom(), 1, Integer::sum);
                familyMap.merge(ft.familyOrStage(), 1, Integer::sum);
                if (ft.miasmPrimary() != null && !ft.miasmPrimary().isBlank()) {
                    miasmMap.merge(ft.miasmPrimary(), 1, Integer::sum);
                }
            }
        }

        final int total = matchedCount > 0 ? matchedCount : remedyIds.size();

        List<GroupCount> kingdoms = toSortedGroupCounts(kingdomMap, total);
        List<GroupCount> families = toSortedGroupCounts(familyMap, total);
        List<GroupCount> miasms = toSortedGroupCounts(miasmMap, total);

        return new FamilyDistribution(total, kingdoms, families, miasms);
    }

    private List<GroupCount> toSortedGroupCounts(Map<String, Integer> counts, int total) {
        List<GroupCount> list = new ArrayList<>();
        counts.forEach((name, count) -> {
            double pct = total > 0 ? (count * 100.0) / total : 0.0;
            list.add(new GroupCount(name, count, Math.round(pct * 10.0) / 10.0));
        });
        list.sort(Comparator.comparingInt(GroupCount::count).reversed());
        return list;
    }

    /**
     * Checks if a remedy matches a specified filter keyword (Kingdom, Family, or Stage).
     */
    public boolean matchesFilter(String remedyId, String filter) {
        if (filter == null || filter.equalsIgnoreCase("ALL") || filter.isBlank()) {
            return true;
        }
        Optional<FamilyTaxonomy> opt = getTaxonomy(remedyId);
        if (opt.isEmpty()) return false;
        FamilyTaxonomy ft = opt.get();
        String f = filter.toLowerCase().trim();
        return (ft.kingdom() != null && ft.kingdom().toLowerCase().contains(f)) ||
               (ft.familyOrStage() != null && ft.familyOrStage().toLowerCase().contains(f)) ||
               (ft.orderOrSeries() != null && ft.orderOrSeries().toLowerCase().contains(f)) ||
               (ft.miasmPrimary() != null && ft.miasmPrimary().toLowerCase().contains(f));
    }
}
