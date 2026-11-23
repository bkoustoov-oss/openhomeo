package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.MateriaMedicaEntry;
import org.openhomeo.core.model.Remedy;
import org.openhomeo.core.model.RemedyRelationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classical Materia Medica Reader and Comparative Reference Viewer.
 * Displays authoritative classical monographs (Boericke, Allen, HPI) with dual-remedy comparison.
 */
public class MateriaMedicaView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(MateriaMedicaView.class);

    private final MasterKnowledgeDao masterDao;

    private final List<Remedy> allRemedies = new ArrayList<>();
    private final TextField searchField;
    private final ComboBox<RemedyItem> primaryRemedyCombo;
    private final ComboBox<RemedyItem> compareRemedyCombo;
    private final TextArea leftContentArea;
    private final TextArea rightContentArea;
    private final ListView<String> sectionsList;

    public record RemedyItem(Remedy remedy) {
        @Override
        public String toString() {
            String common = remedy.commonName() != null ? " - " + remedy.commonName() : "";
            return String.format("%s (%s)%s", remedy.fullName(), remedy.abbr(), common);
        }
    }

    public MateriaMedicaView(MasterKnowledgeDao masterDao) {
        this.masterDao = masterDao;

        // Top Toolbar
        ToolBar toolBar = new ToolBar();

        searchField = new TextField();
        searchField.setPromptText("Filter remedies (e.g. 'neem', 'acon', 'jamun')...");
        searchField.setMinWidth(220);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterRemedies(newVal));

        primaryRemedyCombo = new ComboBox<>();
        primaryRemedyCombo.setMinWidth(280);
        primaryRemedyCombo.setOnAction(e -> loadRemedyText());

        compareRemedyCombo = new ComboBox<>();
        compareRemedyCombo.setMinWidth(280);
        compareRemedyCombo.setOnAction(e -> loadCompareText());

        toolBar.getItems().addAll(
                new Label("Search:"), searchField,
                new Separator(),
                new Label("Primary Remedy:"), primaryRemedyCombo,
                new Separator(),
                new Label("Comparative Remedy:"), compareRemedyCombo
        );
        setTop(toolBar);

        // Center SplitPane: Sections List on left, Two Reading Panes in center
        SplitPane split = new SplitPane();

        // Sections
        VBox secBox = new VBox(6);
        secBox.setPadding(new Insets(10));
        secBox.setMinWidth(160);
        secBox.setMaxWidth(200);

        Label secLbl = new Label("Sections");
        secLbl.getStyleClass().add(Styles.TEXT_BOLD);

        sectionsList = new ListView<>();
        sectionsList.getItems().addAll("ALL SECTIONS", "GENERALITIES", "MIND", "HEAD", "STOMACH", "MODALITIES");
        sectionsList.getSelectionModel().select(0);
        sectionsList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            loadRemedyText();
            loadCompareText();
        });

        secBox.getChildren().addAll(secLbl, sectionsList);

        // Reading Text Panes
        SplitPane textSplit = new SplitPane();

        VBox leftBox = new VBox(6);
        leftBox.setPadding(new Insets(8));
        Label leftLbl = new Label("Primary Remedy Monograph");
        leftLbl.getStyleClass().add(Styles.TEXT_MUTED);
        leftContentArea = new TextArea();
        leftContentArea.setEditable(false);
        leftContentArea.setWrapText(true);
        leftContentArea.setStyle("-fx-font-family: 'Segoe UI', system-ui; -fx-font-size: 13px; -fx-line-spacing: 2px;");
        VBox.setVgrow(leftContentArea, Priority.ALWAYS);
        leftBox.getChildren().addAll(leftLbl, leftContentArea);

        VBox rightBox = new VBox(6);
        rightBox.setPadding(new Insets(8));
        Label rightLbl = new Label("Comparative Monograph");
        rightLbl.getStyleClass().add(Styles.TEXT_MUTED);
        rightContentArea = new TextArea();
        rightContentArea.setEditable(false);
        rightContentArea.setWrapText(true);
        rightContentArea.setStyle("-fx-font-family: 'Segoe UI', system-ui; -fx-font-size: 13px; -fx-line-spacing: 2px;");
        VBox.setVgrow(rightContentArea, Priority.ALWAYS);
        rightBox.getChildren().addAll(rightLbl, rightContentArea);

        textSplit.getItems().addAll(leftBox, rightBox);
        textSplit.setDividerPositions(0.5);

        split.getItems().addAll(secBox, textSplit);
        split.setDividerPositions(0.18);
        setCenter(split);

        loadRemedies();
    }

    private void loadRemedies() {
        try {
            allRemedies.clear();
            allRemedies.addAll(masterDao.getAllRemedies());
            filterRemedies("");
        } catch (Exception e) {
            log.error("Failed to load remedies into Materia Medica view", e);
        }
    }

    private void filterRemedies(String filter) {
        primaryRemedyCombo.getItems().clear();
        compareRemedyCombo.getItems().clear();

        String q = filter != null ? filter.trim().toLowerCase() : "";

        for (Remedy r : allRemedies) {
            boolean match = q.isEmpty()
                    || r.fullName().toLowerCase().contains(q)
                    || r.abbr().toLowerCase().contains(q)
                    || (r.commonName() != null && r.commonName().toLowerCase().contains(q))
                    || r.kingdom().toLowerCase().contains(q);

            if (match) {
                primaryRemedyCombo.getItems().add(new RemedyItem(r));
                compareRemedyCombo.getItems().add(new RemedyItem(r));
            }
        }

        if (!primaryRemedyCombo.getItems().isEmpty()) {
            primaryRemedyCombo.getSelectionModel().select(0);
            if (compareRemedyCombo.getItems().size() > 1) {
                compareRemedyCombo.getSelectionModel().select(1);
            }
            loadRemedyText();
            loadCompareText();
        }
    }

    private void loadRemedyText() {
        RemedyItem item = primaryRemedyCombo.getValue();
        if (item == null) return;
        renderText(item.remedy.id(), leftContentArea);
    }

    private void loadCompareText() {
        RemedyItem item = compareRemedyCombo.getValue();
        if (item == null) return;
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
                    sb.append("Common Name: ").append(r.commonName()).append("\n");
                }
                sb.append("Kingdom: ").append(r.kingdom());
                if (r.family() != null) sb.append(" | Family: ").append(r.family());
                if (r.miasm() != null) sb.append(" | Miasm: ").append(r.miasm());
                sb.append(" | Thermal: ").append(r.thermal());
                sb.append(" | Thirst: ").append(r.thirst());
                sb.append("\n========================================================================\n\n");
            }

            boolean sectionFound = false;
            for (MateriaMedicaEntry e : entries) {
                if (filterSection == null || filterSection.equals("ALL SECTIONS") || filterSection.equalsIgnoreCase(e.section())) {
                    sb.append("--- ").append(e.section()).append(" [").append(e.author()).append(" - ").append(e.bookTitle()).append("] ---\n\n");
                    sb.append(e.bodyText()).append("\n\n");
                    sectionFound = true;
                }
            }

            // If a specific section was not found, show general overview
            if (!sectionFound && !entries.isEmpty()) {
                sb.append("[No isolated ").append(filterSection).append(" chapter; presenting full monograph overview below]\n\n");
                for (MateriaMedicaEntry e : entries) {
                    sb.append("--- ").append(e.section()).append(" ---\n");
                    sb.append(e.bodyText()).append("\n\n");
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
}
