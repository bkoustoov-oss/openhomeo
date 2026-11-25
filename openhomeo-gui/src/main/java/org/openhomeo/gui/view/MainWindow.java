package org.openhomeo.gui.view;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.Rubric;
import org.openhomeo.core.model.SystemPatchRecord;
import org.openhomeo.core.model.User;
import org.openhomeo.core.net.DeploymentProfile;
import org.openhomeo.core.ota.OtaUpdateService;
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.openhomeo.core.security.SecurityService;
import org.openhomeo.core.server.MobileCompanionServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Primary Workstation Window Layout coordinating the multi-tab workspace,
 * global rubric search bar, and active symptom clipboards.
 */
public class MainWindow {
    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);

    private final Stage stage;
    private final MasterKnowledgeDao masterDao;
    private final ClinicDao clinicDao;
    private final SecurityService securityService;
    private final ImageVaultService imageVault;
    private final SymptomClipboard clipboard;
    private final RepertorizationEngine engine;
    private final Runnable onLogout;

    private final BorderPane root;
    private boolean isDarkMode = false;

    private RepertoryMatrixView matrixView;
    private CaseTakingView caseTakingView;
    private PatientDossierView patientDossierView;
    private AiClinicalLogicChatView aiChatView;
    private ClinicalImageViewer imageViewer;
    private MateriaMedicaView mmView;
    private TabPane mainTabPane;
    private Tab tabCase;
    private Tab tabDossier;
    private Tab tabAiChat;

    private TextField searchField;
    private ListView<String> searchResultsList;
    private TreeView<String> rubricTreeView;
    private HBox clipboardBadgeBox;
    private Button otaNotificationBtn;
    private MobileCompanionServer companionServer;

    public MainWindow(Stage stage,
                      MasterKnowledgeDao masterDao,
                      ClinicDao clinicDao,
                      SecurityService securityService,
                      ImageVaultService imageVault,
                      SymptomClipboard clipboard,
                      RepertorizationEngine engine) {
        this(stage, masterDao, clinicDao, securityService, imageVault, clipboard, engine, null);
    }

    public MainWindow(Stage stage,
                      MasterKnowledgeDao masterDao,
                      ClinicDao clinicDao,
                      SecurityService securityService,
                      ImageVaultService imageVault,
                      SymptomClipboard clipboard,
                      RepertorizationEngine engine,
                      Runnable onLogout) {
        this.stage = stage;
        this.masterDao = masterDao;
        this.clinicDao = clinicDao;
        this.securityService = securityService;
        this.imageVault = imageVault;
        this.clipboard = clipboard;
        this.engine = engine;
        this.onLogout = onLogout;

        this.root = new BorderPane();
        initializeUI();
    }

    public Parent getRoot() {
        return root;
    }

    private void initializeUI() {
        // 1. Central TabPane
        mainTabPane = new TabPane();
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: Repertory Explorer & Matrix
        matrixView = new RepertoryMatrixView(masterDao, clipboard, engine);
        BorderPane repPane = new BorderPane();

        // Left Repertory Browser Split
        SplitPane repSplit = new SplitPane();
        VBox leftNav = createRepertoryNav();
        repSplit.getItems().addAll(leftNav, matrixView);
        repSplit.setDividerPositions(0.24);
        repPane.setCenter(repSplit);

        Tab tabRep = new Tab("Repertory & Analysis Matrix", repPane);

        // Tab 2: Case-Taking & Prescriptions
        caseTakingView = new CaseTakingView(clinicDao, masterDao, clipboard, imageVault);
        tabCase = new Tab("Clinical Case-Taking (L-S-M-C)", caseTakingView);

        // Tab 3: Comprehensive Patient Records & 360° Dossier
        patientDossierView = new PatientDossierView(clinicDao, imageVault);
        tabDossier = new Tab("👥 Patient Records & 360° Dossier", patientDossierView);

        // Tab 4: Deterministic Classical Logic AI Chat
        aiChatView = new AiClinicalLogicChatView(clinicDao, masterDao);
        tabAiChat = new Tab("🧠 Clinical Logic AI Chat", aiChatView);

        // Cross-wiring callbacks for smooth practitioner navigation:
        patientDossierView.setOnConsultationRequested(patient -> {
            caseTakingView.selectPatientById(patient.id());
            mainTabPane.getSelectionModel().select(tabCase);
        });

        patientDossierView.setOnLogicAiChatRequested(patient -> {
            aiChatView.selectPatient(patient.id());
            mainTabPane.getSelectionModel().select(tabAiChat);
        });

        caseTakingView.setOnOpenDossierRequested(patient -> {
            patientDossierView.selectPatient(patient.id());
            mainTabPane.getSelectionModel().select(tabDossier);
        });

        caseTakingView.setOnOpenLogicAiRequested(patient -> {
            aiChatView.selectPatient(patient.id());
            mainTabPane.getSelectionModel().select(tabAiChat);
        });

        // Tab 5: Pharmacy Dispensary & Live Stock Register
        PharmacyDispensaryView pharmacyView = new PharmacyDispensaryView(clinicDao, masterDao, securityService);
        Tab tabPharmacy = new Tab("Pharmacy Dispensary & Stock Register", pharmacyView);

        // Tab 6: Clinical Photos & Color Analysis
        imageViewer = new ClinicalImageViewer(clinicDao, imageVault);
        Tab tabImage = new Tab("Clinical Photographs & CIE-LAB", imageViewer);

        // Tab 7: Materia Medica Library
        mmView = new MateriaMedicaView(masterDao, clinicDao);
        mmView.setOnConsultAiRequested(query -> {
            mainTabPane.getSelectionModel().select(tabAiChat);
            aiChatView.askDirectQuery(query);
        });
        Tab tabMM = new Tab("Materia Medica Library", mmView);

        mainTabPane.getTabs().addAll(tabRep, tabCase, tabDossier, tabAiChat, tabPharmacy, tabImage, tabMM);

        // Listen for tab changes to auto-refresh data across modules
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == tabPharmacy) {
                pharmacyView.refreshAllData();
            } else if (newTab == tabDossier) {
                patientDossierView.refreshPatientList();
            } else if (newTab == tabAiChat) {
                aiChatView.refreshPatientContextList();
            }
        });

        // Role-based initial tab selection & Admin Pane mounting
        if (securityService.getCurrentSession() != null) {
            User u = securityService.getCurrentSession().user();
            if (u.role() == User.UserRole.ADMIN) {
                AdminManagementView adminView = new AdminManagementView(clinicDao, securityService, this::checkOtaUpdatesAsync);
                Tab tabAdmin = new Tab("Admin Management", adminView);
                mainTabPane.getTabs().add(tabAdmin);
                mainTabPane.getSelectionModel().select(tabAdmin);
            } else if (u.role() == User.UserRole.PHARMACIST) {
                mainTabPane.getSelectionModel().select(tabPharmacy);
            } else if (u.role() == User.UserRole.DOCTOR || u.role() == User.UserRole.HOMEOPATH) {
                mainTabPane.getSelectionModel().select(tabCase);
            }
        }

        // Header Toolbar
        HBox header = createHeader();
        root.setTop(header);
        root.setCenter(mainTabPane);

        // Check for updates asynchronously on workstation launch
        checkOtaUpdatesAsync();
    }

    private HBox createHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.setStyle("-fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Label brand = new Label("OPENHOMEO");
        brand.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.EXTRA_BOLD, 16));
        brand.setTextFill(javafx.scene.paint.Color.web("#0284c7"));

        searchField = new TextField();
        searchField.setPromptText("Instant Symptom Search (e.g. 'anxiety evening', 'cold agg')...");
        searchField.setMinWidth(380);
        searchField.setOnAction(e -> executeSearch(searchField.getText()));

        Button searchBtn = new Button("Search");
        searchBtn.setOnAction(e -> executeSearch(searchField.getText()));

        // Active Clipboard Buttons (1 to 5)
        clipboardBadgeBox = new HBox(6);
        clipboardBadgeBox.setAlignment(Pos.CENTER_LEFT);
        refreshClipboardBadges();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button themeToggle = new Button("Theme");
        themeToggle.getStyleClass().add(Styles.BUTTON_CIRCLE);
        themeToggle.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            if (isDarkMode) {
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            } else {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            }
        });

        Label networkModePill = new Label();
        DatabaseManager dm = clinicDao != null ? clinicDao.getDatabaseManager() : null;
        if (dm != null) {
            DeploymentProfile prof = dm.getDeploymentProfile();
            if (prof.mode() == DeploymentProfile.DeploymentMode.SERVER) {
                String lanIp = MobileCompanionServer.getLocalLanIp();
                networkModePill.setText("🟢 Clinic Server " + lanIp + ":" + prof.serverPort());
                networkModePill.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-border-color: #86efac; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 4 8; -fx-font-weight: bold; -fx-font-size: 11px;");
                networkModePill.setTooltip(new Tooltip("Clinic Database Server Host ACTIVE\nLAN Address: " + lanIp + ":" + prof.serverPort() + "\nPairing Key: " + prof.pairingKey()));
            } else if (prof.mode() == DeploymentProfile.DeploymentMode.CLIENT) {
                networkModePill.setText("🌐 Client → " + prof.serverHost() + ":" + prof.serverPort());
                networkModePill.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1d4ed8; -fx-border-color: #93c5fd; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 4 8; -fx-font-weight: bold; -fx-font-size: 11px;");
                networkModePill.setTooltip(new Tooltip("Client Terminal connected directly to Shared Clinic Server DB at " + prof.getBaseUrl()));
            } else {
                networkModePill.setText("🖥️ Standalone");
                networkModePill.setStyle("-fx-background-color: #f8fafc; -fx-text-fill: #475569; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 4 8; -fx-font-size: 11px;");
                networkModePill.setTooltip(new Tooltip("Single-PC Local Clinic Database"));
            }
        } else {
            networkModePill.setVisible(false);
            networkModePill.setManaged(false);
        }

        String userDisplay = securityService.getCurrentSession() != null
                ? "[" + securityService.getCurrentSession().user().role().name() + "] " + securityService.getCurrentSession().user().fullName()
                : "Practitioner";
        Label userLabel = new Label(userDisplay);
        userLabel.getStyleClass().add(Styles.TEXT_BOLD);
        userLabel.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-padding: 4 8 4 8; -fx-background-radius: 6px;");

        Button navDossierBtn = new Button("👥 Patient Dossier");
        navDossierBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        navDossierBtn.setTooltip(new Tooltip("Jump directly to Patient Master Records, Longitudinal Dossier & In-App Prescriptions"));
        navDossierBtn.setOnAction(e -> {
            if (tabDossier != null && mainTabPane != null) {
                mainTabPane.getSelectionModel().select(tabDossier);
            }
        });

        Button navAiBtn = new Button("🧠 Logic AI");
        navAiBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);
        navAiBtn.setTooltip(new Tooltip("Open Classical Homeopathic Logic AI Reasoner Chat"));
        navAiBtn.setOnAction(e -> {
            if (tabAiChat != null && mainTabPane != null) {
                mainTabPane.getSelectionModel().select(tabAiChat);
            }
        });

        Button companionBtn = new Button("📱 Bedside Companion");
        companionBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        companionBtn.setTooltip(new Tooltip("Launch Bedside Mobile & Tablet Companion Web Service on Local Wi-Fi"));
        companionBtn.setOnAction(e -> showCompanionServerDialog());

        Button feedbackBtn = new Button("💬 Community & Issues");
        feedbackBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        feedbackBtn.setTooltip(new Tooltip("OpenHomeo GitHub Issues, Bug Reports & Community Discussions"));
        feedbackBtn.setOnAction(e -> showCommunityFeedbackDialog());

        Button lockBtn = new Button("Lock / Switch");
        lockBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        lockBtn.setTooltip(new Tooltip("Lock session and switch user (Admin / Doctor / Pharmacist)"));
        lockBtn.setOnAction(e -> {
            securityService.clearSession();
            if (onLogout != null) {
                onLogout.run();
            }
        });

        // Global Red Update Notification Button (unmissable across all user workstations)
        otaNotificationBtn = new Button("⏳ Checking Updates...");
        otaNotificationBtn.setVisible(false);
        otaNotificationBtn.setManaged(false);

        header.getChildren().addAll(brand, new Separator(), searchField, searchBtn, new Separator(), clipboardBadgeBox, spacer, otaNotificationBtn, navDossierBtn, navAiBtn, companionBtn, feedbackBtn, networkModePill, userLabel, themeToggle, lockBtn);
        return header;
    }

    private OtaUpdateService createOtaService() {
        DatabaseManager dm = clinicDao != null ? clinicDao.getDatabaseManager() : null;
        if (dm != null) {
            return new OtaUpdateService(dm.getMasterDbPath(), dm.getClinicDbPath(), clinicDao);
        }
        String base = System.getProperty("user.home") + java.io.File.separator + ".openhomeo";
        return new OtaUpdateService(base + java.io.File.separator + "master_repertory.db", base + java.io.File.separator + "user_clinic.db", clinicDao);
    }

    public void checkOtaUpdatesAsync() {
        Task<OtaUpdateService.UpdateInfo> task = new Task<>() {
            @Override
            protected OtaUpdateService.UpdateInfo call() {
                return createOtaService().checkForUpdates();
            }
        };

        task.setOnSucceeded(e -> {
            OtaUpdateService.UpdateInfo info = task.getValue();
            Platform.runLater(() -> renderOtaNotificationBadge(info));
        });

        task.setOnFailed(e -> {
            log.warn("Background OTA update check error: {}", task.getException().getMessage());
        });

        Thread t = new Thread(task, "openhomeo-header-ota-check");
        t.setDaemon(true);
        t.start();
    }

    private void renderOtaNotificationBadge(OtaUpdateService.UpdateInfo info) {
        if (otaNotificationBtn == null || info == null) return;

        OtaUpdateService ota = createOtaService();
        Optional<SystemPatchRecord> latestPatch = ota.getLatestAppliedPatch();
        boolean isApplied = latestPatch.isPresent() && latestPatch.get().version().trim().equalsIgnoreCase(info.latestVersion().trim());

        if (info.updateAvailable() && !isApplied) {
            // PROMINENT RED NOTIFICATION FOR INSTALLED USERS WITH COMMENTED DETAILS
            String shortVer = info.latestVersion().split(" ")[0].replace("-Diamond+", "");
            otaNotificationBtn.setText("🔴 UPDATE AVAILABLE: " + shortVer + " (Originated: " + info.releaseDate() + ")");
            otaNotificationBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-border-color: #ef4444; -fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 4 10;");
            otaNotificationBtn.setTooltip(new Tooltip("OVER-THE-AIR WORKSTATION UPDATE AVAILABLE!\nVersion: " + info.latestVersion() + "\nOriginated: " + info.releaseDate() + "\nClick to view commented details and apply update."));
            otaNotificationBtn.setOnAction(evt -> showUpdateAvailableModal(info));
            otaNotificationBtn.setVisible(true);
            otaNotificationBtn.setManaged(true);
        } else if (latestPatch.isPresent()) {
            // SYSTEM ACTIVE & UP TO DATE WITH LOCAL TIME
            SystemPatchRecord rec = latestPatch.get();
            String localTime = OtaUpdateService.formatLocalTimestamp(rec.appliedAt());
            String shortVer = rec.version().split(" ")[0].replace("-Diamond+", "");
            otaNotificationBtn.setText("✓ v" + shortVer + " Active (" + localTime + ")");
            otaNotificationBtn.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-border-color: #86efac; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 10;");
            otaNotificationBtn.setTooltip(new Tooltip("Workstation is up-to-date with latest verified release.\nInstalled Version: " + rec.version() + "\nOriginated: " + rec.releaseDate() + "\nApplied Locally: " + localTime + "\nClick to view active patch details & changelog."));
            otaNotificationBtn.setOnAction(evt -> showPatchDetailsModal(info, rec));
            otaNotificationBtn.setVisible(true);
            otaNotificationBtn.setManaged(true);
        } else {
            otaNotificationBtn.setText("✓ Workstation Up-to-Date");
            otaNotificationBtn.setStyle("-fx-background-color: #f8fafc; -fx-text-fill: #475569; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 4 10;");
            otaNotificationBtn.setVisible(true);
            otaNotificationBtn.setManaged(true);
        }
    }

    private void showUpdateAvailableModal(OtaUpdateService.UpdateInfo info) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("OpenHomeo Over-The-Air Update Available");
        dialog.setHeaderText("🔴 OVER-THE-AIR WORKSTATION & KNOWLEDGE BASE UPDATE");

        VBox box = new VBox(12);
        box.setPadding(new Insets(14));
        box.setPrefWidth(680);

        HBox banner = new HBox(10);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #f87171; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10 14;");
        Label redDot = new Label("🔴");
        redDot.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        VBox bannerTexts = new VBox(2);
        Label bannerTitle = new Label("New Official Upgrade Available: " + info.latestVersion());
        bannerTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        bannerTitle.setStyle("-fx-text-fill: #991b1b;");

        String localTimeNow = ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        Label bannerMeta = new Label("Originated / Release Date: " + info.releaseDate() + "  |  Local Workstation Time: " + localTimeNow + "  |  Catalog: " + info.totalRemedies() + " Remedies");
        bannerMeta.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f1d1d; -fx-font-weight: bold;");
        bannerTexts.getChildren().addAll(bannerTitle, bannerMeta);
        banner.getChildren().addAll(redDot, bannerTexts);

        Label detailsTitle = new Label("📋 What Are The Changes & Commented Details in this Update:");
        detailsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        TextArea changelogArea = new TextArea(info.changelog());
        changelogArea.setEditable(false);
        changelogArea.setWrapText(true);
        changelogArea.setPrefRowCount(10);
        changelogArea.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-control-inner-background: #ffffff; -fx-text-fill: #1e293b;");

        Label safetyLbl = new Label("🛡️ 100% Non-Destructive Update Guarantee:\nYour local patient records, consultation histories, encrypted clinical photographs, prescriptions, and billing data in user_clinic.db are STRICTLY PRESERVED and will NEVER be touched or modified.");
        safetyLbl.setWrapText(true);
        safetyLbl.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-padding: 10 14; -fx-border-color: #86efac; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 11px;");

        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        ProgressBar pBar = new ProgressBar();
        pBar.setVisible(false);
        pBar.setPrefWidth(200);

        Label pStatus = new Label();
        pStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #0284c7;");

        boolean isAdmin = securityService.getCurrentSession() != null
                && securityService.getCurrentSession().user().role() == User.UserRole.ADMIN;

        if (isAdmin) {
            Button btnApply = new Button("🚀 Download & Apply Live Update Now (Automated)");
            btnApply.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
            btnApply.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            btnApply.setOnAction(evt -> {
                btnApply.setDisable(true);
                pBar.setVisible(true);
                pBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                pStatus.setText("Applying live update...");

                String currentUser = securityService.getCurrentSession().user().username();
                OtaUpdateService ota = createOtaService();

                Task<OtaUpdateService.ApplyResult> applyTask = new Task<>() {
                    @Override
                    protected OtaUpdateService.ApplyResult call() {
                        return ota.applyLiveUpdateAutomated(msg -> {
                            Platform.runLater(() -> pStatus.setText(msg.replaceAll("\\[\\d/\\d\\] ", "")));
                        }, currentUser);
                    }
                };
                applyTask.setOnSucceeded(e -> {
                    pBar.setVisible(false);
                    OtaUpdateService.ApplyResult res = applyTask.getValue();
                    if (res.success()) {
                        dialog.close();
                        checkOtaUpdatesAsync();

                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("System Update Applied Successfully");
                        successAlert.setHeaderText("🎉 OpenHomeo " + res.version() + " Installed");

                        String appliedLocalTime = OtaUpdateService.formatLocalTimestamp(Instant.now());
                        successAlert.setContentText(
                                "The automated live update has been staged & verified cleanly.\n\n" +
                                "• Patch Release / Originated Date: " + info.releaseDate() + "\n" +
                                "• Applied Date & Local Timestamp: " + appliedLocalTime + "\n\n" +
                                "• Activated Changes & Commented Enhancements:\n" + info.changelog() + "\n\n" +
                                "• 🛡️ Non-Destructive Invariant Confirmed: All patient cases and prescriptions in user_clinic.db are 100% safe and intact.\n" +
                                "• Verified SHA-256 Checksum: " + res.hash() + "\n\n" +
                                (res.restartRequired()
                                        ? "Click OK to automatically swap the updated application binaries cleanly and relaunch OpenHomeo."
                                        : "A permanent client audit entry with your local workstation timestamp (" + appliedLocalTime + ") has been recorded.")
                        );
                        if (res.restartRequired() && res.updaterScriptPath() != null) {
                            ButtonType btnRestartNow = new ButtonType("🔄 Restart & Complete Upgrade", ButtonBar.ButtonData.OK_DONE);
                            ButtonType btnLater = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
                            successAlert.getButtonTypes().setAll(btnRestartNow, btnLater);
                            successAlert.showAndWait().ifPresent(bt -> {
                                if (bt == btnRestartNow && OtaUpdateService.launchStagedUpdater(res.updaterScriptPath())) {
                                    Platform.exit();
                                    System.exit(0);
                                }
                            });
                        } else {
                            successAlert.showAndWait();
                        }
                    } else {
                        btnApply.setDisable(false);
                        pStatus.setText("Update failed: " + res.message());
                    }
                });
                applyTask.setOnFailed(e -> {
                    btnApply.setDisable(false);
                    pBar.setVisible(false);
                    pStatus.setText("Update failed: " + applyTask.getException().getMessage());
                });
                new Thread(applyTask, "openhomeo-ota-modal-apply").start();
            });
            actionBox.getChildren().addAll(btnApply, pBar, pStatus);
        } else {
            Label clinicianNote = new Label("🔒 Clinician Account Note: Only Administrator accounts can execute system updates.\nPlease notify your clinic administrator, or log in as Admin to apply.");
            clinicianNote.setStyle("-fx-text-fill: #9a3412; -fx-font-weight: bold; -fx-font-size: 11px;");
            actionBox.getChildren().add(clinicianNote);
        }

        box.getChildren().addAll(banner, detailsTitle, changelogArea, safetyLbl, actionBox);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showPatchDetailsModal(OtaUpdateService.UpdateInfo info, SystemPatchRecord rec) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Active System Patch Details");
        dialog.setHeaderText("OpenHomeo Workstation - Active System & Knowledge Patch");

        VBox box = new VBox(12);
        box.setPadding(new Insets(14));
        box.setPrefWidth(680);

        HBox banner = new HBox(10);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #86efac; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10 14;");
        Label checkIcon = new Label("✓");
        checkIcon.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        checkIcon.setStyle("-fx-text-fill: #166534;");

        VBox bannerTexts = new VBox(2);
        Label bannerTitle = new Label("Active Installed Version: " + rec.version());
        bannerTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        bannerTitle.setStyle("-fx-text-fill: #166534;");

        String localApplied = OtaUpdateService.formatLocalTimestamp(rec.appliedAt());
        Label bannerMeta = new Label("Originated / Release Date: " + rec.releaseDate() + "  |  Applied Locally: " + localApplied + "  |  Operator: " + rec.appliedBy());
        bannerMeta.setStyle("-fx-font-size: 11px; -fx-text-fill: #14532d; -fx-font-weight: bold;");
        bannerTexts.getChildren().addAll(bannerTitle, bannerMeta);
        banner.getChildren().addAll(checkIcon, bannerTexts);

        Label detailsTitle = new Label("📋 Active Features & Commented Improvements in this Release:");
        detailsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        TextArea changelogArea = new TextArea(info != null ? info.changelog() : rec.changelogSummary());
        changelogArea.setEditable(false);
        changelogArea.setWrapText(true);
        changelogArea.setPrefRowCount(9);
        changelogArea.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-control-inner-background: #ffffff; -fx-text-fill: #1e293b;");

        GridPane auditGrid = new GridPane();
        auditGrid.setHgap(10);
        auditGrid.setVgap(4);
        auditGrid.setStyle("-fx-background-color: #f8fafc; -fx-padding: 8 12; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

        auditGrid.add(new Label("Integrity Status:"), 0, 0);
        Label lblStatus = new Label(rec.status());
        lblStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #166534;");
        auditGrid.add(lblStatus, 1, 0);

        auditGrid.add(new Label("SHA-256 Checksum:"), 0, 1);
        Label lblHash = new Label(rec.repertoryHash());
        lblHash.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        auditGrid.add(lblHash, 1, 1);

        auditGrid.add(new Label("Local Applied Time:"), 0, 2);
        Label lblTime = new Label(localApplied);
        lblTime.setStyle("-fx-font-weight: bold;");
        auditGrid.add(lblTime, 1, 2);

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        Button btnCheckAgain = new Button("🔍 Check for Newer Updates");
        btnCheckAgain.getStyleClass().add(Styles.BUTTON_OUTLINED);
        btnCheckAgain.setOnAction(evt -> {
            dialog.close();
            checkOtaUpdatesAsync();
        });
        btnRow.getChildren().add(btnCheckAgain);

        box.getChildren().addAll(banner, detailsTitle, changelogArea, auditGrid, btnRow);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showCommunityFeedbackDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("OpenHomeo Community & Issue Tracker");
        dialog.setHeaderText("💬 Community Feedback, Bug Reports & Clinical Suggestions");

        VBox box = new VBox(14);
        box.setPadding(new Insets(16));
        box.setPrefWidth(520);

        Label desc = new Label(
                "OpenHomeo is developed transparently on GitHub as 100% Free Open-Source Software. " +
                "Practitioners, colleges, and researchers can submit clinical bug reports, request feature modifications, " +
                "or debate classical remedies in GitHub Discussions."
        );
        desc.setWrapText(true);

        Button btnBug = new Button("🐞 Report a Clinical / Software Bug");
        btnBug.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
        btnBug.setMaxWidth(Double.MAX_VALUE);
        btnBug.setOnAction(e -> openUrl("https://github.com/bkoustoov-oss/openhomeo/issues/new?template=bug_report.md"));

        Button btnFeature = new Button("💡 Suggest a Feature or Workflow Modification");
        btnFeature.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnFeature.setMaxWidth(Double.MAX_VALUE);
        btnFeature.setOnAction(e -> openUrl("https://github.com/bkoustoov-oss/openhomeo/issues/new?template=feature_request.md"));

        Button btnProving = new Button("🧪 Submit Proving Data / Materia Medica Monograph");
        btnProving.getStyleClass().addAll(Styles.BUTTON_OUTLINED);
        btnProving.setMaxWidth(Double.MAX_VALUE);
        btnProving.setOnAction(e -> openUrl("https://github.com/bkoustoov-oss/openhomeo/issues/new?template=proving_submission.md"));

        Button btnDiscussions = new Button("💬 Join GitHub Community Discussions");
        btnDiscussions.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);
        btnDiscussions.setMaxWidth(Double.MAX_VALUE);
        btnDiscussions.setOnAction(e -> openUrl("https://github.com/bkoustoov-oss/openhomeo/discussions"));

        Button btnViewIssues = new Button("📋 View All Community Issues & Reports");
        btnViewIssues.getStyleClass().add(Styles.BUTTON_OUTLINED);
        btnViewIssues.setMaxWidth(Double.MAX_VALUE);
        btnViewIssues.setOnAction(e -> openUrl("https://github.com/bkoustoov-oss/openhomeo/issues"));

        box.getChildren().addAll(
                desc, new Separator(),
                btnBug, btnFeature, btnProving, btnDiscussions, btnViewIssues
        );

        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                // Fallback on Windows
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            }
        } catch (Exception ex) {
            log.error("Failed to open URL: " + url, ex);
        }
    }

    private void showCompanionServerDialog() {
        if (companionServer == null) {
            companionServer = new MobileCompanionServer(clinicDao, masterDao, 8090);
            try {
                companionServer.start();
            } catch (Exception ex) {
                log.error("Failed to start Mobile Companion Server", ex);
            }
        }

        String localIp = "127.0.0.1";
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Mobile / Tablet Bedside Companion");
        alert.setHeaderText("Bedside Companion Web Service Active (Port 8090)");
        alert.setContentText(String.format(
                "Bedside companion web app is running.\n\n" +
                "• Local URL: http://localhost:8090/\n" +
                "• Wi-Fi / Tablet URL: http://%s:8090/\n\n" +
                "Open this URL on any iPad, Android tablet, or smartphone connected to the clinic's local Wi-Fi.\n" +
                "Physicians can take bedside patient case notes, review materia medica, and examine symptoms seamlessly.",
                localIp
        ));
        alert.showAndWait();
    }

    private void refreshClipboardBadges() {
        clipboardBadgeBox.getChildren().clear();
        clipboardBadgeBox.getChildren().add(new Label("Clipboard:"));

        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            int count = clipboard.getItems(idx).size();
            Button b = new Button("C" + i + (count > 0 ? " (" + count + ")" : ""));
            if (clipboard.getActiveClipboardIndex() == idx) {
                b.getStyleClass().addAll(Styles.ACCENT);
            } else {
                b.getStyleClass().add(Styles.BUTTON_OUTLINED);
            }
            b.setOnAction(e -> {
                clipboard.setActiveClipboardIndex(idx);
                refreshClipboardBadges();
                matrixView.refreshMatrix();
            });
            clipboardBadgeBox.getChildren().add(b);
        }
    }

    private VBox createRepertoryNav() {
        VBox nav = new VBox(8);
        nav.setPadding(new Insets(10));
        nav.setMinWidth(260);

        Label title = new Label("Repertory Chapters");
        title.getStyleClass().add(Styles.TITLE_4);

        ComboBox<String> chapterCombo = new ComboBox<>();
        chapterCombo.setMinWidth(240);

        searchResultsList = new ListView<>();
        searchResultsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String sel = searchResultsList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    addRubricByPath(sel);
                }
            }
        });

        try {
            List<String> chapters = masterDao.getAllChapters();
            chapterCombo.getItems().addAll(chapters);
            if (!chapters.isEmpty()) {
                chapterCombo.getSelectionModel().select(0);
                loadChapterRubrics(chapterCombo.getValue());
            }
            chapterCombo.setOnAction(e -> loadChapterRubrics(chapterCombo.getValue()));
        } catch (Exception e) {
            log.error("Failed to load chapters", e);
        }

        Label hint = new Label("Double-click rubric to add to active clipboard");
        hint.getStyleClass().add(Styles.TEXT_MUTED);

        nav.getChildren().addAll(title, chapterCombo, hint, searchResultsList);
        VBox.setVgrow(searchResultsList, Priority.ALWAYS);
        return nav;
    }

    private void loadChapterRubrics(String chapter) {
        if (chapter == null) return;
        searchResultsList.getItems().clear();
        try {
            List<Rubric> rubrics = masterDao.getRubricTree(chapter);
            for (Rubric r : rubrics) {
                searchResultsList.getItems().add(r.fullPath());
            }
        } catch (Exception e) {
            log.error("Failed to load rubrics for chapter", e);
        }
    }

    private void executeSearch(String query) {
        if (query == null || query.isBlank()) return;
        searchResultsList.getItems().clear();
        try {
            List<Rubric> hits = masterDao.searchRubricsFts(query);
            for (Rubric r : hits) {
                searchResultsList.getItems().add(r.fullPath());
            }
        } catch (Exception e) {
            log.error("Search failed", e);
        }
    }

    private void addRubricByPath(String fullPath) {
        try {
            List<Rubric> hits = masterDao.searchRubricsFts(fullPath.replace("-", " "));
            for (Rubric r : hits) {
                if (r.fullPath().equalsIgnoreCase(fullPath)) {
                    clipboard.addRubric(r);
                    refreshClipboardBadges();
                    matrixView.refreshMatrix();
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Failed to add rubric to clipboard", e);
        }
    }
}
