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
import org.openhomeo.gui.view.LoginDialog;
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
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(750);

        // Show Login first
        LoginDialog loginDialog = new LoginDialog(clinicDao, securityService);
        loginDialog.showAndWait().ifPresentOrElse(user -> {
            securityService.startSession(user);
            MainWindow mainWindow = new MainWindow(
                    primaryStage, masterDao, clinicDao, securityService,
                    imageVault, symptomClipboard, repertorizationEngine
            );

            Scene scene = new Scene(mainWindow.getRoot(), 1280, 850);
            primaryStage.setScene(scene);
            primaryStage.show();
            log.info("OpenHomeo workstation GUI launched successfully.");
        }, () -> {
            log.info("User cancelled login. Exiting.");
            primaryStage.close();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
