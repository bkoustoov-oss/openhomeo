package org.openhomeo.gui.view;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.Rubric;
import org.openhomeo.core.model.User;
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.openhomeo.core.security.SecurityService;
import org.openhomeo.core.server.MobileCompanionServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.net.InetAddress;
import java.util.List;

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
                AdminManagementView adminView = new AdminManagementView(clinicDao, securityService);
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

        header.getChildren().addAll(brand, new Separator(), searchField, searchBtn, new Separator(), clipboardBadgeBox, spacer, navDossierBtn, navAiBtn, companionBtn, feedbackBtn, userLabel, themeToggle, lockBtn);
        return header;
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
