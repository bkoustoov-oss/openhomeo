package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.repertory.RepertorizationEngine;
import org.openhomeo.core.repertory.RepertorizationResult;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * High-performance Interactive Repertorization Matrix Grid (RadarOpus / Synergy style).
 * Displays remedies along columns and rubrics along rows with color-coded grades.
 */
public class RepertoryMatrixView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(RepertoryMatrixView.class);

    private final MasterKnowledgeDao masterDao;
    private final SymptomClipboard clipboard;
    private final RepertorizationEngine engine;

    private final ComboBox<RepertorizationEngine.Algorithm> algoCombo;
    private final ComboBox<String> kingdomCombo;
    private final ComboBox<String> miasmCombo;
    private final Label statusLabel;
    private final ScrollPane matrixScrollPane;
    private final VBox matrixContent;

    public RepertoryMatrixView(MasterKnowledgeDao masterDao, SymptomClipboard clipboard, RepertorizationEngine engine) {
        this.masterDao = masterDao;
        this.clipboard = clipboard;
        this.engine = engine;

        // Top Toolbar
        ToolBar toolBar = new ToolBar();
        toolBar.setPadding(new Insets(8));

        algoCombo = new ComboBox<>();
        algoCombo.getItems().addAll(RepertorizationEngine.Algorithm.values());
        algoCombo.setValue(RepertorizationEngine.Algorithm.KENTIAN_SUM_OF_DEGREES);
        algoCombo.setOnAction(e -> refreshMatrix());

        kingdomCombo = new ComboBox<>();
        kingdomCombo.getItems().addAll("All Kingdoms", "Plant", "Mineral", "Animal", "Nosode");
        kingdomCombo.setValue("All Kingdoms");
        kingdomCombo.setOnAction(e -> refreshMatrix());

        miasmCombo = new ComboBox<>();
        miasmCombo.getItems().addAll("All Miasms", "PSORA", "SYCOSIS", "SYPHILIS", "TUBERCULAR");
        miasmCombo.setValue("All Miasms");
        miasmCombo.setOnAction(e -> refreshMatrix());

        Button refreshBtn = new Button("Calculate Analysis");
        refreshBtn.getStyleClass().addAll(Styles.ACCENT, Styles.BUTTON_OUTLINED);
        refreshBtn.setOnAction(e -> refreshMatrix());

        Button clearBtn = new Button("Clear Clipboard");
        clearBtn.setOnAction(e -> {
            clipboard.clear(clipboard.getActiveClipboardIndex());
            refreshMatrix();
        });

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add(Styles.TEXT_MUTED);

        toolBar.getItems().addAll(
                new Label("Algorithm:"), algoCombo,
                new Separator(),
                new Label("Kingdom:"), kingdomCombo,
                new Separator(),
                new Label("Miasm:"), miasmCombo,
                new Separator(),
                refreshBtn, clearBtn,
                new Separator(),
                statusLabel
        );

        setTop(toolBar);

        // Center Matrix Container
        matrixContent = new VBox(10);
        matrixContent.setPadding(new Insets(15));
        matrixContent.setAlignment(Pos.TOP_LEFT);

        matrixScrollPane = new ScrollPane(matrixContent);
        matrixScrollPane.setFitToWidth(true);
        setCenter(matrixScrollPane);

        refreshMatrix();
    }

    public void refreshMatrix() {
        try {
            List<SymptomClipboard.ClipboardItem> items = clipboard.getActiveItems();
            if (items.isEmpty()) {
                matrixContent.getChildren().clear();
                Label emptyLabel = new Label("Active clipboard is empty.\nDouble-click or drag rubrics from the Repertory Navigator to begin analysis.");
                emptyLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TITLE_4);
                emptyLabel.setPadding(new Insets(40));
                matrixContent.getChildren().add(emptyLabel);
                statusLabel.setText("No symptoms selected");
                return;
            }

            String kingdom = kingdomCombo.getValue().equals("All Kingdoms") ? null : kingdomCombo.getValue();
            String miasm = miasmCombo.getValue().equals("All Miasms") ? null : miasmCombo.getValue();

            RepertorizationEngine.Options opts = new RepertorizationEngine.Options(
                    algoCombo.getValue(), kingdom, null, miasm, true
            );

            RepertorizationResult res = engine.calculate(items, masterDao, opts);
            buildMatrixGrid(items, res);
            statusLabel.setText(String.format("Evaluated %d rubrics | %d candidate remedies",
                    res.totalRubricsEvaluated(), res.rankedRemedies().size()));

        } catch (Exception e) {
            log.error("Failed to calculate repertorization matrix", e);
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void buildMatrixGrid(List<SymptomClipboard.ClipboardItem> items, RepertorizationResult res) {
        matrixContent.getChildren().clear();

        GridPane grid = new GridPane();
        grid.setGridLinesVisible(true);
        grid.setHgap(0);
        grid.setVgap(0);

        // Row 0, Col 0: Corner Label
        Label corner = new Label("Rubrics \\ Remedies");
        corner.setFont(Font.font("System", FontWeight.BOLD, 12));
        corner.setPadding(new Insets(6, 12, 6, 12));
        corner.setMinWidth(260);
        grid.add(corner, 0, 0);

        // Render Top Remedy Headers (Up to top 30 remedies)
        int maxRemedies = Math.min(res.rankedRemedies().size(), 30);
        for (int c = 0; c < maxRemedies; c++) {
            RepertorizationResult.RemedyScore score = res.rankedRemedies().get(c);

            VBox remHeader = new VBox(2);
            remHeader.setAlignment(Pos.CENTER);
            remHeader.setPadding(new Insets(4, 8, 4, 8));
            remHeader.setMinWidth(48);

            Label nameLbl = new Label(score.remedyAbbr());
            nameLbl.setFont(Font.font("System", FontWeight.BOLD, 12));

            Label scoreLbl = new Label(String.format("%.0f (%d)", score.totalScore(), score.coveredSymptomsCount()));
            scoreLbl.setFont(Font.font("System", 10));
            scoreLbl.getStyleClass().add(Styles.TEXT_MUTED);

            if (score.isContraindicated()) {
                remHeader.setStyle("-fx-background-color: #fee2e2;"); // Red warning highlight
                nameLbl.setTextFill(Color.web("#991b1b"));
                nameLbl.setText(score.remedyAbbr() + " !");
            } else {
                remHeader.setStyle("-fx-background-color: #f8fafc;");
            }

            remHeader.getChildren().addAll(nameLbl, scoreLbl);
            grid.add(remHeader, c + 1, 0);
        }

        // Render Rubric Rows and Remedy Grade Cells
        for (int r = 0; r < items.size(); r++) {
            SymptomClipboard.ClipboardItem item = items.get(r);

            // Rubric label with multiplier
            HBox rubricCell = new HBox(6);
            rubricCell.setAlignment(Pos.CENTER_LEFT);
            rubricCell.setPadding(new Insets(4, 8, 4, 8));

            String prefix = item.isEliminative() ? "[ELIM] " : "";
            if (item.userWeight() > 1.0) prefix += "(" + (int)item.userWeight() + "x) ";

            Label rubricLbl = new Label(prefix + item.rubric().fullPath());
            rubricLbl.setFont(Font.font("System", 11));
            rubricLbl.setTooltip(new Tooltip(item.rubric().fullPath()));
            rubricCell.getChildren().add(rubricLbl);
            grid.add(rubricCell, 0, r + 1);

            // Remedy Grade Cells
            for (int c = 0; c < maxRemedies; c++) {
                RepertorizationResult.RemedyScore score = res.rankedRemedies().get(c);
                Integer grade = score.rubricGrades().get(item.rubric().id());

                StackPane cellPane = new StackPane();
                cellPane.setPadding(new Insets(4));
                cellPane.setAlignment(Pos.CENTER);

                if (grade != null && grade > 0) {
                    Text gradeText = createGradeText(grade);
                    cellPane.getChildren().add(gradeText);
                }

                grid.add(cellPane, c + 1, r + 1);
            }
        }

        matrixContent.getChildren().add(grid);
    }

    private Text createGradeText(int grade) {
        Text t = new Text(String.valueOf(grade));
        switch (grade) {
            case 1 -> {
                t.setFont(Font.font("System", FontWeight.NORMAL, FontPosture.REGULAR, 12));
                t.setFill(Color.web("#334155")); // Plain dark slate
            }
            case 2 -> {
                t.setFont(Font.font("System", FontWeight.NORMAL, FontPosture.ITALIC, 12));
                t.setFill(Color.web("#0284c7")); // Italics blue
            }
            case 3 -> {
                t.setFont(Font.font("System", FontWeight.BOLD, FontPosture.REGULAR, 13));
                t.setFill(Color.web("#dc2626")); // Bold red
            }
            case 4 -> {
                t.setFont(Font.font("System", FontWeight.BOLD, FontPosture.REGULAR, 13));
                t.setFill(Color.web("#7e22ce")); // Bold purple
                t.setUnderline(true);
            }
        }
        return t;
    }
}
