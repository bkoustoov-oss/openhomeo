package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.*;
import org.openhomeo.core.pdf.PrescriptionPdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Patient Records & 360° Longitudinal Clinical Dossier Module.
 * Features:
 * 1. Filtered Master Patient Directory (Search by Name, Case No, Phone, Category, Gender, Miasm, Constitution)
 * 2. Demographic & Constitutional Profile Card with primary/follow-up photographs
 * 3. Longitudinal Consultation Trajectory with visual remedy changes and potency escalation history
 * 4. In-Software Structured Prescription Slip Viewer (viewing past prescriptions directly in-app)
 * 5. Pharmacy Dispensary Invoices & Financial Account Ledger
 */
public class PatientDossierView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(PatientDossierView.class);

    private final ClinicDao clinicDao;
    private final ImageVaultService imageVault;
    private final PrescriptionPdfService pdfService = new PrescriptionPdfService();

    // Callbacks for inter-tab navigation
    private Consumer<Patient> onConsultationRequested;
    private Consumer<Patient> onLogicAiChatRequested;

    // UI Components - Left
    private final ObservableList<Patient> patientList = FXCollections.observableArrayList();
    private TableView<Patient> patientTable;
    private TextField searchField;
    private ComboBox<String> filterCategoryCombo;
    private ComboBox<String> filterGenderCombo;
    private ComboBox<String> filterMiasmCombo;
    private ComboBox<String> filterConstitutionCombo;
    private Label totalPatientsLabel;

    // UI Components - Right (Dossier)
    private VBox dossierContainer;
    private Patient activePatient;

    public PatientDossierView(ClinicDao clinicDao, ImageVaultService imageVault) {
        this.clinicDao = clinicDao;
        this.imageVault = imageVault;

        setPadding(new Insets(10));
        setLeft(createPatientDirectoryPane());
        setCenter(createDossierPresentationPane());

        refreshPatientList();
    }

    public void setOnConsultationRequested(Consumer<Patient> onConsultationRequested) {
        this.onConsultationRequested = onConsultationRequested;
    }

    public void setOnLogicAiChatRequested(Consumer<Patient> onLogicAiChatRequested) {
        this.onLogicAiChatRequested = onLogicAiChatRequested;
    }

    // =========================================================================
    // LEFT PANE: MASTER PATIENT DIRECTORY & ADVANCED FILTERS
    // =========================================================================

    private Node createPatientDirectoryPane() {
        VBox left = new VBox(10);
        left.setPrefWidth(390);
        left.setPadding(new Insets(6, 12, 6, 6));

        // Header with Register Button
        HBox topBox = new HBox(8);
        topBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("👥 Patient Roster");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));

        totalPatientsLabel = new Label("Total: 0");
        totalPatientsLabel.getStyleClass().add(Styles.TEXT_MUTED);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnNew = new Button("+ Register");
        btnNew.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnNew.setOnAction(e -> showNewPatientDialog());

        topBox.getChildren().addAll(title, totalPatientsLabel, spacer, btnNew);

        // Search Bar
        searchField = new TextField();
        searchField.setPromptText("🔍 Search Case No, Name, Phone, Address...");
        searchField.textProperty().addListener((obs, o, n) -> executeFilteredSearch());

        // Filter Grid
        GridPane filterGrid = new GridPane();
        filterGrid.setHgap(6);
        filterGrid.setVgap(6);

        filterCategoryCombo = new ComboBox<>(FXCollections.observableArrayList(
                "All Categories", "Pediatric (< 12 yrs)", "Teen (12-18 yrs)", "Adult (18-60 yrs)", "Geriatric (> 60 yrs)"
        ));
        filterCategoryCombo.getSelectionModel().selectFirst();
        filterCategoryCombo.setMaxWidth(Double.MAX_VALUE);
        filterCategoryCombo.setOnAction(e -> executeFilteredSearch());

        filterGenderCombo = new ComboBox<>(FXCollections.observableArrayList("All Genders", "Male", "Female", "Other"));
        filterGenderCombo.getSelectionModel().selectFirst();
        filterGenderCombo.setMaxWidth(Double.MAX_VALUE);
        filterGenderCombo.setOnAction(e -> executeFilteredSearch());

        filterMiasmCombo = new ComboBox<>(FXCollections.observableArrayList(
                "All Miasms", "PSORA", "SYCOSIS", "SYPHILIS", "TUBERCULAR"
        ));
        filterMiasmCombo.getSelectionModel().selectFirst();
        filterMiasmCombo.setMaxWidth(Double.MAX_VALUE);
        filterMiasmCombo.setOnAction(e -> executeFilteredSearch());

        filterConstitutionCombo = new ComboBox<>(FXCollections.observableArrayList(
                "All Constitutions", "Carbonic", "Phosphoric", "Fluoric", "Sulphuric", "Carbo-nitrogenoid", "General"
        ));
        filterConstitutionCombo.getSelectionModel().selectFirst();
        filterConstitutionCombo.setMaxWidth(Double.MAX_VALUE);
        filterConstitutionCombo.setOnAction(e -> executeFilteredSearch());

        filterGrid.addRow(0, filterCategoryCombo, filterGenderCombo);
        filterGrid.addRow(1, filterMiasmCombo, filterConstitutionCombo);
        GridPane.setHgrow(filterCategoryCombo, Priority.ALWAYS);
        GridPane.setHgrow(filterGenderCombo, Priority.ALWAYS);
        GridPane.setHgrow(filterMiasmCombo, Priority.ALWAYS);
        GridPane.setHgrow(filterConstitutionCombo, Priority.ALWAYS);

        // Table
        patientTable = new TableView<>(patientList);
        patientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Patient, String> colCase = new TableColumn<>("Case No");
        colCase.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().caseNumber()));
        colCase.setMaxWidth(110);

        TableColumn<Patient, String> colName = new TableColumn<>("Patient Name");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));

        TableColumn<Patient, String> colDemog = new TableColumn<>("Age/Gen");
        colDemog.setCellValueFactory(c -> {
            Patient p = c.getValue();
            String age = p.dob() != null ? String.valueOf(Period.between(p.dob(), LocalDate.now()).getYears()) : "N/A";
            String g = p.gender() != null && !p.gender().isBlank() ? p.gender().substring(0, 1) : "?";
            return new SimpleStringProperty(age + "/" + g);
        });
        colDemog.setMaxWidth(70);

        TableColumn<Patient, String> colMiasm = new TableColumn<>("Miasm");
        colMiasm.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().miasmaticBackground() != null ? c.getValue().miasmaticBackground() : "PSORA"));
        colMiasm.setMaxWidth(90);

        patientTable.getColumns().addAll(colCase, colName, colDemog, colMiasm);

        patientTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadPatientDossier(newVal);
            }
        });

        VBox.setVgrow(patientTable, Priority.ALWAYS);
        left.getChildren().addAll(topBox, searchField, filterGrid, patientTable);
        return left;
    }

    // =========================================================================
    // RIGHT PANE: COMPREHENSIVE 360° CLINICAL DOSSIER
    // =========================================================================

    private Node createDossierPresentationPane() {
        dossierContainer = new VBox();
        dossierContainer.setPadding(new Insets(6, 6, 6, 12));
        dossierContainer.setAlignment(Pos.CENTER);

        Label emptyMsg = new Label("👈 Select a patient from the roster to view complete 360° clinical dossier");
        emptyMsg.setFont(Font.font("System", FontWeight.NORMAL, 14));
        emptyMsg.getStyleClass().add(Styles.TEXT_MUTED);

        dossierContainer.getChildren().add(emptyMsg);
        return new ScrollPane(dossierContainer);
    }

    public void selectPatient(String patientId) {
        if (patientId == null || patientId.isBlank()) return;
        for (Patient p : patientList) {
            if (p.id().equals(patientId) || p.caseNumber().equalsIgnoreCase(patientId)) {
                patientTable.getSelectionModel().select(p);
                patientTable.scrollTo(p);
                loadPatientDossier(p);
                break;
            }
        }
    }

    public void refreshPatientList() {
        executeFilteredSearch();
    }

    private void executeFilteredSearch() {
        String q = searchField != null ? searchField.getText().trim() : "";
        String cat = filterCategoryCombo != null ? filterCategoryCombo.getValue() : "All Categories";
        String gen = filterGenderCombo != null ? filterGenderCombo.getValue() : "All Genders";
        String miasm = filterMiasmCombo != null ? filterMiasmCombo.getValue() : "All Miasms";
        String constType = filterConstitutionCombo != null ? filterConstitutionCombo.getValue() : "All Constitutions";

        try {
            List<Patient> results = clinicDao.searchPatientsFiltered(
                    q,
                    cat.startsWith("All") ? "ALL" : cat,
                    gen.startsWith("All") ? "ALL" : gen,
                    cat.startsWith("All") ? "ALL" : cat,
                    constType.startsWith("All") ? "ALL" : constType,
                    miasm.startsWith("All") ? "ALL" : miasm
            );
            patientList.setAll(results);
            if (totalPatientsLabel != null) {
                totalPatientsLabel.setText("Total: " + results.size());
            }
            if (!results.isEmpty() && activePatient == null) {
                patientTable.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            log.error("Failed to filter patients", e);
        }
    }

    private void loadPatientDossier(Patient p) {
        this.activePatient = p;
        dossierContainer.getChildren().clear();
        dossierContainer.setAlignment(Pos.TOP_LEFT);
        dossierContainer.setSpacing(14);

        // 1. Patient Dossier Header Banner
        dossierContainer.getChildren().add(createDossierHeaderBanner(p));

        // 2. Tabbed 360° Longitudinal Sub-Panels
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tabProfile = new Tab("👤 Profile & Vitals", createProfileAndVitalsPane(p));
        Tab tabTrajectory = new Tab("📜 Case Trajectory & Remedy Changes", createCaseTrajectoryPane(p));
        Tab tabPrescriptionSlip = new Tab("📄 In-App Prescription Slip", createPrescriptionSlipViewerPane(p));
        Tab tabBills = new Tab("💳 Pharmacy Invoices & Ledger", createPharmacyLedgerPane(p));

        tabPane.getTabs().addAll(tabProfile, tabTrajectory, tabPrescriptionSlip, tabBills);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        dossierContainer.getChildren().add(tabPane);
    }

    private Node createDossierHeaderBanner(Patient p) {
        VBox banner = new VBox(6);
        banner.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 12px 16px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        HBox line1 = new HBox(12);
        line1.setAlignment(Pos.CENTER_LEFT);

        Label nameLbl = new Label(p.getFullName());
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 18));
        nameLbl.setStyle("-fx-text-fill: -color-accent-fg;");

        Label caseLbl = new Label("[" + p.caseNumber() + "]");
        caseLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        caseLbl.getStyleClass().add(Styles.TEXT_MUTED);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnConsult = new Button("🩺 Open Case-Taking");
        btnConsult.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnConsult.setOnAction(e -> {
            if (onConsultationRequested != null) {
                onConsultationRequested.accept(p);
            }
        });

        Button btnLogicAi = new Button("🧠 Chat with Logic AI");
        btnLogicAi.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);
        btnLogicAi.setOnAction(e -> {
            if (onLogicAiChatRequested != null) {
                onLogicAiChatRequested.accept(p);
            }
        });

        line1.getChildren().addAll(nameLbl, caseLbl, spacer, btnConsult, btnLogicAi);

        // Line 2: Demographic Badges
        HBox line2 = new HBox(10);
        line2.setAlignment(Pos.CENTER_LEFT);

        int age = p.dob() != null ? Period.between(p.dob(), LocalDate.now()).getYears() : 0;
        String dobStr = p.dob() != null ? p.dob().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")) : "N/A";

        line2.getChildren().addAll(
                createBadge("Age", age + " yrs (" + dobStr + ")"),
                createBadge("Gender", p.gender() != null ? p.gender() : "Unknown"),
                createBadge("Blood Group", p.bloodGroup() != null ? p.bloodGroup() : "Unknown"),
                createBadge("Constitution", p.constitutionalType() != null ? p.constitutionalType() : "Carbonic"),
                createBadge("Miasm", p.miasmaticBackground() != null ? p.miasmaticBackground() : "PSORA")
        );

        banner.getChildren().addAll(line1, line2);
        return banner;
    }

    private Node createBadge(String label, String value) {
        HBox b = new HBox(4);
        b.setStyle("-fx-background-color: rgba(2, 132, 199, 0.08); -fx-border-color: rgba(2, 132, 199, 0.25); -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-border-radius: 4px;");
        Label l = new Label(label + ":");
        l.setStyle("-fx-font-size: 0.8em; -fx-text-fill: -color-fg-muted; -fx-font-weight: bold;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 0.8em; -fx-font-weight: bold;");
        b.getChildren().addAll(l, v);
        return b;
    }

    // =========================================================================
    // SUB-TAB 1: PROFILE, CONTACT, VITALS & CLINICAL PHOTOS
    // =========================================================================

    private Node createProfileAndVitalsPane(Patient p) {
        VBox vbox = new VBox(14);
        vbox.setPadding(new Insets(12));

        // Two columns: Contact & Demographics (Left), Clinical Photos (Right)
        HBox row = new HBox(16);

        // Left Card: Personal Demographics & Contact
        VBox contactCard = new VBox(10);
        contactCard.setPrefWidth(420);
        contactCard.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 14px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label cardTitle = new Label("📋 Personal & Contact Record");
        cardTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        grid.addRow(0, new Label("Phone / Contact:"), new Label(p.phone() != null && !p.phone().isBlank() ? p.phone() : "Not provided"));
        grid.addRow(1, new Label("Email Address:"), new Label(p.email() != null && !p.email().isBlank() ? p.email() : "Not provided"));
        grid.addRow(2, new Label("Residential Address:"), new Label(p.address() != null && !p.address().isBlank() ? p.address() : "Not provided"));
        grid.addRow(3, new Label("Occupation:"), new Label(p.occupation() != null && !p.occupation().isBlank() ? p.occupation() : "General"));
        grid.addRow(4, new Label("Date of Birth:"), new Label(p.dob() != null ? p.dob().toString() : "N/A"));
        grid.addRow(5, new Label("Registered On:"), new Label(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm").withZone(ZoneId.systemDefault()).format(p.createdAt())));

        contactCard.getChildren().addAll(cardTitle, new Separator(), grid);

        // Right Card: Clinical Photographs & Dermascopic Images
        VBox photoCard = new VBox(10);
        photoCard.setPrefWidth(460);
        photoCard.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 14px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label photoTitle = new Label("📸 Clinical Dermascopic & Baseline Photographs");
        photoTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        HBox photoGallery = new HBox(12);
        photoGallery.setAlignment(Pos.CENTER_LEFT);

        try {
            List<ClinicalImage> images = clinicDao.getImagesForPatient(p.id());
            if (images.isEmpty()) {
                Label noImg = new Label("No clinical photographs captured yet.\nUse 'Clinical Photographs & CIE-LAB' tab to record dermascopic progress.");
                noImg.getStyleClass().add(Styles.TEXT_MUTED);
                photoCard.getChildren().addAll(photoTitle, new Separator(), noImg);
            } else {
                for (ClinicalImage img : images) {
                    VBox imgBox = new VBox(4);
                    imgBox.setAlignment(Pos.CENTER);
                    imgBox.setStyle("-fx-border-color: -color-border-subtle; -fx-padding: 4px; -fx-background-radius: 4px;");

                    ImageView iv = new ImageView();
                    iv.setFitWidth(100);
                    iv.setFitHeight(100);
                    iv.setPreserveRatio(true);

                    try {
                        byte[] bytes = imageVault.retrieveImage(img.contentHash());
                        iv.setImage(new Image(new ByteArrayInputStream(bytes)));
                    } catch (Exception ex) {
                        log.warn("Could not load image thumbnail: {}", ex.getMessage());
                    }

                    Label dateLbl = new Label(DateTimeFormatter.ofPattern("dd-MMM-yy").withZone(ZoneId.systemDefault()).format(img.capturedAt()));
                    dateLbl.setStyle("-fx-font-size: 0.75em;");
                    Label partLbl = new Label(img.bodyPart());
                    partLbl.setStyle("-fx-font-size: 0.75em; -fx-font-weight: bold;");

                    imgBox.getChildren().addAll(iv, partLbl, dateLbl);
                    photoGallery.getChildren().add(imgBox);
                    if (photoGallery.getChildren().size() >= 4) break;
                }
                photoCard.getChildren().addAll(photoTitle, new Separator(), photoGallery);
            }
        } catch (SQLException e) {
            photoCard.getChildren().addAll(photoTitle, new Separator(), new Label("Error loading images: " + e.getMessage()));
        }

        row.getChildren().addAll(contactCard, photoCard);

        // Past Medications & Remedy Superseding History
        VBox historyCard = new VBox(8);
        historyCard.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 14px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        Label histTitle = new Label("💊 Historical Suppressions & Allopathic Drug Background");
        histTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        TableView<PastMedication> pastMedTable = new TableView<>();
        pastMedTable.setPrefHeight(130);
        pastMedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PastMedication, String> colMed = new TableColumn<>("Medication / Drug");
        colMed.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().drugName()));

        TableColumn<PastMedication, String> colCat = new TableColumn<>("Category");
        colCat.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category()));

        TableColumn<PastMedication, String> colDur = new TableColumn<>("Duration");
        colDur.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().duration()));

        TableColumn<PastMedication, String> colImp = new TableColumn<>("Clinical Impression / Suppression Effect");
        colImp.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().suppressedSymptoms()));

        TableColumn<PastMedication, String> colAnt = new TableColumn<>("Antidotal Remedies");
        colAnt.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().antidoteRemedy()));

        pastMedTable.getColumns().addAll(colMed, colCat, colDur, colImp, colAnt);

        try {
            List<PastMedication> pms = clinicDao.getPastMedicationsByPatient(p.id());
            pastMedTable.setItems(FXCollections.observableArrayList(pms));
        } catch (Exception ex) {
            log.warn("Could not query past medications: {}", ex.getMessage());
        }

        historyCard.getChildren().addAll(histTitle, pastMedTable);
        vbox.getChildren().addAll(row, historyCard);
        return vbox;
    }

    // =========================================================================
    // SUB-TAB 2: LONGITUDINAL CASE TRAJECTORY & REMEDY CHANGES
    // =========================================================================

    private Node createCaseTrajectoryPane(Patient p) {
        VBox vbox = new VBox(12);
        vbox.setPadding(new Insets(12));

        Label title = new Label("📜 Longitudinal Case Trajectory & Remedy Evolution");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));

        // Visual Remedy Chain Summary Banner
        HBox remedyChainBox = new HBox(8);
        remedyChainBox.setAlignment(Pos.CENTER_LEFT);
        remedyChainBox.setStyle("-fx-background-color: rgba(2, 132, 199, 0.08); -fx-padding: 10px 14px; -fx-border-color: rgba(2, 132, 199, 0.25); -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label chainTitle = new Label("Remedy Trajectory Chain:");
        chainTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
        chainTitle.setStyle("-fx-text-fill: -color-accent-fg;");
        remedyChainBox.getChildren().add(chainTitle);

        List<Consultation> consults;
        try {
            consults = clinicDao.getConsultationsForPatient(p.id());
        } catch (SQLException e) {
            consults = List.of();
        }

        // Build chronological chain (oldest to newest)
        List<Consultation> chrono = new ArrayList<>(consults);
        Collections.reverse(chrono);

        if (chrono.isEmpty()) {
            remedyChainBox.getChildren().add(new Label("No past consultations recorded for this patient."));
        } else {
            for (int i = 0; i < chrono.size(); i++) {
                Consultation c = chrono.get(i);
                String remSummary = !c.prescriptions().isEmpty()
                        ? c.prescriptions().get(0).remedyName() + " " + c.prescriptions().get(0).potency()
                        : "Placebo / Observation";

                Label badge = new Label(remSummary);
                badge.setStyle("-fx-background-color: -color-bg-default; -fx-padding: 3px 8px; -fx-border-color: -color-border-subtle; -fx-border-radius: 4px; -fx-font-weight: bold; -fx-font-size: 0.85em;");
                remedyChainBox.getChildren().add(badge);

                if (i < chrono.size() - 1) {
                    Label arrow = new Label("➔");
                    arrow.setStyle("-fx-text-fill: -color-accent-fg; -fx-font-weight: bold;");
                    remedyChainBox.getChildren().add(arrow);
                }
            }
        }

        // Consultations Accordion / Chronological Table
        VBox consultsList = new VBox(10);
        for (Consultation c : consults) {
            VBox card = new VBox(6);
            card.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 12px; -fx-border-color: -color-border-subtle; -fx-border-radius: 6px;");

            HBox cardHead = new HBox(10);
            cardHead.setAlignment(Pos.CENTER_LEFT);

            String dateStr = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm").withZone(ZoneId.systemDefault()).format(c.consultDate());
            Label dateLbl = new Label("📅 " + dateStr);
            dateLbl.setFont(Font.font("System", FontWeight.BOLD, 13));

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label chiefLbl = new Label("Chief Complaint: " + c.chiefComplaint());
            chiefLbl.setFont(Font.font("System", FontWeight.BOLD, 12));
            chiefLbl.setStyle("-fx-text-fill: -color-accent-fg;");

            cardHead.getChildren().addAll(dateLbl, sp, chiefLbl);

            // L-S-M-C Details
            GridPane lsmcGrid = new GridPane();
            lsmcGrid.setHgap(10);
            lsmcGrid.setVgap(4);
            lsmcGrid.addRow(0, new Label("Location (L):"), new Label(c.locationNotes() != null && !c.locationNotes().isBlank() ? c.locationNotes() : "—"));
            lsmcGrid.addRow(1, new Label("Sensation (S):"), new Label(c.sensationNotes() != null && !c.sensationNotes().isBlank() ? c.sensationNotes() : "—"));
            lsmcGrid.addRow(2, new Label("Modalities (M):"), new Label(c.modalityNotes() != null && !c.modalityNotes().isBlank() ? c.modalityNotes() : "—"));
            lsmcGrid.addRow(3, new Label("Concomitant (C):"), new Label(c.concomitantNotes() != null && !c.concomitantNotes().isBlank() ? c.concomitantNotes() : "—"));

            // Prescriptions Table for this consultation
            VBox rxBox = new VBox(4);
            rxBox.setStyle("-fx-padding: 6px 0 0 0;");
            Label rxTitle = new Label("Prescriptions (℞):");
            rxTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
            rxBox.getChildren().add(rxTitle);

            if (c.prescriptions().isEmpty()) {
                rxBox.getChildren().add(new Label("No remedies dispensed (Sac Lac / Placebo)."));
            } else {
                for (Prescription rx : c.prescriptions()) {
                    Label rxItem = new Label(String.format("• %s | Potency: %s (%s) | Posology: %s | Repetition: %s",
                            rx.remedyName(), rx.potency(), rx.scale(),
                            rx.posologyInstructions() != null ? rx.posologyInstructions() : "—",
                            rx.repetitionFrequency() != null ? rx.repetitionFrequency() : "—"));
                    rxItem.setStyle("-fx-font-weight: bold; -fx-text-fill: #10b981;");
                    rxBox.getChildren().add(rxItem);
                }
            }

            card.getChildren().addAll(cardHead, new Separator(), lsmcGrid, rxBox);
            consultsList.getChildren().add(card);
        }

        vbox.getChildren().addAll(title, remedyChainBox, new ScrollPane(consultsList));
        return vbox;
    }

    // =========================================================================
    // SUB-TAB 3: IN-SOFTWARE STRUCTURED PRESCRIPTION SLIP VIEWER
    // =========================================================================

    private Node createPrescriptionSlipViewerPane(Patient p) {
        VBox vbox = new VBox(12);
        vbox.setPadding(new Insets(12));

        // Selector Bar
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label lblSelect = new Label("Select Historical Consultation:");
        lblSelect.setFont(Font.font("System", FontWeight.BOLD, 12));

        ComboBox<Consultation> consultCombo = new ComboBox<>();
        consultCombo.setPrefWidth(320);

        List<Consultation> consults;
        try {
            consults = clinicDao.getConsultationsForPatient(p.id());
        } catch (SQLException e) {
            consults = List.of();
        }
        consultCombo.setItems(FXCollections.observableArrayList(consults));
        consultCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Consultation c) {
                if (c == null) return "";
                String d = DateTimeFormatter.ofPattern("dd-MMM-yyyy").withZone(ZoneId.systemDefault()).format(c.consultDate());
                return d + " — " + c.chiefComplaint();
            }
            @Override
            public Consultation fromString(String string) { return null; }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnPrint = new Button("🖨️ Export PDF Prescription");
        btnPrint.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);

        topBar.getChildren().addAll(lblSelect, consultCombo, spacer, btnPrint);

        // Rendered Prescription Slip Card
        VBox slipCard = new VBox(12);
        slipCard.setMaxWidth(750);
        slipCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 24px; -fx-border-color: #cbd5e1; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 8, 0, 0, 2);");

        Runnable renderSlip = () -> {
            Consultation sel = consultCombo.getValue();
            slipCard.getChildren().clear();

            if (sel == null) {
                Label noSel = new Label("Select a consultation above to display its prescription slip.");
                noSel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px;");
                slipCard.getChildren().add(noSel);
                return;
            }

            // Dynamic Institution Profile & Attending Doctor
            ClinicInstitutionProfile inst;
            try {
                inst = clinicDao.getInstitutionProfile();
            } catch (Exception ex) {
                inst = ClinicInstitutionProfile.defaultProfile();
            }

            Doctor attendingDoctor = null;
            if (sel.doctorId() != null && !sel.doctorId().isBlank()) {
                try {
                    attendingDoctor = clinicDao.getDoctorById(sel.doctorId()).orElse(null);
                } catch (Exception ignored) {}
            }
            if (attendingDoctor == null) {
                attendingDoctor = new Doctor(
                        "doc-inst",
                        inst.chiefConsultantName(),
                        inst.chiefConsultantRegNo(),
                        inst.chiefConsultantQualifications(),
                        "Chief Consulting Homeopath",
                        inst.institutionName(),
                        inst.address(),
                        inst.phone(),
                        inst.email(),
                        "Consulting Physician",
                        true,
                        Instant.now()
                );
            }

            final Doctor finalAttendingDoctor = attendingDoctor;

            // Clinic Letterhead
            VBox headerBox = new VBox(3);
            headerBox.setAlignment(Pos.CENTER);
            Label clinicTitle = new Label(inst.institutionName().toUpperCase());
            clinicTitle.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
            clinicTitle.setStyle("-fx-text-fill: #0f172a;");

            String docSubText = attendingDoctor.fullName() +
                    (attendingDoctor.qualification() != null && !attendingDoctor.qualification().isBlank() ? ", " + attendingDoctor.qualification() : "") +
                    (attendingDoctor.registrationNo() != null && !attendingDoctor.registrationNo().isBlank() ? " | Reg. No: " + attendingDoctor.registrationNo() : "");
            Label docSubtitle = new Label(docSubText);
            docSubtitle.setFont(Font.font("System", FontWeight.NORMAL, 12));
            docSubtitle.setStyle("-fx-text-fill: #334155;");

            String addrSubText = inst.address() +
                    (inst.phone() != null && !inst.phone().isBlank() ? " | Tel: " + inst.phone() : "") +
                    (inst.email() != null && !inst.email().isBlank() ? " | " + inst.email() : "");
            Label addressSubtitle = new Label(addrSubText);
            addressSubtitle.setFont(Font.font("System", FontWeight.NORMAL, 11));
            addressSubtitle.setStyle("-fx-text-fill: #64748b;");

            headerBox.getChildren().addAll(clinicTitle, docSubtitle, addressSubtitle);

            // Separator Line
            Separator line = new Separator();
            line.setStyle("-fx-background-color: #0284c7;");

            // Patient Bar
            GridPane pBar = new GridPane();
            pBar.setHgap(16);
            pBar.setVgap(4);
            pBar.setStyle("-fx-padding: 8px 0; -fx-font-size: 12px;");

            int age = p.dob() != null ? Period.between(p.dob(), LocalDate.now()).getYears() : 0;
            String consultDateStr = DateTimeFormatter.ofPattern("dd-MMM-yyyy").withZone(ZoneId.systemDefault()).format(sel.consultDate());

            Label pName = new Label("Patient: " + p.getFullName());
            pName.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");
            Label pCase = new Label("Case No: " + p.caseNumber());
            pCase.setStyle("-fx-text-fill: #334155;");
            Label pAgeGen = new Label("Age/Sex: " + age + " yrs / " + p.gender());
            pAgeGen.setStyle("-fx-text-fill: #334155;");
            Label pDate = new Label("Date: " + consultDateStr);
            pDate.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");

            pBar.addRow(0, pName, pCase, pAgeGen, pDate);

            // Diagnosis / Complaint
            VBox diagBox = new VBox(2);
            diagBox.setStyle("-fx-background-color: #f8fafc; -fx-padding: 6px 10px; -fx-border-color: #e2e8f0; -fx-border-radius: 4px;");
            Label diagLbl = new Label("Clinical Presentation / Diagnosis: " + sel.chiefComplaint());
            diagLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #0369a1; -fx-font-size: 12px;");
            diagBox.getChildren().add(diagLbl);

            // Rx Symbol
            Label rxSymbol = new Label("℞");
            rxSymbol.setFont(Font.font("Georgia", FontWeight.BOLD, 22));
            rxSymbol.setStyle("-fx-text-fill: #0284c7; -fx-padding: 4px 0 0 0;");

            // Prescriptions Table
            VBox rxTable = new VBox(8);
            if (sel.prescriptions().isEmpty()) {
                Label noRx = new Label("Sac. Lac. (Sugar of Milk) — Single powder at bedtime under observation.");
                noRx.setStyle("-fx-text-fill: #334155; -fx-font-style: italic;");
                rxTable.getChildren().add(noRx);
            } else {
                for (int i = 0; i < sel.prescriptions().size(); i++) {
                    Prescription r = sel.prescriptions().get(i);
                    VBox itemBox = new VBox(2);
                    itemBox.setStyle("-fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0; -fx-padding: 4px 0 6px 0;");

                    Label remLine = new Label((i + 1) + ". " + r.remedyName() + " — Potency: " + r.potency() + " (" + r.scale() + ")");
                    remLine.setFont(Font.font("System", FontWeight.BOLD, 13));
                    remLine.setStyle("-fx-text-fill: #0f172a;");

                    Label posLine = new Label("   Posology: " + (r.posologyInstructions() != null ? r.posologyInstructions() : "—") +
                            " | Repetition: " + (r.repetitionFrequency() != null ? r.repetitionFrequency() : "—"));
                    posLine.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");

                    itemBox.getChildren().addAll(remLine, posLine);
                    rxTable.getChildren().add(itemBox);
                }
            }

            // Diet & Regimen Box
            VBox dietBox = new VBox(3);
            dietBox.setStyle("-fx-background-color: #fffbeb; -fx-padding: 8px 12px; -fx-border-color: #fde68a; -fx-border-radius: 4px;");
            Label dietTitle = new Label("Diet & Regimen Instructions:");
            dietTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #b45309; -fx-font-size: 11px;");
            Label dietBody = new Label(inst.defaultRegimenInstructions() != null && !inst.defaultRegimenInstructions().isBlank()
                    ? inst.defaultRegimenInstructions()
                    : "• Avoid strong smelling substances (raw onion, garlic, menthol, camphor, eucalyptus, strong coffee) 30 mins before & after dose.\n• Take globules dry on a clean tongue or dissolved in fresh water as instructed.");
            dietBody.setStyle("-fx-text-fill: #78350f; -fx-font-size: 11px;");
            dietBox.getChildren().addAll(dietTitle, dietBody);

            // Footer / Signature
            HBox footerBox = new HBox();
            footerBox.setAlignment(Pos.BOTTOM_RIGHT);
            footerBox.setStyle("-fx-padding: 16px 0 0 0;");
            VBox sigBox = new VBox(2);
            sigBox.setAlignment(Pos.CENTER);
            Label sigLine = new Label("____________________________________");
            sigLine.setStyle("-fx-text-fill: #94a3b8;");
            String sigDocText = attendingDoctor.fullName() +
                    (attendingDoctor.qualification() != null && !attendingDoctor.qualification().isBlank() ? ", " + attendingDoctor.qualification() : "");
            Label sigName = new Label(sigDocText);
            sigName.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 11px;");
            Label sigTitle2 = new Label(attendingDoctor.designation() != null && !attendingDoctor.designation().isBlank()
                    ? attendingDoctor.designation()
                    : (attendingDoctor.registrationNo() != null && !attendingDoctor.registrationNo().isBlank() ? "Reg. No: " + attendingDoctor.registrationNo() : "Consulting Homeopathic Physician"));
            sigTitle2.setStyle("-fx-text-fill: #64748b; -fx-font-size: 10px;");
            sigBox.getChildren().addAll(sigLine, sigName, sigTitle2);
            footerBox.getChildren().add(sigBox);

            slipCard.getChildren().addAll(headerBox, line, pBar, diagBox, rxSymbol, rxTable, dietBox, footerBox);
        };

        consultCombo.setOnAction(e -> renderSlip.run());
        if (!consults.isEmpty()) {
            consultCombo.getSelectionModel().selectFirst();
            renderSlip.run();
        }

        btnPrint.setOnAction(e -> {
            Consultation sel = consultCombo.getValue();
            if (sel == null) return;
            try {
                ClinicInstitutionProfile instProfile;
                try {
                    instProfile = clinicDao.getInstitutionProfile();
                } catch (Exception ex) {
                    instProfile = ClinicInstitutionProfile.defaultProfile();
                }

                Doctor printDoctor = null;
                if (sel.doctorId() != null && !sel.doctorId().isBlank()) {
                    try {
                        printDoctor = clinicDao.getDoctorById(sel.doctorId()).orElse(null);
                    } catch (Exception ignored) {}
                }
                if (printDoctor == null) {
                    printDoctor = new Doctor(
                            "doc-inst",
                            instProfile.chiefConsultantName(),
                            instProfile.chiefConsultantRegNo(),
                            instProfile.chiefConsultantQualifications(),
                            "Chief Consulting Homeopath",
                            instProfile.institutionName(),
                            instProfile.address(),
                            instProfile.phone(),
                            instProfile.email(),
                            "Consulting Physician",
                            true,
                            Instant.now()
                    );
                }

                File out = new File(System.getProperty("user.home"), "OpenHomeo_Prescription_" + p.caseNumber() + ".pdf");
                pdfService.generatePrescriptionPdf(printDoctor, instProfile, p, sel, Collections.emptyList(), true, out);

                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setTitle("Prescription Exported");
                a.setHeaderText("PDF Prescription Generated Successfully");
                a.setContentText("Saved to: " + out.getAbsolutePath());
                a.showAndWait();
            } catch (Exception ex) {
                log.error("Failed to generate PDF", ex);
            }
        });

        vbox.getChildren().addAll(topBar, new ScrollPane(slipCard));
        return vbox;
    }

    // =========================================================================
    // SUB-TAB 4: PHARMACY DISPENSARY INVOICES & FINANCIAL LEDGER
    // =========================================================================

    private Node createPharmacyLedgerPane(Patient p) {
        VBox vbox = new VBox(12);
        vbox.setPadding(new Insets(12));

        Label title = new Label("💳 Pharmacy Dispensary Invoices & Billing Account");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));

        TableView<PharmacyBill> billsTable = new TableView<>();
        billsTable.setPrefHeight(260);
        billsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PharmacyBill, String> colNum = new TableColumn<>("Invoice No");
        colNum.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().billNumber()));

        TableColumn<PharmacyBill, String> colDate = new TableColumn<>("Billing Date");
        colDate.setCellValueFactory(c -> {
            String d = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm").withZone(ZoneId.systemDefault()).format(c.getValue().billDate());
            return new SimpleStringProperty(d);
        });

        TableColumn<PharmacyBill, String> colDoc = new TableColumn<>("Consulting Doctor");
        colDoc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().doctorName()));

        TableColumn<PharmacyBill, String> colFeePoint = new TableColumn<>("Doctor Fee Status");
        colFeePoint.setCellValueFactory(c -> {
            boolean isDeskPaid = c.getValue().itemsJson() != null && c.getValue().itemsJson().contains("DOCTOR_DESK_PAID");
            return new SimpleStringProperty(isDeskPaid ? "✓ Paid at Doctor Desk" : "⚠ Collect at Dispensary");
        });

        TableColumn<PharmacyBill, String> colSub = new TableColumn<>("Subtotal (₹)");
        colSub.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().subtotalInr())));

        TableColumn<PharmacyBill, String> colGst = new TableColumn<>("GST (5%)");
        colGst.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().gstAmountInr())));

        TableColumn<PharmacyBill, String> colTotal = new TableColumn<>("Total (₹)");
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format("₹%.2f", c.getValue().totalInr())));

        TableColumn<PharmacyBill, String> colMode = new TableColumn<>("Payment Mode");
        colMode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().paymentMode()));

        TableColumn<PharmacyBill, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().paymentStatus()));

        billsTable.getColumns().addAll(colNum, colDate, colDoc, colFeePoint, colSub, colGst, colTotal, colMode, colStatus);

        double totalSpent = 0;
        try {
            List<PharmacyBill> bills = clinicDao.getBillsForPatient(p.id());
            billsTable.setItems(FXCollections.observableArrayList(bills));
            for (PharmacyBill b : bills) {
                totalSpent += b.totalInr();
            }
        } catch (SQLException e) {
            log.error("Failed to load patient bills", e);
        }

        // Summary Spend Card
        HBox summaryCard = new HBox(16);
        summaryCard.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 12px 16px; -fx-border-color: -color-border-subtle; -fx-border-radius: 6px;");
        summaryCard.setAlignment(Pos.CENTER_LEFT);

        Label totalLbl = new Label(String.format("Lifetime Dispensary Spend: ₹%,.2f", totalSpent));
        totalLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        totalLbl.setStyle("-fx-text-fill: -color-accent-fg;");

        Label countLbl = new Label("Total Invoices: " + billsTable.getItems().size());
        countLbl.getStyleClass().add(Styles.TEXT_MUTED);

        summaryCard.getChildren().addAll(totalLbl, new Separator(), countLbl);

        vbox.getChildren().addAll(title, summaryCard, billsTable);
        return vbox;
    }

    // =========================================================================
    // NEW PATIENT REGISTRATION DIALOG
    // =========================================================================

    private void showNewPatientDialog() {
        Dialog<Patient> dlg = new Dialog<>();
        dlg.setTitle("Register New Patient");
        dlg.setHeaderText("Enter patient demographic & constitutional profile");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(16));

        TextField txtFirst = new TextField();
        TextField txtLast = new TextField();
        DatePicker dpDob = new DatePicker(LocalDate.now().minusYears(30));
        ComboBox<String> cmbGender = new ComboBox<>(FXCollections.observableArrayList("Male", "Female", "Other"));
        cmbGender.getSelectionModel().selectFirst();
        TextField txtPhone = new TextField();
        TextField txtEmail = new TextField();
        TextField txtAddress = new TextField();
        ComboBox<String> cmbBlood = new ComboBox<>(FXCollections.observableArrayList("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-"));
        cmbBlood.getSelectionModel().selectFirst();
        ComboBox<String> cmbConst = new ComboBox<>(FXCollections.observableArrayList("Carbonic", "Phosphoric", "Fluoric", "Sulphuric", "Carbo-nitrogenoid", "General"));
        cmbConst.getSelectionModel().selectFirst();
        ComboBox<String> cmbMiasm = new ComboBox<>(FXCollections.observableArrayList("PSORA", "SYCOSIS", "SYPHILIS", "TUBERCULAR"));
        cmbMiasm.getSelectionModel().selectFirst();

        g.addRow(0, new Label("First Name:"), txtFirst, new Label("Last Name:"), txtLast);
        g.addRow(1, new Label("Date of Birth:"), dpDob, new Label("Gender:"), cmbGender);
        g.addRow(2, new Label("Phone:"), txtPhone, new Label("Email:"), txtEmail);
        g.addRow(3, new Label("Address:"), txtAddress, new Label("Blood Group:"), cmbBlood);
        g.addRow(4, new Label("Constitution:"), cmbConst, new Label("Miasm:"), cmbMiasm);

        dlg.getDialogPane().setContent(g);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                if (txtFirst.getText().trim().isBlank()) return null;
                String caseNo = "CASE-" + (System.currentTimeMillis() % 100000);
                try {
                    return clinicDao.createPatient(
                            caseNo, txtFirst.getText().trim(), txtLast.getText().trim(),
                            dpDob.getValue(), cmbGender.getValue(), "General",
                            txtPhone.getText().trim(), txtEmail.getText().trim(), txtAddress.getText().trim(),
                            cmbBlood.getValue(), cmbConst.getValue(), cmbMiasm.getValue()
                    );
                } catch (SQLException e) {
                    log.error("Failed to save patient", e);
                    return null;
                }
            }
            return null;
        });

        dlg.showAndWait().ifPresent(newPt -> {
            refreshPatientList();
            selectPatient(newPt.id());
        });
    }
}
