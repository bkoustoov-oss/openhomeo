package org.openhomeo.gui.view;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.Rubric;
import org.openhomeo.core.model.User;
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.openhomeo.core.security.SecurityService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Test
    public void testLoginViewAndMainWindowLifecycle(@TempDir Path tempDir) throws Exception {
        if (!jfxStarted) return;

        DatabaseManager dbManager = new DatabaseManager(tempDir.toString());
        dbManager.initializeClinicSchema();
        ClinicDao clinicDao = new ClinicDao(dbManager);
        MasterKnowledgeDao masterDao = new MasterKnowledgeDao(dbManager);
        SecurityService securityService = new SecurityService();
        ImageVaultService imageVault = new ImageVaultService(tempDir.resolve("vault").toString());
        SymptomClipboard clipboard = new SymptomClipboard();
        RepertorizationEngine engine = new RepertorizationEngine();

        String passHash = securityService.hashPassword("testpass");
        User testUser = new User(UUID.randomUUID().toString(), "dr_test", passHash, "Dr. Test", User.UserRole.ADMIN, Instant.now());
        clinicDao.createUser(testUser);

        CountDownLatch testLatch = new CountDownLatch(1);
        AtomicBoolean loginSuccessTriggered = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                // 1. Instantiate LoginView
                LoginView loginView = new LoginView(clinicDao, securityService, user -> {
                    loginSuccessTriggered.set(true);
                });
                assertNotNull(loginView);
                assertFalse(loginView.getChildren().isEmpty());

                // 2. Instantiate MainWindow
                securityService.startSession(testUser);
                MainWindow mainWindow = new MainWindow(
                        new Stage(), masterDao, clinicDao, securityService,
                        imageVault, clipboard, engine, () -> {}
                );
                assertNotNull(mainWindow.getRoot());

                testLatch.countDown();
            } catch (Exception e) {
                fail(e);
            }
        });

        assertTrue(testLatch.await(5, TimeUnit.SECONDS), "LoginView and MainWindow should instantiate successfully on JavaFX thread");
    }
}
