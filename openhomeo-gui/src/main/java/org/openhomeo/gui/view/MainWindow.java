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
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.openhomeo.core.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final BorderPane root;
    private boolean isDarkMode = false;

    private RepertoryMatrixView matrixView;
    private CaseTakingView caseTakingView;
    private ClinicalImageViewer imageViewer;
    private MateriaMedicaView mmView;

    private TextField searchField;
    private ListView<String> searchResultsList;
    private TreeView<String> rubricTreeView;
    private HBox clipboardBadgeBox;

    public MainWindow(Stage stage,
                      MasterKnowledgeDao masterDao,
                      ClinicDao clinicDao,
                      SecurityService securityService,
                      ImageVaultService imageVault,
                      SymptomClipboard clipboard,
                      RepertorizationEngine engine) {
        this.stage = stage;
        this.masterDao = masterDao;
        this.clinicDao = clinicDao;
        this.securityService = securityService;
        this.imageVault = imageVault;
        this.clipboard = clipboard;
        this.engine = engine;

        this.root = new BorderPane();
        initializeUI();
    }

    public Parent getRoot() {
        return root;
    }

    private void initializeUI() {
        // 1. Header Toolbar
        HBox header = createHeader();
        root.setTop(header);

        // 2. Central TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

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
        caseTakingView = new CaseTakingView(clinicDao, masterDao, clipboard);
        Tab tabCase = new Tab("Clinical Case-Taking (L-S-M-C)", caseTakingView);

        // Tab 3: Clinical Photos & Color Analysis
        imageViewer = new ClinicalImageViewer(clinicDao, imageVault);
        Tab tabImage = new Tab("Clinical Photographs & CIE-LAB", imageViewer);

        // Tab 4: Materia Medica Library
        mmView = new MateriaMedicaView(masterDao);
        Tab tabMM = new Tab("Materia Medica Library", mmView);

        tabPane.getTabs().addAll(tabRep, tabCase, tabImage, tabMM);
        root.setCenter(tabPane);
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
                ? securityService.getCurrentSession().user().fullName()
                : "Practitioner";
        Label userLabel = new Label(userDisplay);
        userLabel.getStyleClass().add(Styles.TEXT_BOLD);

        header.getChildren().addAll(brand, new Separator(), searchField, searchBtn, new Separator(), clipboardBadgeBox, spacer, userLabel, themeToggle);
        return header;
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
