package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.image.ClinicalColorAnalyzer;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.ClinicalImage;
import org.openhomeo.core.model.ImageAnnotation;
import org.openhomeo.core.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Clinical Color Image Inspection & Longitudinal Comparison Workstation.
 * Features side-by-side comparison, CIE-L*a*b* analysis, CIEDE2000 Delta-E, and vector overlays.
 */
public class ClinicalImageViewer extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(ClinicalImageViewer.class);

    private final ClinicDao clinicDao;
    private final ImageVaultService imageVault;

    private final ComboBox<PatientItem> patientCombo;
    private final ListView<ImageListItem> galleryList;

    private final ImageView leftImageView;
    private final ImageView rightImageView;
    private final Label deltaELabel;
    private final Label erythemaLabel;
    private final Label interpretationLabel;

    private BufferedImage leftBufferedImg;
    private BufferedImage rightBufferedImg;

    public record PatientItem(Patient patient) {
        @Override
        public String toString() {
            return String.format("[%s] %s %s", patient.caseNumber(), patient.firstName(), patient.lastName());
        }
    }

    public record ImageListItem(ClinicalImage image) {
        @Override
        public String toString() {
            return String.format("%s - %s (%s)",
                    image.capturedAt().toString().substring(0, 10),
                    image.bodyPart(),
                    image.fileFormat());
        }
    }

    public ClinicalImageViewer(ClinicDao clinicDao, ImageVaultService imageVault) {
        this.clinicDao = clinicDao;
        this.imageVault = imageVault;

        // Top Toolbar
        ToolBar toolBar = new ToolBar();
        patientCombo = new ComboBox<>();
        patientCombo.setMinWidth(260);
        patientCombo.setPromptText("Select Patient...");
        patientCombo.setOnAction(e -> loadPatientImages());

        Button importBtn = new Button("Import Photograph");
        importBtn.getStyleClass().addAll(Styles.ACCENT, Styles.BUTTON_OUTLINED);
        importBtn.setOnAction(e -> handleImportPhoto());

        Button compareBtn = new Button("Compare Baseline vs Follow-up");
        compareBtn.setOnAction(e -> handleComparePhotos());

        toolBar.getItems().addAll(new Label("Patient:"), patientCombo, importBtn, new Separator(), compareBtn);
        setTop(toolBar);

        // Center Split: Left Gallery, Right Comparison Workspace
        SplitPane split = new SplitPane();

        // Left Gallery
        VBox galleryBox = new VBox(10);
        galleryBox.setPadding(new Insets(10));
        galleryBox.setMinWidth(280);
        galleryBox.setMaxWidth(350);

        Label galTitle = new Label("Patient Image Gallery");
        galTitle.getStyleClass().add(Styles.TITLE_4);

        galleryList = new ListView<>();
        galleryList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        galleryList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) displaySelectedImage(newV.image());
        });

        galleryBox.getChildren().addAll(galTitle, galleryList);

        // Right Comparison Pane
        VBox compareBox = new VBox(15);
        compareBox.setPadding(new Insets(15));
        compareBox.setAlignment(Pos.TOP_CENTER);

        HBox viewerHBox = new HBox(20);
        viewerHBox.setAlignment(Pos.CENTER);

        VBox leftPane = new VBox(6);
        leftPane.setAlignment(Pos.CENTER);
        Label leftTitle = new Label("Baseline Image (Visit 1)");
        leftTitle.getStyleClass().add(Styles.TEXT_MUTED);
        leftImageView = new ImageView();
        leftImageView.setFitWidth(360);
        leftImageView.setFitHeight(360);
        leftImageView.setPreserveRatio(true);
        leftImageView.setStyle("-fx-border-color: #cbd5e1; -fx-border-width: 1;");
        leftPane.getChildren().addAll(leftTitle, leftImageView);

        VBox rightPane = new VBox(6);
        rightPane.setAlignment(Pos.CENTER);
        Label rightTitle = new Label("Follow-up Image (Visit 2)");
        rightTitle.getStyleClass().add(Styles.TEXT_MUTED);
        rightImageView = new ImageView();
        rightImageView.setFitWidth(360);
        rightImageView.setFitHeight(360);
        rightImageView.setPreserveRatio(true);
        rightImageView.setStyle("-fx-border-color: #cbd5e1; -fx-border-width: 1;");
        rightPane.getChildren().addAll(rightTitle, rightImageView);

        viewerHBox.getChildren().addAll(leftPane, rightPane);

        // Quantitative Metrics Card
        VBox metricsCard = new VBox(8);
        metricsCard.setPadding(new Insets(15));
        metricsCard.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

        Label metricsHeader = new Label("Clinical Color Progression Metrics (CIEDE2000 ISO/CIE)");
        metricsHeader.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 13));

        deltaELabel = new Label("Delta-E (Total Color Difference): --");
        erythemaLabel = new Label("Erythema Shift (Delta a*): --");
        interpretationLabel = new Label("Clinical Interpretation: Select two images from gallery and click Compare.");
        interpretationLabel.getStyleClass().add(Styles.TEXT_BOLD);

        metricsCard.getChildren().addAll(metricsHeader, deltaELabel, erythemaLabel, interpretationLabel);

        compareBox.getChildren().addAll(viewerHBox, metricsCard);

        split.getItems().addAll(galleryBox, compareBox);
        split.setDividerPositions(0.28);
        setCenter(split);

        loadPatients();
    }

    private void loadPatients() {
        patientCombo.getItems().clear();
        try {
            for (Patient p : clinicDao.listPatients(null)) {
                patientCombo.getItems().add(new PatientItem(p));
            }
            if (!patientCombo.getItems().isEmpty()) {
                patientCombo.getSelectionModel().select(0);
                loadPatientImages();
            }
        } catch (Exception e) {
            log.error("Failed to load patients for image viewer", e);
        }
    }

    private void loadPatientImages() {
        galleryList.getItems().clear();
        PatientItem sel = patientCombo.getValue();
        if (sel == null) return;

        try {
            List<ClinicalImage> images = clinicDao.getImagesForPatient(sel.patient.id());
            for (ClinicalImage img : images) {
                galleryList.getItems().add(new ImageListItem(img));
            }
            if (!galleryList.getItems().isEmpty()) {
                galleryList.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            log.error("Failed to load patient images", e);
        }
    }

    private void displaySelectedImage(ClinicalImage img) {
        try {
            BufferedImage bImg = imageVault.retrieveBufferedImage(img.contentHash());
            if (bImg != null) {
                leftBufferedImg = bImg;
                Image fxImg = SwingFXUtils.toFXImage(bImg, null);
                leftImageView.setImage(fxImg);
            }
        } catch (Exception e) {
            log.error("Failed to display image", e);
        }
    }

    private void handleImportPhoto() {
        PatientItem sel = patientCombo.getValue();
        if (sel == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Import Clinical Photograph");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.bmp")
        );

        File file = fc.showOpenDialog(getScene().getWindow());
        if (file == null) return;

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            ImageVaultService.StoreResult storeRes = imageVault.storeImage(bytes, "PNG");

            BufferedImage bImg = ImageIO.read(file);
            ClinicalColorAnalyzer.LabStats stats = ClinicalColorAnalyzer.analyzeRegion(bImg, null);

            ClinicalImage img = new ClinicalImage(
                    UUID.randomUUID().toString(), sel.patient.id(), null,
                    storeRes.contentHash(), storeRes.fileFormat(), "Skin Lesion",
                    "D65_Standardized", stats.meanL(), stats.meanA(), stats.meanB(), 0.0,
                    "Imported photograph: " + file.getName(), Instant.now(), List.of()
            );

            clinicDao.saveClinicalImage(img);
            loadPatientImages();

        } catch (Exception e) {
            log.error("Failed to import photo", e);
        }
    }

    private void handleComparePhotos() {
        List<ImageListItem> sel = galleryList.getSelectionModel().getSelectedItems();
        if (sel.size() < 2) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please select at least 2 photographs from the gallery to compare.");
            alert.showAndWait();
            return;
        }

        try {
            ClinicalImage img1 = sel.get(0).image();
            ClinicalImage img2 = sel.get(1).image();

            BufferedImage bImg1 = imageVault.retrieveBufferedImage(img1.contentHash());
            BufferedImage bImg2 = imageVault.retrieveBufferedImage(img2.contentHash());

            leftBufferedImg = bImg1;
            rightBufferedImg = bImg2;

            leftImageView.setImage(SwingFXUtils.toFXImage(bImg1, null));
            rightImageView.setImage(SwingFXUtils.toFXImage(bImg2, null));

            ClinicalColorAnalyzer.LabStats s1 = ClinicalColorAnalyzer.analyzeRegion(bImg1, null);
            ClinicalColorAnalyzer.LabStats s2 = ClinicalColorAnalyzer.analyzeRegion(bImg2, null);

            ClinicalColorAnalyzer.ComparisonResult comp = ClinicalColorAnalyzer.compareProgression(s1, s2);

            deltaELabel.setText(String.format("Delta-E (CIEDE2000): %.2f", comp.deltaE00()));
            erythemaLabel.setText(String.format("Erythema Shift (Delta a*): %.2f | Lightness Shift (Delta L*): %.2f",
                    comp.erythemaDeltaA(), comp.lightnessDeltaL()));
            interpretationLabel.setText("Clinical Interpretation: " + comp.clinicalInterpretation());

        } catch (Exception e) {
            log.error("Failed to execute color comparison", e);
        }
    }
}
