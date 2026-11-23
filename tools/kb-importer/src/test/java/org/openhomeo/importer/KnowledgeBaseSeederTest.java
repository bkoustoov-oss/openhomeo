package org.openhomeo.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class KnowledgeBaseSeederTest {

    @Test
    public void testKnowledgeBaseCompilationAndFts(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test_repertory.db").toFile();
        KnowledgeBaseSeeder.seed(dbFile.getAbsolutePath());

        assertTrue(dbFile.exists(), "Master repertory database file must exist");
        assertTrue(dbFile.length() > 0, "Database file must have non-zero size");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {

            // Check Remedies
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM remedies")) {
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertTrue(count >= 30, "Should have seeded remedies, got: " + count);
            }

            // Check Polarity Rubrics
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubrics WHERE is_polar = 1")) {
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertTrue(count >= 4, "Should have polar rubrics configured, got: " + count);
            }

            // Check FTS5
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubrics_fts WHERE rubrics_fts MATCH 'anxiety'")) {
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertTrue(count >= 1, "FTS5 should match 'anxiety', got: " + count);
            }
        }
    }
}
