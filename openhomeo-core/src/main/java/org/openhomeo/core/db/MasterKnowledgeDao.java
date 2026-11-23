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
