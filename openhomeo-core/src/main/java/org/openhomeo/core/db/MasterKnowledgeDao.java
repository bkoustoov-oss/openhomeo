package org.openhomeo.core.db;

import org.openhomeo.core.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Master Repertory, Materia Medica, and Remedy Knowledge Base.
 */
public class MasterKnowledgeDao {
    private final DatabaseManager dbManager;

    public MasterKnowledgeDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<Remedy> getAllRemedies() throws SQLException {
        List<Remedy> list = new ArrayList<>();
        String sql = "SELECT * FROM remedies ORDER BY full_name ASC";
        try (Connection conn = dbManager.getMasterConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRemedy(rs));
            }
        }
        return list;
    }

    public Optional<Remedy> getRemedyById(String id) throws SQLException {
        String sql = "SELECT * FROM remedies WHERE id = ?";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRemedy(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<RemedyRelationship> getRemedyRelationships(String remedyId) throws SQLException {
        List<RemedyRelationship> list = new ArrayList<>();
        String sql = "SELECT * FROM remedy_relationships WHERE remedy_id = ? OR related_remedy_id = ?";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, remedyId.toLowerCase());
            ps.setString(2, remedyId.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RemedyRelationship(
                            rs.getString("id"),
                            rs.getString("remedy_id"),
                            rs.getString("related_remedy_id"),
                            RemedyRelationship.RelationType.valueOf(rs.getString("relation_type")),
                            rs.getString("notes")
                    ));
                }
            }
        }
        return list;
    }

    public List<MateriaMedicaEntry> getMateriaMedica(String remedyId) throws SQLException {
        List<MateriaMedicaEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM materia_medica WHERE remedy_id = ? ORDER BY section ASC";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, remedyId.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MateriaMedicaEntry(
                            rs.getString("id"),
                            rs.getString("remedy_id"),
                            rs.getString("author"),
                            rs.getString("book_title"),
                            rs.getString("section"),
                            rs.getString("body_text")
                    ));
                }
            }
        }
        return list;
    }

    public List<Rubric> getRubricTree(String chapter) throws SQLException {
        List<Rubric> list = new ArrayList<>();
        String sql = "SELECT * FROM rubrics WHERE chapter = ? ORDER BY full_path ASC";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chapter.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRubric(rs));
                }
            }
        }
        return list;
    }

    public List<Rubric> searchRubricsFts(String query) throws SQLException {
        List<Rubric> list = new ArrayList<>();
        String sql = """
            SELECT r.* FROM rubrics r
            JOIN rubrics_fts f ON r.rowid = f.rowid
            WHERE rubrics_fts MATCH ?
            ORDER BY rank
            LIMIT 100;
        """;
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // Clean and format query for SQLite FTS5 (support prefix match)
            String sanitized = query.trim().replaceAll("[^a-zA-Z0-9\\s]", "");
            if (sanitized.isBlank()) return list;

            StringBuilder matchBuilder = new StringBuilder();
            for (String part : sanitized.split("\\s+")) {
                if (!part.isBlank()) {
                    matchBuilder.append(part).append("* ");
                }
            }

            ps.setString(1, matchBuilder.toString().trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRubric(rs));
                }
            }
        }
        return list;
    }

    public Rubric getRubricWithRemedies(String rubricId) throws SQLException {
        String sqlRubric = "SELECT * FROM rubrics WHERE id = ?";
        String sqlRemedies = "SELECT * FROM rubric_remedies WHERE rubric_id = ? ORDER BY grade DESC";

        try (Connection conn = dbManager.getMasterConnection()) {
            Rubric rubric = null;
            try (PreparedStatement ps = conn.prepareStatement(sqlRubric)) {
                ps.setString(1, rubricId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rubric = mapRubric(rs);
                    }
                }
            }

            if (rubric == null) return null;

            List<RubricRemedy> remedies = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlRemedies)) {
                ps.setString(1, rubricId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        remedies.add(new RubricRemedy(
                                rs.getString("rubric_id"),
                                rs.getString("remedy_id"),
                                rs.getInt("grade"),
                                rs.getString("author_citation")
                        ));
                    }
                }
            }
            return rubric.withRemedies(remedies);
        }
    }

    public List<String> getAllChapters() throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT chapter FROM rubrics ORDER BY chapter ASC";
        try (Connection conn = dbManager.getMasterConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        }
        return list;
    }

    // =========================================================================
    // FAMILIES TAXONOMY (KINGDOMS, APG IV, SCHOLTEN PERIODIC TABLE, MIASMS)
    // =========================================================================

    public List<FamilyTaxonomy> getAllFamilies() throws SQLException {
        List<FamilyTaxonomy> list = new ArrayList<>();
        String sql = "SELECT * FROM remedy_families ORDER BY kingdom ASC, family_or_stage ASC, remedy_name ASC";
        try (Connection conn = dbManager.getMasterConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapFamily(rs));
            }
        }
        return list;
    }

    public Optional<FamilyTaxonomy> getFamilyTaxonomy(String remedyId) throws SQLException {
        if (remedyId == null) return Optional.empty();
        String sql = "SELECT * FROM remedy_families WHERE LOWER(remedy_id) = ? OR LOWER(remedy_name) LIKE ? LIMIT 1";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, remedyId.toLowerCase().trim());
            ps.setString(2, "%" + remedyId.toLowerCase().trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapFamily(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<FamilyTaxonomy> getFamiliesByKingdom(String kingdom) throws SQLException {
        List<FamilyTaxonomy> list = new ArrayList<>();
        String sql = "SELECT * FROM remedy_families WHERE LOWER(kingdom) = ? ORDER BY family_or_stage ASC, remedy_name ASC";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, kingdom.toLowerCase().trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFamily(rs));
                }
            }
        }
        return list;
    }

    private FamilyTaxonomy mapFamily(ResultSet rs) throws SQLException {
        return new FamilyTaxonomy(
                rs.getString("remedy_id"),
                rs.getString("remedy_name"),
                rs.getString("kingdom"),
                rs.getString("sub_kingdom"),
                rs.getString("order_or_series"),
                rs.getString("family_or_stage"),
                rs.getString("miasm_primary"),
                rs.getString("miasm_secondary"),
                rs.getString("sensation_theme"),
                rs.getString("clinical_keynote")
        );
    }

    // =========================================================================
    // SYNTHESIS RUBRIC ADDITIONS & CROSS-REFERENCES
    // =========================================================================

    public List<RubricRemedyAddition> getRubricAdditions(String rubricId) throws SQLException {
        List<RubricRemedyAddition> list = new ArrayList<>();
        String sql = "SELECT * FROM rubric_remedy_additions WHERE rubric_id = ? ORDER BY degree DESC, author_code ASC";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rubricId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RubricRemedyAddition(
                            rs.getString("rubric_id"),
                            rs.getString("remedy_id"),
                            rs.getInt("degree"),
                            rs.getString("author_code"),
                            rs.getString("author_name"),
                            rs.getString("citation_ref"),
                            rs.getBoolean("is_verified")
                    ));
                }
            }
        }
        return list;
    }

    public List<RubricCrossReference> getRubricCrossReferences(String rubricId) throws SQLException {
        List<RubricCrossReference> list = new ArrayList<>();
        String sql = "SELECT * FROM rubric_cross_references WHERE source_rubric_id = ?";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rubricId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RubricCrossReference(
                            rs.getString("source_rubric_id"),
                            rs.getString("target_rubric_id"),
                            rs.getString("target_full_path"),
                            RubricCrossReference.RelationshipType.valueOf(rs.getString("relationship_type"))
                    ));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // MATERIA MEDICA DIGITAL LIBRARY (1,500-VOLUME SCOPE WITH FTS5)
    // =========================================================================

    public List<MateriaMedicaBook> getAllMateriaMedicaBooks() throws SQLException {
        List<MateriaMedicaBook> list = new ArrayList<>();
        String sql = "SELECT * FROM mm_books ORDER BY category ASC, author ASC, title ASC";
        try (Connection conn = dbManager.getMasterConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new MateriaMedicaBook(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("era"),
                        rs.getString("category"),
                        rs.getString("description"),
                        rs.getInt("total_sections")
                ));
            }
        }
        return list;
    }

    public List<MateriaMedicaSection> getSectionsForBook(String bookId) throws SQLException {
        List<MateriaMedicaSection> list = new ArrayList<>();
        String sql = "SELECT * FROM mm_library_sections WHERE book_id = ? ORDER BY section_heading ASC";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSection(rs));
                }
            }
        }
        return list;
    }

    public List<MateriaMedicaSection> getSectionsForRemedy(String remedyName) throws SQLException {
        List<MateriaMedicaSection> list = new ArrayList<>();
        String sql = "SELECT * FROM mm_library_sections WHERE LOWER(remedy_name) LIKE ? ORDER BY book_title ASC";
        try (Connection conn = dbManager.getMasterConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + remedyName.toLowerCase().trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSection(rs));
                }
            }
        }
        return list;
    }

    public List<MateriaMedicaSearchResult> searchMateriaMedicaFts(String query) throws SQLException {
        List<MateriaMedicaSearchResult> list = new ArrayList<>();
        if (query == null || query.trim().isBlank()) return list;

        String sanitized = query.trim().replaceAll("[^a-zA-Z0-9\\s]", " ").trim();
        if (sanitized.isBlank()) return list;

        StringBuilder ftsQuery = new StringBuilder();
        for (String w : sanitized.split("\\s+")) {
            if (!w.isBlank()) {
                ftsQuery.append(w).append("* ");
            }
        }

        // Try FTS5 MATCH first
        try (Connection conn = dbManager.getMasterConnection()) {
            String ftsSql = """
                SELECT f.book_id, b.title AS book_title, f.author, f.remedy_name, f.section_heading,
                       snippet(mm_library_fts, 4, '<b>', '</b>', '...', 25) AS snippet,
                       rank
                FROM mm_library_fts f
                LEFT JOIN mm_books b ON f.book_id = b.id
                WHERE mm_library_fts MATCH ?
                ORDER BY rank
                LIMIT 80;
            """;
            try (PreparedStatement ps = conn.prepareStatement(ftsSql)) {
                ps.setString(1, ftsQuery.toString().trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new MateriaMedicaSearchResult(
                                rs.getString("book_id"),
                                rs.getString("book_title") != null ? rs.getString("book_title") : rs.getString("book_id"),
                                rs.getString("author"),
                                rs.getString("remedy_name"),
                                rs.getString("section_heading"),
                                rs.getString("snippet"),
                                rs.getDouble("rank")
                        ));
                    }
                }
            } catch (SQLException ftsErr) {
                // Fallback to LIKE query on mm_library_sections if FTS MATCH throws syntax exception
                String likeSql = """
                    SELECT s.book_id, s.book_title, s.author, s.remedy_name, s.section_heading,
                           SUBSTR(s.body_text, 1, 200) || '...' AS snippet
                    FROM mm_library_sections s
                    WHERE s.body_text LIKE ? OR s.remedy_name LIKE ? OR s.section_heading LIKE ?
                    LIMIT 40;
                """;
                try (PreparedStatement psLike = conn.prepareStatement(likeSql)) {
                    String param = "%" + sanitized + "%";
                    psLike.setString(1, param);
                    psLike.setString(2, param);
                    psLike.setString(3, param);
                    try (ResultSet rs = psLike.executeQuery()) {
                        while (rs.next()) {
                            list.add(new MateriaMedicaSearchResult(
                                    rs.getString("book_id"),
                                    rs.getString("book_title"),
                                    rs.getString("author"),
                                    rs.getString("remedy_name"),
                                    rs.getString("section_heading"),
                                    rs.getString("snippet"),
                                    1.0
                            ));
                        }
                    }
                }
            }
        }
        return list;
    }

    private MateriaMedicaSection mapSection(ResultSet rs) throws SQLException {
        return new MateriaMedicaSection(
                rs.getString("id"),
                rs.getString("book_id"),
                rs.getString("book_title"),
                rs.getString("author"),
                rs.getString("remedy_name"),
                rs.getString("section_heading"),
                rs.getString("body_text"),
                rs.getString("page_ref")
        );
    }

    private Remedy mapRemedy(ResultSet rs) throws SQLException {
        Integer stage = rs.getObject("periodic_stage") != null ? rs.getInt("periodic_stage") : null;
        Integer series = rs.getObject("periodic_series") != null ? rs.getInt("periodic_series") : null;
        String thermalStr = rs.getString("thermal");
        String thirstStr = rs.getString("thirst");

        return new Remedy(
                rs.getString("id"),
                rs.getString("abbr"),
                rs.getString("full_name"),
                rs.getString("common_name"),
                rs.getString("kingdom"),
                rs.getString("family"),
                stage,
                series,
                rs.getString("miasm"),
                thermalStr != null ? Remedy.Thermal.valueOf(thermalStr) : Remedy.Thermal.UNKNOWN,
                thirstStr != null ? Remedy.Thirst.valueOf(thirstStr) : Remedy.Thirst.UNKNOWN,
                null
        );
    }

    private Rubric mapRubric(ResultSet rs) throws SQLException {
        return new Rubric(
                rs.getString("id"),
                rs.getString("chapter"),
                rs.getString("parent_id"),
                rs.getString("rubric_text"),
                rs.getString("full_path"),
                rs.getInt("depth"),
                rs.getBoolean("is_polar"),
                rs.getString("opposite_rubric_id"),
                List.of()
        );
    }
}
