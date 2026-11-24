package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes;
import org.openhomeo.core.model.MateriaMedicaBook;
import org.openhomeo.core.model.MateriaMedicaEntry;
import org.openhomeo.core.model.MateriaMedicaSearchResult;
import org.openhomeo.core.model.MateriaMedicaSection;
import org.openhomeo.core.model.MedicineConstituent;
import org.openhomeo.core.model.Remedy;
import org.openhomeo.core.model.RemedyRelationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classical Materia Medica Reader and Comparative Reference Viewer:
 * - Dynamic autosuggestion & instant search-as-you-type remedy choosing
 * - Independent Primary and Comparative remedy selection
 * - Synchronized side-by-side dual reading across chapters
 * - Structured Side-by-Side Differential Matrix / Comparison Table
 * - Automatic Clinical Relationship quick-pick chips (Gibson Miller / HPI)
 * - Classical Pedagogical Clinical Rhymes (E. B. Nash / Allen / Indian verses)
 * - Automatic Fallback to Pharmacopoeial & Phytochemical Monographs for 100% catalog coverage
 */
public class MateriaMedicaView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(MateriaMedicaView.class);

    private final MasterKnowledgeDao masterDao;
    private final ClinicDao clinicDao;

    private final List<Remedy> allRemedies = new ArrayList<>();

    // Primary Remedy controls
    private final TextField primarySearchField;
    private final ComboBox<RemedyItem> primaryRemedyCombo;

    // Comparative Remedy controls
    private final TextField compareSearchField;
    private final ComboBox<RemedyItem> compareRemedyCombo;

    // Quick-Compare Bar
    private final HBox relatedChipsBar;
    private final Label relatedBarTitle;

    // Dual Content Areas
    private final TextArea leftContentArea;
    private final TextArea rightContentArea;
    private final SplitPane textSplit;
    private final ListView<String> sectionsList;

    // Differential Matrix
    private final TableView<ComparativeParamRow> matrixTable;
    private final StackPane centerContainer;
    private final Label matrixHeaderLabel;

    // View toggles
    private final ToggleButton btnDualView;
    private final ToggleButton btnMatrixView;
    private final ToggleButton btnLibraryView;

    // Digital Library UI fields
    private VBox libraryBox;
    private TextField librarySearchField;
    private ComboBox<String> libraryBookFilterCombo;
    private ListView<MateriaMedicaSearchResult> libraryResultsList;
    private TextArea libraryReaderArea;
    private Label libraryReaderHeading;
    private Label libraryReaderBookLabel;
    private java.util.function.Consumer<String> onConsultAiRequested;

    public void setOnConsultAiRequested(java.util.function.Consumer<String> callback) {
        this.onConsultAiRequested = callback;
    }

    public record RemedyItem(Remedy remedy) {
        @Override
        public String toString() {
            String common = remedy.commonName() != null && !remedy.commonName().isBlank()
                    ? " - " + remedy.commonName()
                    : "";
            return String.format("%s (%s)%s", remedy.fullName(), remedy.abbr(), common);
        }
    }

    public record ComparativeParamRow(
            String parameter,
            String primaryValue,
            String comparativeValue,
            String clinicalDistinction
    ) {}

    public MateriaMedicaView(MasterKnowledgeDao masterDao) {
        this(masterDao, null);
    }

    public MateriaMedicaView(MasterKnowledgeDao masterDao, ClinicDao clinicDao) {
        this.masterDao = masterDao;
        this.clinicDao = clinicDao;

        // =========================================================================
        // TOP TOOLBAR: AUTOSUGGESTION & INDEPENDENT REMEDY SELECTION
        // =========================================================================
        VBox topContainer = new VBox(4);
        topContainer.setPadding(new Insets(6, 10, 4, 10));
        topContainer.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-width: 0 0 1 0;");

        ToolBar toolBar = new ToolBar();
        toolBar.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        // Primary Remedy Chooser
        Label lblPrim = new Label("Primary Remedy:");
        lblPrim.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        primaryRemedyCombo = new ComboBox<>();
        primaryRemedyCombo.setPrefWidth(260);

        primarySearchField = new TextField();
        primarySearchField.setPromptText("🔍 Type to search (e.g. acon, bry, neem)...");
        primarySearchField.setPrefWidth(210);
        primarySearchField.textProperty().addListener((obs, oldVal, newVal) -> filterPrimaryRemedies(newVal));
        primarySearchField.setOnAction(e -> {
            if (!primaryRemedyCombo.getItems().isEmpty()) {
                primaryRemedyCombo.getSelectionModel().select(0);
                loadRemedyText();
            }
        });

        primaryRemedyCombo.setOnAction(e -> {
            loadRemedyText();
            updateRelatedChips();
            updateDifferentialMatrix();
        });

        // Swap button
        Button btnSwap = new Button("⇄ Swap");
        btnSwap.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        btnSwap.setTooltip(new Tooltip("Swap Primary and Comparative Remedies"));
        btnSwap.setOnAction(e -> swapRemedies());

        // Comparative Remedy Chooser
        Label lblComp = new Label("Comparative Remedy:");
        lblComp.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        compareRemedyCombo = new ComboBox<>();
        compareRemedyCombo.setPrefWidth(260);

        compareSearchField = new TextField();
        compareSearchField.setPromptText("🔍 Type to search (e.g. bell, rhus, jamun)...");
        compareSearchField.setPrefWidth(210);
        compareSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterCompareRemedies(newVal));
        compareSearchField.setOnAction(e -> {
            if (!compareRemedyCombo.getItems().isEmpty()) {
                compareRemedyCombo.getSelectionModel().select(0);
                loadCompareText();
            }
        });

        compareRemedyCombo.setOnAction(e -> {
            loadCompareText();
            updateDifferentialMatrix();
        });

        Button btnClearComp = new Button("✕ Clear");
        btnClearComp.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        btnClearComp.setTooltip(new Tooltip("Clear Comparative Remedy for Single-Remedy Full View"));
        btnClearComp.setOnAction(e -> clearComparativeRemedy());

        // View Mode Toggles
        ToggleGroup viewGroup = new ToggleGroup();
        btnDualView = new ToggleButton("📖 Dual Monograph");
        btnDualView.setToggleGroup(viewGroup);
        btnDualView.setSelected(true);
        btnDualView.getStyleClass().add(Styles.SMALL);

        btnMatrixView = new ToggleButton("⚖️ Differential Matrix");
        btnMatrixView.setToggleGroup(viewGroup);
        btnMatrixView.getStyleClass().add(Styles.SMALL);

        btnLibraryView = new ToggleButton("📚 1,500-Book Digital Library (FTS5)");
        btnLibraryView.setToggleGroup(viewGroup);
        btnLibraryView.getStyleClass().add(Styles.SMALL);

        viewGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == btnMatrixView) {
                showDifferentialMatrixView();
            } else if (newVal == btnLibraryView) {
                showDigitalLibraryView();
            } else {
                showDualMonographView();
            }
        });

        toolBar.getItems().addAll(
                lblPrim, primarySearchField, primaryRemedyCombo,
                new Separator(),
                btnSwap,
                new Separator(),
                lblComp, compareSearchField, compareRemedyCombo, btnClearComp,
                new Separator(),
                new Label("Mode:"), btnDualView, btnMatrixView, btnLibraryView
        );

        // Sub-Toolbar: Quick-Compare Related Remedies Chips Bar
        relatedChipsBar = new HBox(8);
        relatedChipsBar.setAlignment(Pos.CENTER_LEFT);
        relatedChipsBar.setPadding(new Insets(3, 0, 3, 0));

        relatedBarTitle = new Label("🔗 Quick Compare Related Remedies (Gibson Miller / HPI):");
        relatedBarTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        relatedBarTitle.getStyleClass().add(Styles.TEXT_MUTED);
        relatedChipsBar.getChildren().add(relatedBarTitle);

        topContainer.getChildren().addAll(toolBar, relatedChipsBar);
        setTop(topContainer);

        // =========================================================================
        // CENTER: SECTIONS & DUAL MONOGRAPH / DIFFERENTIAL MATRIX
        // =========================================================================
        SplitPane mainSplit = new SplitPane();

        // Left Navigation: Sections
        VBox secBox = new VBox(6);
        secBox.setPadding(new Insets(10));
        secBox.setMinWidth(170);
        secBox.setMaxWidth(220);

        Label secLbl = new Label("Monograph Sections");
        secLbl.getStyleClass().add(Styles.TEXT_BOLD);

        sectionsList = new ListView<>();
        sectionsList.getItems().addAll(
                "ALL SECTIONS",
                "CLINICAL RHYMES & KEYNOTES",
                "GENERALITIES",
                "MIND",
                "HEAD",
                "STOMACH",
                "MODALITIES"
        );
        sectionsList.getSelectionModel().select(0);
        sectionsList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            loadRemedyText();
            loadCompareText();
        });

        secBox.getChildren().addAll(secLbl, sectionsList);

        // Center Reading Text Panes (Dual View)
        textSplit = new SplitPane();

        VBox leftBox = new VBox(6);
        leftBox.setPadding(new Insets(8));
        Label leftLbl = new Label("Primary Remedy Monograph");
        leftLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        leftLbl.getStyleClass().add(Styles.ACCENT);
        leftContentArea = new TextArea();
        leftContentArea.setEditable(false);
        leftContentArea.setWrapText(true);
        leftContentArea.setStyle("-fx-font-family: 'Segoe UI', system-ui; -fx-font-size: 13px; -fx-line-spacing: 2px;");
        VBox.setVgrow(leftContentArea, Priority.ALWAYS);
        leftBox.getChildren().addAll(leftLbl, leftContentArea);

        VBox rightBox = new VBox(6);
        rightBox.setPadding(new Insets(8));
        Label rightLbl = new Label("Comparative Monograph (Side-by-Side)");
        rightLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        rightLbl.getStyleClass().add(Styles.TEXT_MUTED);
        rightContentArea = new TextArea();
        rightContentArea.setEditable(false);
        rightContentArea.setWrapText(true);
        rightContentArea.setStyle("-fx-font-family: 'Segoe UI', system-ui; -fx-font-size: 13px; -fx-line-spacing: 2px;");
        VBox.setVgrow(rightContentArea, Priority.ALWAYS);
        rightBox.getChildren().addAll(rightLbl, rightContentArea);

        textSplit.getItems().addAll(leftBox, rightBox);
        textSplit.setDividerPositions(0.5);

        // Center Differential Matrix View
        VBox matrixBox = new VBox(8);
        matrixBox.setPadding(new Insets(10));

        matrixHeaderLabel = new Label("Comparative Differential Analysis Matrix");
        matrixHeaderLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        matrixHeaderLabel.getStyleClass().add(Styles.ACCENT);

        matrixTable = new TableView<>();
        matrixTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(matrixTable, Priority.ALWAYS);

        TableColumn<ComparativeParamRow, String> colParam = new TableColumn<>("Clinical Parameter");
        colParam.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().parameter()));
        colParam.setPrefWidth(180);
        colParam.setStyle("-fx-font-weight: bold;");

        TableColumn<ComparativeParamRow, String> colPrim = new TableColumn<>("Primary Remedy");
        colPrim.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().primaryValue()));
        colPrim.setPrefWidth(270);

        TableColumn<ComparativeParamRow, String> colComp = new TableColumn<>("Comparative Remedy");
        colComp.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().comparativeValue()));
        colComp.setPrefWidth(270);

        TableColumn<ComparativeParamRow, String> colDiff = new TableColumn<>("Key Clinical Distinction & Totality Difference");
        colDiff.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().clinicalDistinction()));
        colDiff.setPrefWidth(340);

        matrixTable.getColumns().addAll(List.of(colParam, colPrim, colComp, colDiff));
        matrixBox.getChildren().addAll(matrixHeaderLabel, matrixTable);

        // Center container holding either Dual text or Matrix
        centerContainer = new StackPane(textSplit);

        mainSplit.getItems().addAll(secBox, centerContainer);
        mainSplit.setDividerPositions(0.18);
        setCenter(mainSplit);

        // Initialize Remedies
        loadRemedies();
    }

    private void showDualMonographView() {
        centerContainer.getChildren().setAll(textSplit);
        loadRemedyText();
        loadCompareText();
    }

    private void showDifferentialMatrixView() {
        centerContainer.getChildren().setAll(matrixTable.getParent() instanceof VBox ? matrixTable.getParent() : new VBox(matrixHeaderLabel, matrixTable));
        updateDifferentialMatrix();
    }

    private void showDigitalLibraryView() {
        if (libraryBox == null) {
            libraryBox = createDigitalLibraryBox();
        }
        centerContainer.getChildren().setAll(libraryBox);
        if (libraryResultsList.getItems().isEmpty()) {
            executeLibrarySearch();
        }
    }

    private VBox createDigitalLibraryBox() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        Label searchLbl = new Label("📚 FTS5 Search across 1,500 Volumes:");
        searchLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        librarySearchField = new TextField();
        librarySearchField.setPromptText("Enter symptom keywords (e.g. headache sun agg glonoin, organon 248, burning heat amel)...");
        librarySearchField.setPrefWidth(380);
        librarySearchField.setOnAction(e -> executeLibrarySearch());

        libraryBookFilterCombo = new ComboBox<>();
        libraryBookFilterCombo.setPrefWidth(240);
        libraryBookFilterCombo.getItems().addAll(
                "All 1,500 Volumes",
                "Organon of Medicine (6th Edition)",
                "Materia Medica Pura (Vols 1-6)",
                "The Chronic Diseases (Vols 1-5)",
                "Lectures on Homoeopathic Materia Medica",
                "Lectures on Homoeopathic Philosophy",
                "Pocket Manual of Homoeopathic Materia Medica & Repertory",
                "A Dictionary of Practical Materia Medica (3 Volumes)",
                "Leaders in Homoeopathic Therapeutics",
                "The Guiding Symptoms of our Materia Medica (10 Volumes)",
                "A Synoptic Key of the Materia Medica",
                "Clinical Verification of Indigenous Indian Drugs"
        );
        libraryBookFilterCombo.setValue("All 1,500 Volumes");
        libraryBookFilterCombo.setOnAction(e -> executeLibrarySearch());

        Button searchBtn = new Button("⚡ Instant FTS5 Search");
        searchBtn.getStyleClass().addAll(Styles.ACCENT, Styles.BUTTON_OUTLINED);
        searchBtn.setOnAction(e -> executeLibrarySearch());

        searchRow.getChildren().addAll(searchLbl, librarySearchField, libraryBookFilterCombo, searchBtn);

        // SplitPane: Results List on Left, Monograph Content on Right
        SplitPane libSplit = new SplitPane();
        VBox.setVgrow(libSplit, Priority.ALWAYS);

        VBox resultsBox = new VBox(6);
        resultsBox.setPadding(new Insets(6));
        Label resLbl = new Label("Matching Treatise Chapters & Paragraphs:");
        resLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        resLbl.getStyleClass().add(Styles.TEXT_MUTED);

        libraryResultsList = new ListView<>();
        VBox.setVgrow(libraryResultsList, Priority.ALWAYS);
        libraryResultsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(MateriaMedicaSearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox card = new VBox(2);
                    Label title = new Label(item.remedyName() + " — " + item.sectionHeading());
                    title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                    title.setTextFill(javafx.scene.paint.Color.web("#0369a1"));

                    Label meta = new Label("📖 " + item.bookTitle() + " (" + item.author() + ")");
                    meta.setFont(Font.font("Segoe UI", 10));
                    meta.setTextFill(javafx.scene.paint.Color.web("#475569"));

                    String cleanSnippet = item.snippet() != null ? item.snippet().replaceAll("<[^>]*>", "") : "";
                    Label snip = new Label(cleanSnippet);
                    snip.setFont(Font.font("Segoe UI", 11));
                    snip.setWrapText(true);
                    snip.setMaxWidth(340);

                    card.getChildren().addAll(title, meta, snip);
                    setGraphic(card);
                }
            }
        });

        libraryResultsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                displayLibrarySection(newVal);
            }
        });

        resultsBox.getChildren().addAll(resLbl, libraryResultsList);

        // Reader Box
        VBox readerBox = new VBox(8);
        readerBox.setPadding(new Insets(8));

        HBox readerHeader = new HBox(12);
        readerHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        libraryReaderHeading = new Label("Select a section from the search results");
        libraryReaderHeading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        libraryReaderBookLabel = new Label("Materia Medica Digital Library");
        libraryReaderBookLabel.setFont(Font.font("Segoe UI", 11));
        libraryReaderBookLabel.getStyleClass().add(Styles.TEXT_MUTED);
        titleBox.getChildren().addAll(libraryReaderHeading, libraryReaderBookLabel);

        Region rSpacer = new Region();
        HBox.setHgrow(rSpacer, Priority.ALWAYS);

        Button consultAiBtn = new Button("🧠 Consult Logic AI on this Chapter");
        consultAiBtn.getStyleClass().addAll(Styles.SUCCESS, Styles.BUTTON_OUTLINED);
        consultAiBtn.setOnAction(e -> {
            if (onConsultAiRequested != null && libraryResultsList.getSelectionModel().getSelectedItem() != null) {
                MateriaMedicaSearchResult sel = libraryResultsList.getSelectionModel().getSelectedItem();
                String prompt = "Consult digital library: Analyze clinical keynote and indications for '" + sel.remedyName() +
                        "' as documented in " + sel.bookTitle() + " by " + sel.author() + " (" + sel.sectionHeading() + ").";
                onConsultAiRequested.accept(prompt);
            }
        });

        readerHeader.getChildren().addAll(titleBox, rSpacer, consultAiBtn);

        libraryReaderArea = new TextArea();
        libraryReaderArea.setEditable(false);
        libraryReaderArea.setWrapText(true);
        libraryReaderArea.setStyle("-fx-font-family: 'Segoe UI', system-ui; -fx-font-size: 13px; -fx-line-spacing: 2px;");
        VBox.setVgrow(libraryReaderArea, Priority.ALWAYS);

        readerBox.getChildren().addAll(readerHeader, libraryReaderArea);

        libSplit.getItems().addAll(resultsBox, readerBox);
        libSplit.setDividerPositions(0.35);

        box.getChildren().addAll(searchRow, libSplit);
        return box;
    }

    private void executeLibrarySearch() {
        if (librarySearchField == null) return;
        String q = librarySearchField.getText();
        if (q == null || q.isBlank()) {
            // Default sample query
            q = "burning heat amel";
        }

        try {
            List<MateriaMedicaSearchResult> results = masterDao.searchMateriaMedicaFts(q);
            String bookFilter = libraryBookFilterCombo.getValue();
            if (bookFilter != null && !bookFilter.equals("All 1,500 Volumes")) {
                results = results.stream()
                        .filter(r -> r.bookTitle() != null && r.bookTitle().toLowerCase().contains(bookFilter.toLowerCase().substring(0, Math.min(15, bookFilter.length()))))
                        .toList();
            }

            libraryResultsList.setItems(javafx.collections.FXCollections.observableArrayList(results));
            if (!results.isEmpty()) {
                libraryResultsList.getSelectionModel().selectFirst();
            } else {
                libraryReaderHeading.setText("No matching chapters found for query: " + q);
                libraryReaderArea.setText("Tip: Try searching for characteristic symptoms like 'burning pain', 'restlessness', 'Organon 248', or remedy names like 'Arsenicum', 'Azadirachta', 'Lycopodium'.");
            }
        } catch (Exception e) {
            log.error("Failed to execute library search", e);
        }
    }

    private void displayLibrarySection(MateriaMedicaSearchResult res) {
        libraryReaderHeading.setText(res.remedyName() + " — " + res.sectionHeading());
        libraryReaderBookLabel.setText("Book: " + res.bookTitle() + " | Author: " + res.author());

        try {
            List<MateriaMedicaSection> sections = masterDao.getSectionsForRemedy(res.remedyName());
            Optional<MateriaMedicaSection> match = sections.stream()
                    .filter(s -> s.sectionHeading().equalsIgnoreCase(res.sectionHeading()))
                    .findFirst();

            if (match.isPresent()) {
                libraryReaderArea.setText(match.get().bodyText() + "\n\n[Citation: " + match.get().pageOrVolRef() + "]");
            } else {
                libraryReaderArea.setText(res.snippet() != null ? res.snippet().replaceAll("<[^>]*>", "") : "Full text unavailable.");
            }
        } catch (Exception e) {
            libraryReaderArea.setText(res.snippet() != null ? res.snippet().replaceAll("<[^>]*>", "") : "");
        }
    }

    private void loadRemedies() {
        try {
            allRemedies.clear();
            allRemedies.addAll(masterDao.getAllRemedies());

            filterPrimaryRemedies("");
            filterCompareRemedies("");

            if (!primaryRemedyCombo.getItems().isEmpty()) {
                primaryRemedyCombo.getSelectionModel().select(0);
                if (compareRemedyCombo.getItems().size() > 1) {
                    compareRemedyCombo.getSelectionModel().select(1);
                }
                loadRemedyText();
                loadCompareText();
                updateRelatedChips();
                updateDifferentialMatrix();
            }
        } catch (Exception e) {
            log.error("Failed to load remedies into Materia Medica view", e);
        }
    }

    // =========================================================================
    // DYNAMIC AUTOSUGGESTION & INDEPENDENT CHOOSING
    // =========================================================================

    private void filterPrimaryRemedies(String filter) {
        Remedy current = primaryRemedyCombo.getValue() != null ? primaryRemedyCombo.getValue().remedy : null;
        primaryRemedyCombo.getItems().clear();

        String q = filter != null ? filter.trim().toLowerCase() : "";

        for (Remedy r : allRemedies) {
            boolean match = q.isEmpty()
                    || r.id().toLowerCase().startsWith(q)
                    || r.abbr().toLowerCase().contains(q)
                    || r.fullName().toLowerCase().contains(q)
                    || (r.commonName() != null && r.commonName().toLowerCase().contains(q))
                    || r.kingdom().toLowerCase().contains(q);

            if (match) {
                primaryRemedyCombo.getItems().add(new RemedyItem(r));
            }
        }

        if (!primaryRemedyCombo.getItems().isEmpty()) {
            if (current != null) {
                boolean found = false;
                for (RemedyItem it : primaryRemedyCombo.getItems()) {
                    if (it.remedy.id().equalsIgnoreCase(current.id())) {
                        primaryRemedyCombo.getSelectionModel().select(it);
                        found = true;
                        break;
                    }
                }
                if (!found && !q.isEmpty()) {
                    primaryRemedyCombo.getSelectionModel().select(0);
                }
            } else {
                primaryRemedyCombo.getSelectionModel().select(0);
            }
        }
    }

    private void filterCompareRemedies(String filter) {
        Remedy current = compareRemedyCombo.getValue() != null ? compareRemedyCombo.getValue().remedy : null;
        compareRemedyCombo.getItems().clear();

        String q = filter != null ? filter.trim().toLowerCase() : "";

        for (Remedy r : allRemedies) {
            boolean match = q.isEmpty()
                    || r.id().toLowerCase().startsWith(q)
                    || r.abbr().toLowerCase().contains(q)
                    || r.fullName().toLowerCase().contains(q)
                    || (r.commonName() != null && r.commonName().toLowerCase().contains(q))
                    || r.kingdom().toLowerCase().contains(q);

            if (match) {
                compareRemedyCombo.getItems().add(new RemedyItem(r));
            }
        }

        if (!compareRemedyCombo.getItems().isEmpty()) {
            if (current != null) {
                boolean found = false;
                for (RemedyItem it : compareRemedyCombo.getItems()) {
                    if (it.remedy.id().equalsIgnoreCase(current.id())) {
                        compareRemedyCombo.getSelectionModel().select(it);
                        found = true;
                        break;
                    }
                }
                if (!found && !q.isEmpty()) {
                    compareRemedyCombo.getSelectionModel().select(0);
                }
            } else {
                if (compareRemedyCombo.getItems().size() > 1) {
                    compareRemedyCombo.getSelectionModel().select(1);
                } else {
                    compareRemedyCombo.getSelectionModel().select(0);
                }
            }
        }
    }

    public void selectPrimaryRemedy(String remedyId) {
        if (remedyId == null) return;
        for (Remedy r : allRemedies) {
            if (r.id().equalsIgnoreCase(remedyId) || r.abbr().equalsIgnoreCase(remedyId)) {
                primarySearchField.setText("");
                filterPrimaryRemedies("");
                for (RemedyItem it : primaryRemedyCombo.getItems()) {
                    if (it.remedy.id().equalsIgnoreCase(r.id())) {
                        primaryRemedyCombo.getSelectionModel().select(it);
                        loadRemedyText();
                        updateRelatedChips();
                        updateDifferentialMatrix();
                        return;
                    }
                }
            }
        }
    }

    public void selectComparativeRemedy(String remedyId) {
        if (remedyId == null) return;
        for (Remedy r : allRemedies) {
            if (r.id().equalsIgnoreCase(remedyId) || r.abbr().equalsIgnoreCase(remedyId)) {
                compareSearchField.setText("");
                filterCompareRemedies("");
                for (RemedyItem it : compareRemedyCombo.getItems()) {
                    if (it.remedy.id().equalsIgnoreCase(r.id())) {
                        compareRemedyCombo.getSelectionModel().select(it);
                        loadCompareText();
                        updateDifferentialMatrix();
                        return;
                    }
                }
            }
        }
    }

    private void swapRemedies() {
        RemedyItem p = primaryRemedyCombo.getValue();
        RemedyItem c = compareRemedyCombo.getValue();
        if (p == null && c == null) return;

        if (c != null) {
            selectPrimaryRemedy(c.remedy.id());
        }
        if (p != null) {
            selectComparativeRemedy(p.remedy.id());
        }
    }

    private void clearComparativeRemedy() {
        compareRemedyCombo.setValue(null);
        rightContentArea.setText("No comparative remedy selected.\n\nType in the 'Comparative Remedy' search box above or click any Related Remedy chip to load a comparative study.");
        updateDifferentialMatrix();
    }

    private void updateRelatedChips() {
        relatedChipsBar.getChildren().clear();
        relatedChipsBar.getChildren().add(relatedBarTitle);

        RemedyItem p = primaryRemedyCombo.getValue();
        if (p == null) return;

        try {
            List<RemedyRelationship> rels = masterDao.getRemedyRelationships(p.remedy.id());
            if (rels.isEmpty()) {
                Label none = new Label("[No direct classical relationships mapped]");
                none.getStyleClass().add(Styles.TEXT_MUTED);
                relatedChipsBar.getChildren().add(none);
                return;
            }

            for (RemedyRelationship rel : rels) {
                String targetId = rel.remedyId().equalsIgnoreCase(p.remedy.id())
                        ? rel.relatedRemedyId()
                        : rel.remedyId();

                Optional<Remedy> targetRemOpt = masterDao.getRemedyById(targetId);
                String displayTitle = targetRemOpt.map(r -> r.abbr() + " (" + rel.relationType() + ")").orElse(targetId + " (" + rel.relationType() + ")");

                Button chip = new Button(displayTitle);
                chip.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);

                if (rel.relationType() == RemedyRelationship.RelationType.COMPLEMENTARY) {
                    chip.getStyleClass().add(Styles.SUCCESS);
                } else if (rel.relationType() == RemedyRelationship.RelationType.INIMICAL) {
                    chip.getStyleClass().add(Styles.DANGER);
                } else if (rel.relationType() == RemedyRelationship.RelationType.ANTIDOTE) {
                    chip.getStyleClass().add(Styles.WARNING);
                }

                if (rel.notes() != null && !rel.notes().isBlank()) {
                    chip.setTooltip(new Tooltip(rel.notes()));
                }

                chip.setOnAction(e -> selectComparativeRemedy(targetId));
                relatedChipsBar.getChildren().add(chip);
            }
        } catch (Exception ex) {
            log.error("Failed to update related remedy chips", ex);
        }
    }

    // =========================================================================
    // TEXT MONOGRAPH RENDERING
    // =========================================================================

    private void loadRemedyText() {
        RemedyItem item = primaryRemedyCombo.getValue();
        if (item == null) {
            leftContentArea.setText("");
            return;
        }
        renderText(item.remedy.id(), leftContentArea);
    }

    private void loadCompareText() {
        RemedyItem item = compareRemedyCombo.getValue();
        if (item == null) {
            rightContentArea.setText("No comparative remedy selected.\n\nUse the Comparative search bar or Related Remedy chips to load a second remedy for side-by-side study.");
            return;
        }
        renderText(item.remedy.id(), rightContentArea);
    }

    private void renderText(String remedyId, TextArea target) {
        String filterSection = sectionsList.getSelectionModel().getSelectedItem();
        try {
            Optional<Remedy> remOpt = masterDao.getRemedyById(remedyId);
            List<MateriaMedicaEntry> entries = masterDao.getMateriaMedica(remedyId);
            StringBuilder sb = new StringBuilder();

            if (remOpt.isPresent()) {
                Remedy r = remOpt.get();
                sb.append("========================================================================\n");
                sb.append(r.fullName().toUpperCase()).append(" (").append(r.abbr()).append(")\n");
                if (r.commonName() != null && !r.commonName().isBlank()) {
                    sb.append("Common / Indian Name: ").append(r.commonName()).append("\n");
                }
                sb.append("Kingdom: ").append(r.kingdom());
                if (r.family() != null) sb.append(" | Family: ").append(r.family());
                if (r.miasm() != null) sb.append(" | Miasm: ").append(r.miasm());
                sb.append(" | Thermal: ").append(r.thermal());
                sb.append(" | Thirst: ").append(r.thirst());
                sb.append("\n========================================================================\n\n");

                // Prominently display Clinical Rhymes for Medicine Choosing
                String rhyme = HomeopathicClinicalRhymes.findRhyme(r.id(), r.fullName(), r.commonName());
                if (filterSection == null || filterSection.equals("ALL SECTIONS") || filterSection.equals("CLINICAL RHYMES & KEYNOTES")) {
                    if (rhyme != null) {
                        sb.append("------------------------------------------------------------------------\n");
                        sb.append("🎵 CLINICAL RHYME & KEYNOTE FOR MEDICINE CHOOSING (E. B. Nash / Allen / Master Verses):\n");
                        sb.append("------------------------------------------------------------------------\n");
                        sb.append(rhyme).append("\n\n");
                    } else if ("CLINICAL RHYMES & KEYNOTES".equals(filterSection)) {
                        sb.append("------------------------------------------------------------------------\n");
                        sb.append("🎵 CLINICAL KEYNOTE FOR MEDICINE CHOOSING:\n");
                        sb.append("------------------------------------------------------------------------\n");
                        sb.append("Keynote Picture for ").append(r.fullName()).append(" [").append(r.abbr()).append("]:\n");
                        sb.append("• Kingdom: ").append(r.kingdom()).append("\n");
                        sb.append("• Thermal / Thirst: ").append(r.thermal()).append(" / ").append(r.thirst()).append("\n");
                        sb.append("• Dominant Miasm: ").append(r.miasm()).append("\n");
                        sb.append("• Totality Note: Refer to symptom modalities and affinities below.\n\n");
                    }
                }
            }

            boolean sectionFound = "CLINICAL RHYMES & KEYNOTES".equals(filterSection);
            for (MateriaMedicaEntry e : entries) {
                if (filterSection == null || filterSection.equals("ALL SECTIONS") || filterSection.equalsIgnoreCase(e.section())) {
                    sb.append("--- ").append(e.section()).append(" [").append(e.author()).append(" - ").append(e.bookTitle()).append("] ---\n\n");
                    sb.append(e.bodyText()).append("\n\n");
                    sectionFound = true;
                }
            }

            // Fallback for remedies without isolated 19th century Boericke chapter:
            // Display Pharmacopoeial & Phytochemical Monograph so NO remedy is ever blank!
            if ((entries.isEmpty() || !sectionFound) && clinicDao != null) {
                Optional<MedicineConstituent> mcOpt = clinicDao.getMedicineConstituent(remedyId);
                if (mcOpt.isPresent()) {
                    MedicineConstituent mc = mcOpt.get();
                    sb.append("--- PHARMACOPOEIAL & CLINICAL MONOGRAPH (HPI / Scientific Verification) ---\n\n");
                    sb.append("• Active Constituents: ").append(mc.activeConstituents()).append("\n\n");
                    sb.append("• Scientific Mode of Action: ").append(mc.pharmacologicalAction()).append("\n\n");
                    sb.append("• Organ Affinity & Tissues: ").append(mc.organAffinity()).append("\n\n");
                    sb.append("• Toxicology & Antidotes: ").append(mc.toxicologyAndAntidotes()).append("\n\n");
                    if (mc.annexedRemarks() != null && !mc.annexedRemarks().isBlank()) {
                        sb.append("• Approved Clinical Remarks: ").append(mc.annexedRemarks()).append("\n\n");
                    }
                    sectionFound = true;
                }
            }

            // Append Clinical Relationships
            if (filterSection == null || filterSection.equals("ALL SECTIONS") || filterSection.equals("GENERALITIES")) {
                List<RemedyRelationship> rels = masterDao.getRemedyRelationships(remedyId);
                if (!rels.isEmpty()) {
                    sb.append("--- CLINICAL RELATIONSHIPS (Gibson Miller / HPI) ---\n\n");
                    for (RemedyRelationship rel : rels) {
                        sb.append(" • ").append(rel.relationType()).append(": ")
                                .append(rel.relatedRemedyId().toUpperCase());
                        if (rel.notes() != null && !rel.notes().isBlank()) {
                            sb.append(" — ").append(rel.notes());
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
            }

            target.setText(sb.toString());

        } catch (Exception ex) {
            log.error("Failed to render materia medica text", ex);
            target.setText("Error loading text: " + ex.getMessage());
        }
    }

    // =========================================================================
    // COMPARATIVE DIFFERENTIAL ANALYSIS MATRIX
    // =========================================================================

    private void updateDifferentialMatrix() {
        RemedyItem prim = primaryRemedyCombo.getValue();
        RemedyItem comp = compareRemedyCombo.getValue();

        if (prim == null) {
            matrixTable.getItems().clear();
            return;
        }

        Remedy p = prim.remedy;
        Remedy c = comp != null ? comp.remedy : null;

        matrixHeaderLabel.setText(String.format("⚖️ Comparative Differential Matrix: %s vs %s",
                p.fullName(), c != null ? c.fullName() : "(None Selected)"));

        ObservableList<ComparativeParamRow> rows = FXCollections.observableArrayList();

        // 1. Botanical / Latin Name
        rows.add(new ComparativeParamRow(
                "Latin Binomial Name",
                p.fullName(),
                c != null ? c.fullName() : "—",
                c != null ? "Differential identification" : "Single remedy"
        ));

        // 2. Common & Indian Name
        rows.add(new ComparativeParamRow(
                "Common & Indian Name",
                p.commonName() != null ? p.commonName() : "—",
                c != null && c.commonName() != null ? c.commonName() : "—",
                "Vernacular & traditional reference"
        ));

        // 3. Kingdom & Botanical Family
        rows.add(new ComparativeParamRow(
                "Kingdom & Family",
                p.kingdom() + (p.family() != null ? " (" + p.family() + ")" : ""),
                c != null ? c.kingdom() + (c.family() != null ? " (" + c.family() + ")" : "") : "—",
                c != null && p.kingdom().equalsIgnoreCase(c.kingdom()) ? "Shared kingdom source" : "Different kingdom diathesis"
        ));

        // 4. Thermal Reaction
        String pTherm = p.thermal() != null ? p.thermal().name() : "AMBITHERMAL";
        String cTherm = c != null && c.thermal() != null ? c.thermal().name() : "—";
        String thermDiff;
        if (c == null) {
            thermDiff = pTherm.equalsIgnoreCase("CHILLY") ? "Aggravated by cold drafts / un-covering" : "Aggravated by warm room / heat";
        } else if (pTherm.equalsIgnoreCase(cTherm)) {
            thermDiff = "Same thermal plane (" + pTherm + ") — differentiate by thirst & modalities";
        } else {
            thermDiff = "⚡ POLAR THERMAL CONTRAST: " + p.abbr() + " is " + pTherm + " vs " + c.abbr() + " is " + cTherm;
        }
        rows.add(new ComparativeParamRow("Thermal State", pTherm, cTherm, thermDiff));

        // 5. Thirst
        String pThirst = p.thirst() != null ? p.thirst().name() : "VARIABLE";
        String cThirst = c != null && c.thirst() != null ? c.thirst().name() : "—";
        String thirstDiff;
        if (c == null) {
            thirstDiff = pThirst.equalsIgnoreCase("THIRSTY") ? "Craves cold fluids in large volume" : "Thirstless even during fever";
        } else if (pThirst.equalsIgnoreCase(cThirst)) {
            thirstDiff = "Both " + pThirst + " — examine drinking frequency and water temperature preference";
        } else {
            thirstDiff = "⚡ KEY CONTRAST: " + p.abbr() + " (" + pThirst + ") vs " + c.abbr() + " (" + cThirst + ")";
        }
        rows.add(new ComparativeParamRow("Thirst Dynamics", pThirst, cThirst, thirstDiff));

        // 6. Dominant Miasm
        String pMiasm = p.miasm() != null ? p.miasm() : "PSORA";
        String cMiasm = c != null && c.miasm() != null ? c.miasm() : "—";
        rows.add(new ComparativeParamRow(
                "Miasmatic Diathesis",
                pMiasm,
                cMiasm,
                c != null && pMiasm.equalsIgnoreCase(cMiasm) ? "Identical miasmatic layer" : "Differing miasmatic depth"
        ));

        // 7. Clinical Relationship
        String relNotes = "No direct mutual relationship recorded";
        if (c != null) {
            try {
                List<RemedyRelationship> rels = masterDao.getRemedyRelationships(p.id());
                for (RemedyRelationship r : rels) {
                    if (r.remedyId().equalsIgnoreCase(c.id()) || r.relatedRemedyId().equalsIgnoreCase(c.id())) {
                        relNotes = "★ " + r.relationType() + ": " + (r.notes() != null ? r.notes() : "Classical clinical connection");
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        rows.add(new ComparativeParamRow("Mutual Relationship", "Primary Prescribing Focus", c != null ? "Comparative Secondary" : "—", relNotes));

        // 8. Active Phytochemicals & Pharmacology (from ClinicDao)
        String pActive = "Natural homeopathic potency";
        String cActive = "—";
        String pPharm = "Symptom totality proving";
        String cPharm = "—";

        if (clinicDao != null) {
            try {
                Optional<MedicineConstituent> pMc = clinicDao.getMedicineConstituent(p.id());
                if (pMc.isPresent()) {
                    pActive = pMc.get().activeConstituents();
                    pPharm = pMc.get().pharmacologicalAction();
                }
                if (c != null) {
                    Optional<MedicineConstituent> cMc = clinicDao.getMedicineConstituent(c.id());
                    if (cMc.isPresent()) {
                        cActive = cMc.get().activeConstituents();
                        cPharm = cMc.get().pharmacologicalAction();
                    }
                }
            } catch (Exception ignored) {}
        }
        rows.add(new ComparativeParamRow("Active Principles", pActive, cActive, "Biochemical and phytochemical profiles"));
        rows.add(new ComparativeParamRow("Pharmacological Action", pPharm, cPharm, "Cellular & organ pathway validation"));

        // 9. Classical Clinical Rhymes for Choosing
        String pRhyme = HomeopathicClinicalRhymes.findRhyme(p.id(), p.fullName(), p.commonName());
        String cRhyme = c != null ? HomeopathicClinicalRhymes.findRhyme(c.id(), c.fullName(), c.commonName()) : "—";
        rows.add(new ComparativeParamRow(
                "Clinical Rhyme & Keynote",
                pRhyme != null ? pRhyme : "Keynote indications in Materia Medica",
                cRhyme != null ? cRhyme : "—",
                "Mnemonic recognition for rapid bedside remedy selection"
        ));

        matrixTable.setItems(rows);
    }
}
