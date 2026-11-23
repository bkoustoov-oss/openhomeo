package org.openhomeo.gui.view;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.Rubric;
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.SymptomClipboard;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class GuiComponentTest {

    private static boolean jfxStarted = false;

    @BeforeAll
    public static void initJfx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                jfxStarted = true;
                latch.countDown();
            });
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (IllegalStateException alreadyStarted) {
            jfxStarted = true;
        }
    }

    @Test
    public void testRepertoryMatrixViewComponents(@TempDir Path tempDir) throws Exception {
        if (!jfxStarted) return;

        DatabaseManager dbManager = new DatabaseManager(tempDir.toString());
        MasterKnowledgeDao masterDao = new MasterKnowledgeDao(dbManager);
        SymptomClipboard clipboard = new SymptomClipboard();
        RepertorizationEngine engine = new RepertorizationEngine();

        CountDownLatch testLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // Add test rubric to clipboard
                Rubric r = masterDao.getRubricWithRemedies("mind_anxiety");
                assertNotNull(r);
                clipboard.addRubric(r);

                RepertoryMatrixView matrixView = new RepertoryMatrixView(masterDao, clipboard, engine);
                assertNotNull(matrixView);
                assertNotNull(matrixView.getTop());
                assertNotNull(matrixView.getCenter());

                testLatch.countDown();
            } catch (Exception e) {
                fail(e);
            }
        });

        assertTrue(testLatch.await(5, TimeUnit.SECONDS), "RepertoryMatrixView should render on JavaFX thread");
    }
}
