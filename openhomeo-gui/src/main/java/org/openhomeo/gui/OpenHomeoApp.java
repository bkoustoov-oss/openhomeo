package org.openhomeo.gui;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.User;
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.openhomeo.core.security.SecurityService;
import org.openhomeo.gui.view.LoginView;
import org.openhomeo.gui.view.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

/**
 * OpenHomeo Native Desktop Workstation Application Entry Point.
 */
public class OpenHomeoApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(OpenHomeoApp.class);

    private DatabaseManager dbManager;
    private MasterKnowledgeDao masterDao;
    private ClinicDao clinicDao;
    private SecurityService securityService;
    private ImageVaultService imageVault;
    private SymptomClipboard symptomClipboard;
    private RepertorizationEngine repertorizationEngine;

    @Override
    public void init() throws Exception {
        log.info("Initializing OpenHomeo Native Desktop Workstation...");

        String appDataDir = System.getProperty("user.home") + File.separator + ".openhomeo";
        File dir = new File(appDataDir);
        if (!dir.exists()) dir.mkdirs();

        this.dbManager = new DatabaseManager(appDataDir);
        this.dbManager.initializeClinicSchema();

        this.masterDao = new MasterKnowledgeDao(dbManager);
        this.clinicDao = new ClinicDao(dbManager);
        this.securityService = new SecurityService();
        this.imageVault = new ImageVaultService(appDataDir + File.separator + "vault");
        this.symptomClipboard = new SymptomClipboard();
        this.repertorizationEngine = new RepertorizationEngine();

        // Ensure default practitioner user exists
        if (clinicDao.findUserByUsername("admin").isEmpty()) {
            String hash = securityService.hashPassword("admin123");
            clinicDao.createUser(new User(
                    UUID.randomUUID().toString(), "admin", hash, "Dr. Practitioner", User.UserRole.ADMIN, Instant.now()
            ));
            log.info("Created default administrator account: admin / admin123");
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // Set modern AtlantaFX theme
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        primaryStage.setTitle("OpenHomeo Workstation - Professional Edition");

        // Load application branding icon
        try {
            var iconStream = OpenHomeoApp.class.getResourceAsStream("/icons/openhomeo.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception e) {
            log.warn("Could not load application icon: {}", e.getMessage());
        }

        // Screen adaptive dimensions
        javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        double targetWidth = Math.min(1280, screenBounds.getWidth() * 0.95);
        double targetHeight = Math.min(850, screenBounds.getHeight() * 0.92);

        primaryStage.setWidth(targetWidth);
        primaryStage.setHeight(targetHeight);
        primaryStage.setMinWidth(Math.min(1050, targetWidth));
        primaryStage.setMinHeight(Math.min(700, targetHeight));

        showLoginScreen(primaryStage);

        // Center on screen
        primaryStage.centerOnScreen();

        // Bring to front over any active maximized windows
        primaryStage.setAlwaysOnTop(true);
        primaryStage.show();
        primaryStage.toFront();
        primaryStage.requestFocus();

        // Release topmost lock after presentation so window behaves normally
        javafx.application.Platform.runLater(() -> {
            primaryStage.setAlwaysOnTop(false);
            primaryStage.requestFocus();
        });

        log.info("OpenHomeo workstation window presented on desktop at [x={}, y={}, w={}, h={}, showing={}]",
                primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(), primaryStage.getHeight(), primaryStage.isShowing());
    }

    private void showLoginScreen(Stage primaryStage) {
        LoginView loginView = new LoginView(clinicDao, securityService, user -> {
            securityService.startSession(user);
            MainWindow mainWindow = new MainWindow(
                    primaryStage, masterDao, clinicDao, securityService,
                    imageVault, symptomClipboard, repertorizationEngine,
                    () -> showLoginScreen(primaryStage)
            );

            primaryStage.getScene().setRoot(mainWindow.getRoot());
            log.info("OpenHomeo workstation GUI unlocked and loaded main workspace.");
        });

        if (primaryStage.getScene() == null) {
            Scene scene = new Scene(loginView, 1280, 850);
            primaryStage.setScene(scene);
        } else {
            primaryStage.getScene().setRoot(loginView);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
