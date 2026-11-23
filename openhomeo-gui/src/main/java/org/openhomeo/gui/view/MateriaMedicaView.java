package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.MateriaMedicaEntry;
import org.openhomeo.core.model.Remedy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Classical Materia Medica Reader and Comparative Reference Viewer.
 */
public class MateriaMedicaView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(MateriaMedicaView.class);

    private final MasterKnowledgeDao masterDao;

    private final ComboBox<RemedyItem> primaryRemedyCombo;
    private final ComboBox<RemedyItem> compareRemedyCombo;
    private final TextArea leftContentArea;
    private final TextArea rightContentArea;
    private final ListView<String> sectionsList;

    public record RemedyItem(Remedy remedy) {
        @Override
        public String toString() {
            return String.format("%s (%s)", remedy.fullName(), remedy.abbr());
        }
    }

    public MateriaMedicaView(MasterKnowledgeDao masterDao) {
        this.masterDao = masterDao;

        // Top Toolbar
        ToolBar toolBar = new ToolBar();
        primaryRemedyCombo = new ComboBox<>();
        primaryRemedyCombo.setMinWidth(240);
        primaryRemedyCombo.setOnAction(e -> loadRemedyText());

        compareRemedyCombo = new ComboBox<>();
        compareRemedyCombo.setMinWidth(240);
        compareRemedyCombo.setOnAction(e -> loadCompareText());

        toolBar.getItems().addAll(
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
        sectionsList.getItems().addAll("ALL SECTIONS", "MIND", "HEAD", "STOMACH", "GENERALITIES", "MODALITIES");
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
        VBox.setVgrow(leftContentArea, Priority.ALWAYS);
        leftBox.getChildren().addAll(leftLbl, leftContentArea);

        VBox rightBox = new VBox(6);
        rightBox.setPadding(new Insets(8));
        Label rightLbl = new Label("Comparative Monograph");
        rightLbl.getStyleClass().add(Styles.TEXT_MUTED);
        rightContentArea = new TextArea();
        rightContentArea.setEditable(false);
        rightContentArea.setWrapText(true);
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
        primaryRemedyCombo.getItems().clear();
        compareRemedyCombo.getItems().clear();

        try {
            for (Remedy r : masterDao.getAllRemedies()) {
                primaryRemedyCombo.getItems().add(new RemedyItem(r));
                compareRemedyCombo.getItems().add(new RemedyItem(r));
            }
            if (!primaryRemedyCombo.getItems().isEmpty()) {
                primaryRemedyCombo.getSelectionModel().select(0);
                if (primaryRemedyCombo.getItems().size() > 1) {
                    compareRemedyCombo.getSelectionModel().select(1);
                }
                loadRemedyText();
                loadCompareText();
            }
        } catch (Exception e) {
            log.error("Failed to load remedies into Materia Medica view", e);
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
            List<MateriaMedicaEntry> entries = masterDao.getMateriaMedica(remedyId);
            StringBuilder sb = new StringBuilder();

            for (MateriaMedicaEntry e : entries) {
                if (filterSection == null || filterSection.equals("ALL SECTIONS") || filterSection.equalsIgnoreCase(e.section())) {
                    sb.append("=== ").append(e.section()).append(" [").append(e.author()).append("] ===\n\n");
                    sb.append(e.bodyText()).append("\n\n");
                }
            }

            if (sb.isEmpty()) {
                sb.append("No specific text found for section: ").append(filterSection);
            }
            target.setText(sb.toString());

        } catch (Exception ex) {
            log.error("Failed to render materia medica text", ex);
            target.setText("Error loading text: " + ex.getMessage());
        }
    }
}
