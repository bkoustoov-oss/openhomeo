package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import org.openhomeo.core.ai.EntryPointSuggestionEngine;
import org.openhomeo.core.ai.PrescriptionLogicAnalyzer;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.*;
import org.openhomeo.core.net.NetworkDiscoveryService;
import org.openhomeo.core.pdf.PrescriptionPdfService;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Classical Case-Taking Workstation:
 * - Hahnemannian L-S-M-C Framework
 * - Intelligent Entry-Point Medicine Suggestion Engine with Doctor Supersede / Nullification Case Study Database
 * - Earlier Medications, Suppressions & Drug Miasm Tracking
 * - Enlisted Practitioner Management
 * - Posology Prescription Generator & Dispatch to Dispensary
 * - Structured PDF Printing with Auto-Fit Single Page option
 */
public class CaseTakingView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(CaseTakingView.class);

    private final ClinicDao clinicDao;
    private final MasterKnowledgeDao masterDao;
    private final SymptomClipboard clipboard;
    private final PrescriptionPdfService pdfService = new PrescriptionPdfService();
    private final EntryPointSuggestionEngine suggestionEngine = new EntryPointSuggestionEngine();

    // Patient & Doctor selection
    private final ComboBox<PatientItem> patientCombo;
    private final ComboBox<DoctorItem> doctorCombo;

    // Case Fields
    private final TextArea chiefComplaintArea;
    private final TextField locationField;
    private final TextField sensationField;
    private final TextField modalityField;
    private final TextField concomitantField;
    private final TextArea mindArea;
    private final TextArea physicalsArea;
    private final TextArea heringsLawArea;

    // Earlier Medications
    private final ListView<String> pastMedListView;
    private final List<PastMedication> currentPatientPastMeds = new ArrayList<>();

    // Intelligent Suggestion
    private final Label suggestionLabel;
    private final Label suggestionReasonLabel;
    private EntryPointSuggestionEngine.SuggestedRemedy currentSuggestedRemedy = null;

    // Prescription Controls
    private final ComboBox<RemedyItem> remedyCombo;
    private final TextField potencyField;
    private final ComboBox<Prescription.PotencyScale> scaleCombo;
    private final TextField posologyField;
    private final TextField repetitionField;

    // Printing Controls
    private final CheckBox autoFitCheck;

    private final ListView<ConsultationItem> historyList;
    private final Label statusLabel;

    public record PatientItem(Patient patient) {
        @Override
        public String toString() {
            return String.format("[%s] %s %s (%s)", patient.caseNumber(), patient.firstName(), patient.lastName(),
                    patient.constitutionalType() != null ? patient.constitutionalType() : "General");
        }
    }

    public record DoctorItem(Doctor doctor) {
        @Override
        public String toString() {
            return String.format("%s, %s", doctor.fullName(), doctor.qualification() != null ? doctor.qualification() : "");
        }
    }

    public record RemedyItem(Remedy remedy) {
        @Override
        public String toString() {
            return String.format("%s (%s)", remedy.fullName(), remedy.abbr());
        }
    }

    public record ConsultationItem(Consultation consultation) {
        @Override
        public String toString() {
            return String.format("%s - %s",
                    consultation.consultDate().toString().substring(0, 10),
                    consultation.chiefComplaint().length() > 35 ? consultation.chiefComplaint().substring(0, 32) + "..." : consultation.chiefComplaint());
        }
    }

    public CaseTakingView(ClinicDao clinicDao, MasterKnowledgeDao masterDao, SymptomClipboard clipboard) {
        this.clinicDao = clinicDao;
        this.masterDao = masterDao;
        this.clipboard = clipboard;

        setPadding(new Insets(10));

        // 1. Top Bar: Patient & Doctor Selection
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 10, 0));

        patientCombo = new ComboBox<>();
        patientCombo.setPromptText("Select Patient...");
        patientCombo.setPrefWidth(300);
        patientCombo.setOnAction(e -> {
            loadPatientHistory();
            loadPatientPastMedications();
            computeEntryPointSuggestions();
        });

        Button filterPatBtn = new Button("🔍 Filter Patients");
        filterPatBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        filterPatBtn.setOnAction(e -> showPatientFilterDialog());

        Button newPatBtn = new Button("New Patient");
        newPatBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        newPatBtn.setOnAction(e -> showNewPatientDialog());

        doctorCombo = new ComboBox<>();
        doctorCombo.setPromptText("Select Attending Doctor...");
        doctorCombo.setPrefWidth(300);

        Button manageDocsBtn = new Button("Enlist / Manage Doctors");
        manageDocsBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        manageDocsBtn.setOnAction(e -> showDoctorManagementDialog());

        Button autoLanBtn = new Button("🌐 Auto-Search LAN");
        autoLanBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        autoLanBtn.setOnAction(e -> triggerLanAutoConnect());

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add(Styles.TEXT_MUTED);

        topBar.getChildren().addAll(
                new Label("Patient:"), patientCombo, filterPatBtn, newPatBtn,
                new Label("Doctor:"), doctorCombo, manageDocsBtn, autoLanBtn,
                statusLabel
        );
        setTop(topBar);

        // 2. Central Split: Form (Left) & History (Right)
        SplitPane splitPane = new SplitPane();

        // Left Panel: Case-Taking Form
        VBox formBox = new VBox(10);
        formBox.setPadding(new Insets(10, 15, 10, 5));

        chiefComplaintArea = new TextArea();
        chiefComplaintArea.setPromptText("Chief complaints, presenting symptoms, and clinical onset narrative...");
        chiefComplaintArea.setPrefRowCount(2);
        chiefComplaintArea.textProperty().addListener((o, ov, nv) -> computeEntryPointSuggestions());

        locationField = new TextField();
        locationField.setPromptText("L: Anatomical site, lateralization (Left/Right/Diagonal)...");
        locationField.textProperty().addListener((o, ov, nv) -> computeEntryPointSuggestions());

        sensationField = new TextField();
        sensationField.setPromptText("S: Sensation character (burning, throbbing, stitching, cramping)...");
        sensationField.textProperty().addListener((o, ov, nv) -> computeEntryPointSuggestions());

        modalityField = new TextField();
        modalityField.setPromptText("M: Modalities (< worse / > better: time, temperature, motion, weather)..");
        modalityField.textProperty().addListener((o, ov, nv) -> computeEntryPointSuggestions());

        concomitantField = new TextField();
        concomitantField.setPromptText("C: Concomitants (accompanying un-related symptoms)...");

        mindArea = new TextArea();
        mindArea.setPromptText("Mind & Disposition: Mental generals, emotional triggers, fears, weeping, irritability...");
        mindArea.setPrefRowCount(2);
        mindArea.textProperty().addListener((o, ov, nv) -> computeEntryPointSuggestions());

        physicalsArea = new TextArea();
        physicalsArea.setPromptText("Physical Generals: Thermals (hot/chilly), thirst, cravings, aversions, sleep, dreams...");
        physicalsArea.setPrefRowCount(2);
        physicalsArea.textProperty().addListener((o, ov, nv) -> computeEntryPointSuggestions());

        // --- Earlier Medications Card ---
        VBox pastMedCard = new VBox(6);
        pastMedCard.setPadding(new Insets(8, 12, 8, 12));
        pastMedCard.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 8px;");

        HBox pastMedHead = new HBox(10);
        pastMedHead.setAlignment(Pos.CENTER_LEFT);
        Label pastMedTitle = new Label("Earlier Medications, Suppressions & Drug Miasm History");
        pastMedTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Button addPastMedBtn = new Button("+ Log Earlier Medication / Suppression");
        addPastMedBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        addPastMedBtn.setOnAction(e -> showAddPastMedicationDialog());
        pastMedHead.getChildren().addAll(pastMedTitle, spacer1, addPastMedBtn);

        pastMedListView = new ListView<>();
        pastMedListView.setPrefHeight(65);
        pastMedCard.getChildren().addAll(pastMedHead, pastMedListView);

        // --- Intelligent Entry-Point Suggestion Card ---
        VBox suggestionCard = new VBox(6);
        suggestionCard.setPadding(new Insets(10, 12, 10, 12));
        suggestionCard.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #86efac; -fx-border-radius: 8px;");

        HBox sugTop = new HBox(10);
        sugTop.setAlignment(Pos.CENTER_LEFT);
        Label sugTitle = new Label("⚡ Intelligent Entry-Point Suggestion Engine");
        sugTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        sugTitle.setStyle("-fx-text-fill: #166534;");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Button computeBtn = new Button("🔄 Re-Analyze Entry Points");
        computeBtn.getStyleClass().add(Styles.SMALL);
        computeBtn.setOnAction(e -> computeEntryPointSuggestions());
        sugTop.getChildren().addAll(sugTitle, spacer2, computeBtn);

        suggestionLabel = new Label("Enter symptoms above to trigger dynamic entry-point recommendations.");
        suggestionLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        suggestionLabel.setStyle("-fx-text-fill: #15803d;");

        suggestionReasonLabel = new Label();
        suggestionReasonLabel.setWrapText(true);
        suggestionReasonLabel.setStyle("-fx-text-fill: #374151; -fx-font-size: 11px;");

        HBox sugActions = new HBox(12);
        sugActions.setAlignment(Pos.CENTER_LEFT);

        Button acceptSugBtn = new Button("✅ Accept Suggestion to Prescription");
        acceptSugBtn.getStyleClass().addAll(Styles.SUCCESS, Styles.SMALL);
        acceptSugBtn.setOnAction(e -> acceptSuggestionToPrescription());

        Button supersedeBtn = new Button("🤝 Doctor Supersede & Case Study Note");
        supersedeBtn.getStyleClass().addAll(Styles.WARNING, Styles.SMALL);
        supersedeBtn.setOnAction(e -> showSupersedeAuditDialog());

        sugActions.getChildren().addAll(acceptSugBtn, supersedeBtn);
        suggestionCard.getChildren().addAll(sugTop, suggestionLabel, suggestionReasonLabel, sugActions);

        heringsLawArea = new TextArea();
        heringsLawArea.setPromptText("Hering's Law Evaluation: Direction of cure progression (inside-out, above-downward)...");
        heringsLawArea.setPrefRowCount(2);

        // Prescription Box
        TitledPane prescPane = new TitledPane();
        prescPane.setText("Prescription (℞) & Posology Dispensation");
        prescPane.setExpanded(true);

        GridPane prescGrid = new GridPane();
        prescGrid.setHgap(10);
        prescGrid.setVgap(8);
        prescGrid.setPadding(new Insets(10));

        remedyCombo = new ComboBox<>();
        remedyCombo.setPromptText("Select Remedy...");
        remedyCombo.setPrefWidth(280);

        potencyField = new TextField("200");
        potencyField.setPrefWidth(80);

        scaleCombo = new ComboBox<>();
        scaleCombo.getItems().addAll(Prescription.PotencyScale.values());
        scaleCombo.setValue(Prescription.PotencyScale.CENTESIMAL);

        posologyField = new TextField("4 globules / 1 dose");
        posologyField.setPrefWidth(180);

        repetitionField = new TextField("Single dose at bedtime, then placebo / SAC-LAC");

        prescGrid.add(new Label("Remedy:"), 0, 0);
        prescGrid.add(remedyCombo, 1, 0);
        prescGrid.add(new Label("Potency:"), 2, 0);
        prescGrid.add(potencyField, 3, 0);

        prescGrid.add(new Label("Scale:"), 0, 1);
        prescGrid.add(scaleCombo, 1, 1);
        prescGrid.add(new Label("Posology:"), 2, 1);
        prescGrid.add(posologyField, 3, 1);

        prescGrid.add(new Label("Repetition:"), 0, 2);
        prescGrid.add(repetitionField, 1, 2, 3, 1);

        prescPane.setContent(prescGrid);

        // Action Toolbar
        HBox actionBox = new HBox(12);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Button saveConsultBtn = new Button("Save Consultation");
        saveConsultBtn.getStyleClass().addAll(Styles.SUCCESS, Styles.LARGE);
        saveConsultBtn.setOnAction(e -> saveCurrentConsultation(false));

        Button saveAndPrintBtn = new Button("Save & Print PDF");
        saveAndPrintBtn.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);
        saveAndPrintBtn.setOnAction(e -> saveCurrentConsultation(true));

        Button logicAnalysisBtn = new Button("🧠 Classical Logic & Case Trajectory");
        logicAnalysisBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.LARGE);
        logicAnalysisBtn.setOnAction(e -> showCaseLogicAnalysisDialog());

        autoFitCheck = new CheckBox("Auto-Fit to 1 Page (Prescription Slip)");
        autoFitCheck.setSelected(true);
        autoFitCheck.setTooltip(new Tooltip("Fits all clinical and prescription details onto exactly one A4 page without awkward page breaks"));

        actionBox.getChildren().addAll(saveConsultBtn, saveAndPrintBtn, logicAnalysisBtn, autoFitCheck);

        formBox.getChildren().addAll(
                new Label("Chief Complaint:"), chiefComplaintArea,
                new Label("Hahnemannian L-S-M-C Framework:"),
                locationField, sensationField, modalityField, concomitantField,
                new Label("Mind & Emotional Portrait:"), mindArea,
                new Label("Physical Generals:"), physicalsArea,
                pastMedCard,
                suggestionCard,
                new Label("Longitudinal Hering's Law Assessment:"), heringsLawArea,
                prescPane,
                actionBox
        );

        ScrollPane formScroll = new ScrollPane(formBox);
        formScroll.setFitToWidth(true);

        // Right Panel: Consultation History
        VBox historyBox = new VBox(10);
        historyBox.setPadding(new Insets(15));
        historyBox.setMinWidth(320);

        Label histHeader = new Label("Patient Case History");
        histHeader.getStyleClass().add(Styles.TITLE_4);

        historyList = new ListView<>();
        VBox.setVgrow(historyList, Priority.ALWAYS);

        HBox histBtnBox = new HBox(8);
        Button printHistBtn = new Button("Print Selected Consultation PDF");
        printHistBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        printHistBtn.setOnAction(e -> printSelectedHistory());
        histBtnBox.getChildren().add(printHistBtn);

        historyBox.getChildren().addAll(histHeader, historyList, histBtnBox);

        splitPane.getItems().addAll(formScroll, historyBox);
        splitPane.setDividerPositions(0.66);
        setCenter(splitPane);

        loadInitialData();
    }

    private void loadInitialData() {
        try {
            // Load Patients
            patientCombo.getItems().clear();
            for (Patient p : clinicDao.listPatients("")) {
                patientCombo.getItems().add(new PatientItem(p));
            }
            if (!patientCombo.getItems().isEmpty()) {
                patientCombo.getSelectionModel().select(0);
            }

            // Load Doctors
            doctorCombo.getItems().clear();
            for (Doctor d : clinicDao.getActiveDoctors()) {
                doctorCombo.getItems().add(new DoctorItem(d));
            }
            if (!doctorCombo.getItems().isEmpty()) {
                doctorCombo.getSelectionModel().select(0);
            }

            // Load Remedies for Prescription
            remedyCombo.getItems().clear();
            for (Remedy r : masterDao.getAllRemedies()) {
                remedyCombo.getItems().add(new RemedyItem(r));
            }
            if (!remedyCombo.getItems().isEmpty()) {
                remedyCombo.getSelectionModel().select(0);
            }

            loadPatientHistory();
            loadPatientPastMedications();
            computeEntryPointSuggestions();
        } catch (Exception e) {
            log.error("Failed to load case taking initial data", e);
        }
    }

    private void loadPatientHistory() {
        historyList.getItems().clear();
        PatientItem sel = patientCombo.getValue();
        if (sel == null) return;

        try {
            List<Consultation> consults = clinicDao.getConsultationsForPatient(sel.patient.id());
            for (Consultation c : consults) {
                historyList.getItems().add(new ConsultationItem(c));
            }
        } catch (Exception e) {
            log.error("Failed to load patient history", e);
        }
    }

    private void loadPatientPastMedications() {
        pastMedListView.getItems().clear();
        currentPatientPastMeds.clear();
        PatientItem sel = patientCombo.getValue();
        if (sel == null) return;

        try {
            List<PastMedication> list = clinicDao.getPastMedicationsByPatient(sel.patient.id());
            currentPatientPastMeds.addAll(list);
            for (PastMedication m : list) {
                pastMedListView.getItems().add(String.format("• %s [%s] (%s) -> Suppressed: %s (Antidote: %s)",
                        m.drugName(), m.category(), m.duration(),
                        m.suppressedSymptoms() != null ? m.suppressedSymptoms() : "None",
                        m.antidoteRemedy() != null ? m.antidoteRemedy() : "-"));
            }
            if (list.isEmpty()) {
                pastMedListView.getItems().add("No prior allopathic / suppressive medications recorded.");
            }
        } catch (Exception e) {
            log.error("Failed to load past medications", e);
        }
    }

    private void showAddPastMedicationDialog() {
        PatientItem sel = patientCombo.getValue();
        if (sel == null) {
            showAlert("No Patient Selected", "Please select or register a patient first.");
            return;
        }

        Dialog<PastMedication> dlg = new Dialog<>();
        dlg.setTitle("Record Prior Medication / Suppression History");
        dlg.setHeaderText("Log prior allopathic, steroid, antibiotic, or Ayurvedic treatment to assess drug miasm and antidotes.");

        ButtonType saveBtn = new ButtonType("Save Record", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        ComboBox<String> drugNameCombo = new ComboBox<>();
        drugNameCombo.setEditable(true);
        drugNameCombo.getItems().addAll(
                "Corticosteroids / Prednisolone / Betamethasone",
                "Broad-Spectrum Antibiotics (Amoxicillin, Azithromycin)",
                "NSAIDs / Paracetamol / Ibuprofen",
                "PPI / Pantoprazole / Omeprazole",
                "Antihypertensives (Amlodipine, Telmisartan)",
                "Oral Hypoglycemics (Metformin)",
                "Previous Homeopathy (High Potencies without matching)",
                "Ayurvedic Bhasma / Heavy Metal Preparations"
        );
        drugNameCombo.setValue("Corticosteroids / Prednisolone / Betamethasone");
        drugNameCombo.setPrefWidth(300);

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getItems().addAll("Allopathic / Steroid", "Antibiotics", "NSAID / Painkiller", "PPI / Antacid", "Cardiovascular", "Previous Homeopathy", "Ayurvedic");
        catCombo.setValue("Allopathic / Steroid");

        TextField durField = new TextField("6 months continuous");
        TextField doseField = new TextField("10mg daily");
        TextField suppField = new TextField("Cutaneous eczema suppressed abruptly; asthma started thereafter");
        TextField antiField = new TextField("Nux vomica, Sulphur, Thuja");

        grid.add(new Label("Drug / Medicine Name:"), 0, 0); grid.add(drugNameCombo, 1, 0);
        grid.add(new Label("Category:"), 0, 1); grid.add(catCombo, 1, 1);
        grid.add(new Label("Duration of Use:"), 0, 2); grid.add(durField, 1, 2);
        grid.add(new Label("Dosage Pattern:"), 0, 3); grid.add(doseField, 1, 3);
        grid.add(new Label("Suppressed Symptoms:"), 0, 4); grid.add(suppField, 1, 4);
        grid.add(new Label("Antidote Remedy:"), 0, 5); grid.add(antiField, 1, 5);

        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(b -> {
            if (b == saveBtn && drugNameCombo.getValue() != null && !drugNameCombo.getValue().isBlank()) {
                return new PastMedication(
                        UUID.randomUUID().toString(),
                        null,
                        sel.patient.id(),
                        drugNameCombo.getValue().trim(),
                        catCombo.getValue(),
                        durField.getText().trim(),
                        doseField.getText().trim(),
                        suppField.getText().trim(),
                        antiField.getText().trim(),
                        Instant.now()
                );
            }
            return null;
        });

        dlg.showAndWait().ifPresent(pm -> {
            try {
                clinicDao.savePastMedication(pm);
                loadPatientPastMedications();
                statusLabel.setText("Recorded earlier medication: " + pm.drugName());
            } catch (Exception ex) {
                log.error("Failed to save past medication", ex);
            }
        });
    }

    private void computeEntryPointSuggestions() {
        List<String> rubrics = new ArrayList<>();
        for (SymptomClipboard.ClipboardItem it : clipboard.getActiveItems()) {
            rubrics.add(it.rubric().fullPath());
        }

        EntryPointSuggestionEngine.EntryPointAnalysisResult result = suggestionEngine.analyze(
                chiefComplaintArea.getText(),
                locationField.getText(),
                sensationField.getText(),
                modalityField.getText(),
                concomitantField.getText(),
                mindArea.getText(),
                physicalsArea.getText(),
                rubrics
        );

        currentSuggestedRemedy = result.primarySuggestion();
        suggestionLabel.setText(String.format("Recommended: %s (%s) — Match Score: %.0f%%",
                currentSuggestedRemedy.remedyName(), currentSuggestedRemedy.suggestedPotencyScale(),
                currentSuggestedRemedy.confidenceScore()));

        suggestionReasonLabel.setText(result.clinicalRationaleSummary() + " | Differentiators: " +
                String.join("; ", currentSuggestedRemedy.clinicalReasons()));
    }

    private void acceptSuggestionToPrescription() {
        if (currentSuggestedRemedy == null) return;

        // Select remedy in combo
        for (RemedyItem it : remedyCombo.getItems()) {
            if (it.remedy.id().equalsIgnoreCase(currentSuggestedRemedy.remedyId())) {
                remedyCombo.getSelectionModel().select(it);
                break;
            }
        }

        if (currentSuggestedRemedy.suggestedPotencyScale().startsWith("Q")) {
            potencyField.setText("Q");
            scaleCombo.setValue(Prescription.PotencyScale.DECIMAL);
            posologyField.setText("10-15 drops in 1/4 cup water");
            repetitionField.setText("Three times daily before meals (TID)");
        } else {
            potencyField.setText("200");
            scaleCombo.setValue(Prescription.PotencyScale.CENTESIMAL);
            posologyField.setText("4 medicated globules / 1 dose");
            repetitionField.setText("Night and morning for 3 days, then wait & watch");
        }

        statusLabel.setText("Accepted suggested remedy: " + currentSuggestedRemedy.remedyName());
    }

    private void showSupersedeAuditDialog() {
        if (currentSuggestedRemedy == null) {
            showAlert("No Active Suggestion", "Please enter symptoms to generate a recommendation first.");
            return;
        }

        PatientItem sel = patientCombo.getValue();
        if (sel == null) return;

        Dialog<RemedySupersedeAudit> dlg = new Dialog<>();
        dlg.setTitle("Doctor Supersede & Case Study Research Note");
        dlg.setHeaderText("Record clinical justification for superseding the algorithmic recommendation.\nThis creates a high-value dataset for clinical case analysis and machine learning.");

        ButtonType submitBtn = new ButtonType("Save Case Study Audit", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(submitBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        Label sugLabel = new Label(currentSuggestedRemedy.remedyName() + " (" + currentSuggestedRemedy.remedyId() + ")");
        sugLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        ComboBox<RemedyItem> overrideCombo = new ComboBox<>();
        overrideCombo.getItems().addAll(remedyCombo.getItems());
        overrideCombo.setValue(remedyCombo.getValue());
        overrideCombo.setPrefWidth(260);

        TextField reasonField = new TextField("Midnight restless pacing and tongue with red triangular tip took precedence over motion modality");
        reasonField.setPrefWidth(380);

        ComboBox<String> tagCombo = new ComboBox<>();
        tagCombo.getItems().addAll("Keynote Dominance", "Etiology over Modality", "Thermal Inversion", "Suppression Antidote", "Miasmatic Shift", "Constitutional Synthesis");
        tagCombo.setValue("Keynote Dominance");

        TextArea notesArea = new TextArea(
                "Although the joint pains aggravated by motion pointed towards Bryonia, the patient exhibited intense midnight restlessness, tossing about in bed, and marked relief upon continued limbering up.\n" +
                "In classical Kentian hierarchy, the mental and general restlessness superseded the particular physical motion modality."
        );
        notesArea.setPrefRowCount(4);

        grid.add(new Label("Algorithmic Recommendation:"), 0, 0); grid.add(sugLabel, 1, 0);
        grid.add(new Label("Doctor's Chosen Prescription:"), 0, 1); grid.add(overrideCombo, 1, 1);
        grid.add(new Label("Supersede / Nullification Reason:"), 0, 2); grid.add(reasonField, 1, 2);
        grid.add(new Label("Case Study Tag:"), 0, 3); grid.add(tagCombo, 1, 3);
        grid.add(new Label("In-Depth Clinical Case Study Notes:"), 0, 4); grid.add(notesArea, 1, 4);

        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(b -> {
            if (b == submitBtn) {
                RemedyItem chosen = overrideCombo.getValue();
                DoctorItem doc = doctorCombo.getValue();
                return new RemedySupersedeAudit(
                        UUID.randomUUID().toString(),
                        null,
                        sel.patient.id(),
                        doc != null ? doc.doctor.id() : null,
                        currentSuggestedRemedy.remedyId(),
                        currentSuggestedRemedy.remedyName(),
                        chosen != null ? chosen.remedy.id() : "unknown",
                        chosen != null ? chosen.remedy.fullName() : "Unknown",
                        reasonField.getText().trim(),
                        notesArea.getText().trim(),
                        tagCombo.getValue(),
                        Instant.now()
                );
            }
            return null;
        });

        dlg.showAndWait().ifPresent(audit -> {
            try {
                clinicDao.saveSupersedeAudit(audit);
                showAlert("Case Study Saved", "Doctor supersede audit recorded successfully for future comparative analysis and machine learning.");
                statusLabel.setText("Recorded case study audit for " + audit.prescribedRemedyName());
            } catch (Exception ex) {
                log.error("Failed to save supersede audit", ex);
            }
        });
    }

    private void saveCurrentConsultation(boolean thenPrintPdf) {
        PatientItem sel = patientCombo.getValue();
        if (sel == null) {
            showAlert("Validation Error", "Please select a patient before saving consultation.");
            return;
        }

        String complaint = chiefComplaintArea.getText().trim();
        if (complaint.isBlank()) {
            showAlert("Validation Error", "Chief complaint cannot be empty.");
            return;
        }

        try {
            String consultId = UUID.randomUUID().toString();
            DoctorItem docItem = doctorCombo.getValue();
            String doctorId = docItem != null ? docItem.doctor.id() : null;

            // Rubrics
            List<Consultation.SelectedRubric> selectedRubrics = new ArrayList<>();
            for (SymptomClipboard.ClipboardItem it : clipboard.getActiveItems()) {
                selectedRubrics.add(new Consultation.SelectedRubric(
                        it.rubric().id(), it.rubric().fullPath(),
                        clipboard.getActiveClipboardIndex(), it.userWeight(), it.isEliminative()
                ));
            }

            // Prescription
            List<Prescription> prescriptions = new ArrayList<>();
            RemedyItem remItem = remedyCombo.getValue();
            if (remItem != null) {
                // Logic-Centric Classical Homeopathic Clinical AI Evaluation
                List<Consultation> pastConsults = clinicDao.getConsultationsForPatient(sel.patient.id());
                List<Prescription> pastRx = new ArrayList<>();
                for (Consultation pc : pastConsults) {
                    if (pc.prescriptions() != null) pastRx.addAll(pc.prescriptions());
                }

                PrescriptionLogicAnalyzer analyzer = new PrescriptionLogicAnalyzer();
                PrescriptionLogicAnalyzer.AnalysisResult analysis = analyzer.analyzePrescription(
                        remItem.remedy.id(), remItem.remedy.fullName(),
                        potencyField.getText().trim(), scaleCombo.getValue(),
                        repetitionField.getText().trim(), pastRx,
                        complaint, heringsLawArea.getText().trim()
                );

                if (analysis.requiresDoctorInterrogation()) {
                    boolean proceed = showAiInterrogationDialog(analyzer, analysis, sel.patient, docItem != null ? docItem.doctor : null, remItem.remedy, consultId);
                    if (!proceed) {
                        statusLabel.setText("Prescription cancelled for posological revision.");
                        return;
                    }
                }

                prescriptions.add(new Prescription(
                        UUID.randomUUID().toString(), consultId,
                        remItem.remedy.id(), remItem.remedy.fullName(),
                        potencyField.getText().trim(),
                        scaleCombo.getValue(),
                        posologyField.getText().trim(),
                        repetitionField.getText().trim(),
                        LocalDate.now()
                ));
            }

            Consultation c = new Consultation(
                    consultId, sel.patient.id(), doctorId, Instant.now(),
                    complaint, locationField.getText().trim(), sensationField.getText().trim(),
                    modalityField.getText().trim(), concomitantField.getText().trim(),
                    mindArea.getText().trim(), physicalsArea.getText().trim(),
                    heringsLawArea.getText().trim(), "Routine clinical follow-up",
                    selectedRubrics, prescriptions
            );

            clinicDao.saveConsultation(c);

            // Automatically dispatch order to Dispensary Pharmacy
            if (!prescriptions.isEmpty()) {
                Prescription pr = prescriptions.get(0);
                String orderId = UUID.randomUUID().toString();
                String orderNum = "ORD-" + LocalDate.now().getYear() + "-" + (System.currentTimeMillis() % 10000);
                List<PharmacyOrderItem> orderItems = List.of(
                        new PharmacyOrderItem(
                                UUID.randomUUID().toString(), orderId, pr.remedyId(),
                                pr.remedyName(), pr.potency() + " " + pr.scale().name(),
                                "Dispensary Medicated Globules / Dilution",
                                pr.posologyInstructions() + " | " + pr.repetitionFrequency(),
                                null, null, null, "SBL / Reckeweg", 1, 100.0, false
                        )
                );
                PharmacyOrder po = new PharmacyOrder(
                        orderId, orderNum, consultId, sel.patient.id(),
                        sel.patient.firstName() + " " + sel.patient.lastName(),
                        doctorId, docItem != null ? docItem.doctor.fullName() : "Dr. Practitioner",
                        PharmacyOrder.OrderStatus.ORDERED, null, null, null,
                        Instant.now(), null, orderItems
                );
                clinicDao.createPharmacyOrder(po);
            }

            statusLabel.setText("Consultation saved and order dispatched to Pharmacy Dispensary.");
            loadPatientHistory();

            if (thenPrintPdf) {
                exportPrescriptionToPdf(docItem != null ? docItem.doctor : null, sel.patient, c);
            } else {
                showAlert("Success", "Consultation saved, prescription dispatched to Pharmacy Dispensary.");
            }

        } catch (Exception e) {
            log.error("Failed to save consultation", e);
            showAlert("Error", "Failed to save consultation: " + e.getMessage());
        }
    }

    private void printSelectedHistory() {
        ConsultationItem sel = historyList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert("No Selection", "Please select a consultation from the history list to print.");
            return;
        }

        PatientItem patItem = patientCombo.getValue();
        if (patItem == null) return;

        Doctor doc = null;
        if (sel.consultation.doctorId() != null) {
            try {
                doc = clinicDao.getDoctorById(sel.consultation.doctorId()).orElse(null);
            } catch (Exception ignored) {}
        }
        if (doc == null && doctorCombo.getValue() != null) {
            doc = doctorCombo.getValue().doctor;
        }

        exportPrescriptionToPdf(doc, patItem.patient, sel.consultation);
    }

    private void exportPrescriptionToPdf(Doctor doctor, Patient patient, Consultation consultation) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Professional Prescription PDF");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));
            String patNameClean = patient != null ? (patient.firstName() + "_" + patient.lastName()).replaceAll("[^a-zA-Z0-9_]", "") : "Patient";
            chooser.setInitialFileName(String.format("Prescription_%s_%s.pdf", patNameClean, LocalDate.now()));

            File targetFile = chooser.showSaveDialog(getScene().getWindow());
            if (targetFile != null) {
                boolean autoFit = autoFitCheck.isSelected();
                pdfService.generatePrescriptionPdf(doctor, patient, consultation, currentPatientPastMeds, autoFit, targetFile);

                statusLabel.setText("PDF exported successfully: " + targetFile.getName());

                // Auto-open PDF if supported
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(targetFile);
                } else {
                    showAlert("PDF Exported", "Prescription PDF created successfully at:\n" + targetFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.error("Failed to export PDF", e);
            showAlert("PDF Generation Error", "Could not generate PDF: " + e.getMessage());
        }
    }

    private void showDoctorManagementDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Enlist & Manage Doctors");
        dialog.setHeaderText("Enlist practitioner qualifications, registration, and clinic details for prescriptions");

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(12);
        content.setPadding(new Insets(15));
        content.setMinWidth(550);

        ListView<String> docListView = new ListView<>();
        docListView.setPrefHeight(120);

        Runnable refreshList = () -> {
            docListView.getItems().clear();
            try {
                for (Doctor d : clinicDao.getAllDoctors()) {
                    String status = d.isActive() ? "[ACTIVE]" : "[INACTIVE]";
                    docListView.getItems().add(String.format("%s %s (%s) - Reg: %s\n%s | %s",
                            status, d.fullName(), d.qualification(), d.registrationNo(), d.clinicName(), d.phone()));
                }
            } catch (Exception e) {
                log.error("Failed to list doctors", e);
            }
        };
        refreshList.run();

        // Enlist New Doctor Form
        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        TextField nameField = new TextField(); nameField.setPromptText("Dr. Full Name (e.g. Dr. A. K. Roy)");
        TextField regField = new TextField(); regField.setPromptText("Registration No. (e.g. WBH-12345 / CCH)");
        TextField qualField = new TextField(); qualField.setPromptText("Qualifications (e.g. B.H.M.S., M.D. Hom.)");
        TextField desigField = new TextField("Consulting Homeopathic Physician"); desigField.setPromptText("Designation");
        TextField clinicField = new TextField("Classical Homeopathy Wellness Clinic"); clinicField.setPromptText("Clinic / Hospital Name");
        TextField addrField = new TextField(); addrField.setPromptText("Address");
        TextField phoneField = new TextField(); phoneField.setPromptText("Contact Phone");
        TextField emailField = new TextField(); emailField.setPromptText("Email Address");
        TextField sigField = new TextField("Authorized Signatory"); sigField.setPromptText("Signature / Stamp Notes");

        grid.add(new Label("Full Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Registration No:"), 0, 1); grid.add(regField, 1, 1);
        grid.add(new Label("Qualifications:"), 0, 2); grid.add(qualField, 1, 2);
        grid.add(new Label("Designation:"), 0, 3); grid.add(desigField, 1, 3);
        grid.add(new Label("Clinic Name:"), 0, 4); grid.add(clinicField, 1, 4);
        grid.add(new Label("Clinic Address:"), 0, 5); grid.add(addrField, 1, 5);
        grid.add(new Label("Phone:"), 0, 6); grid.add(phoneField, 1, 6);
        grid.add(new Label("Email:"), 0, 7); grid.add(emailField, 1, 7);
        grid.add(new Label("Signature Stamp:"), 0, 8); grid.add(sigField, 1, 8);

        Button saveDocBtn = new Button("Enlist Doctor");
        saveDocBtn.getStyleClass().add(Styles.SUCCESS);
        saveDocBtn.setOnAction(e -> {
            String n = nameField.getText().trim();
            if (n.isBlank()) {
                showAlert("Validation Error", "Doctor's full name is required.");
                return;
            }
            try {
                Doctor d = new Doctor(
                        UUID.randomUUID().toString(), n, regField.getText().trim(),
                        qualField.getText().trim(), desigField.getText().trim(),
                        clinicField.getText().trim(), addrField.getText().trim(),
                        phoneField.getText().trim(), emailField.getText().trim(),
                        sigField.getText().trim(), true, Instant.now()
                );
                clinicDao.saveDoctor(d);
                refreshList.run();
                loadInitialData();
                statusLabel.setText("Doctor enlisted: " + d.fullName());
                nameField.clear(); regField.clear(); qualField.clear(); addrField.clear(); phoneField.clear(); emailField.clear();
            } catch (Exception ex) {
                log.error("Failed to save doctor", ex);
                showAlert("Error", "Could not save doctor: " + ex.getMessage());
            }
        });

        content.getChildren().addAll(
                new Label("Active & Enlisted Doctors:"), docListView,
                new Separator(),
                new Label("Enlist New Doctor Profile:"), grid, saveDocBtn
        );

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void showNewPatientDialog() {
        Dialog<Patient> dialog = new Dialog<>();
        dialog.setTitle("Register New Patient");
        dialog.setHeaderText("Enter patient demographic and constitutional profile");

        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField caseNumField = new TextField("CASE-" + (System.currentTimeMillis() % 100000));
        TextField fnField = new TextField();
        TextField lnField = new TextField();
        DatePicker dobPicker = new DatePicker(LocalDate.now().minusYears(30));
        ComboBox<String> genderCombo = new ComboBox<>();
        genderCombo.getItems().addAll("Male", "Female", "Other");
        genderCombo.setValue("Male");

        TextField phoneField = new TextField();
        TextField emailField = new TextField();
        TextField addrField = new TextField();

        // Comprehensive Classical Homeopathic Constitution Dropdown
        ComboBox<String> constCombo = new ComboBox<>();
        constCombo.setEditable(true);
        constCombo.getItems().addAll(HomeopathicConstitution.getAllDescriptions());
        constCombo.setValue(HomeopathicConstitution.CARBONIC.getDescription());
        constCombo.setPrefWidth(340);

        ComboBox<String> miasmCombo = new ComboBox<>();
        miasmCombo.getItems().addAll("PSORA", "SYCOSIS", "SYPHILIS", "TUBERCULAR");
        miasmCombo.setValue("PSORA");

        grid.add(new Label("Case Number:"), 0, 0); grid.add(caseNumField, 1, 0);
        grid.add(new Label("First Name:"), 0, 1); grid.add(fnField, 1, 1);
        grid.add(new Label("Last Name:"), 0, 2); grid.add(lnField, 1, 2);
        grid.add(new Label("Date of Birth:"), 0, 3); grid.add(dobPicker, 1, 3);
        grid.add(new Label("Gender:"), 0, 4); grid.add(genderCombo, 1, 4);
        grid.add(new Label("Phone:"), 0, 5); grid.add(phoneField, 1, 5);
        grid.add(new Label("Email:"), 0, 6); grid.add(emailField, 1, 6);
        grid.add(new Label("Address:"), 0, 7); grid.add(addrField, 1, 7);
        grid.add(new Label("Constitution:"), 0, 8); grid.add(constCombo, 1, 8);
        grid.add(new Label("Miasm:"), 0, 9); grid.add(miasmCombo, 1, 9);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                try {
                    return clinicDao.createPatient(
                            caseNumField.getText().trim(),
                            fnField.getText().trim(),
                            lnField.getText().trim(),
                            dobPicker.getValue(),
                            genderCombo.getValue(),
                            "General",
                            phoneField.getText().trim(),
                            emailField.getText().trim(),
                            addrField.getText().trim(),
                            "O+",
                            constCombo.getValue() != null ? constCombo.getValue().trim() : "Carbonic",
                            miasmCombo.getValue()
                    );
                } catch (Exception e) {
                    log.error("Failed to create patient", e);
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newPatient -> {
            loadInitialData();
            for (PatientItem item : patientCombo.getItems()) {
                if (item.patient.id().equals(newPatient.id())) {
                    patientCombo.getSelectionModel().select(item);
                    break;
                }
            }
            statusLabel.setText("Patient registered: " + newPatient.firstName() + " " + newPatient.lastName());
        });
    }

    private boolean showAiInterrogationDialog(
            PrescriptionLogicAnalyzer analyzer,
            PrescriptionLogicAnalyzer.AnalysisResult analysis,
            Patient patient,
            Doctor doctor,
            Remedy remedy,
            String consultId
    ) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Classical Posology & Logic Interrogation (Kentian & Organon Canon)");
        dlg.setHeaderText("The Classical Logic AI has detected posological or relational discrepancies:");

        VBox content = new VBox(12);
        content.setPadding(new Insets(14));
        content.setPrefWidth(600);

        List<TextArea> explanationAreas = new ArrayList<>();
        List<PrescriptionLogicAnalyzer.RuleCheck> checks = analysis.ruleChecks();

        for (PrescriptionLogicAnalyzer.RuleCheck check : checks) {
            VBox checkCard = new VBox(6);
            checkCard.setPadding(new Insets(10));
            checkCard.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-danger-emphasis; -fx-border-radius: 6; -fx-background-radius: 6;");

            Label lblRule = new Label("⚠️ " + check.ruleTitle() + " (" + check.severity() + ")");
            lblRule.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            lblRule.setStyle("-fx-text-fill: -color-danger-fg;");

            Label lblCanon = new Label(check.canonCitation());
            lblCanon.setWrapText(true);
            lblCanon.setStyle("-fx-font-style: italic; -fx-font-size: 11px;");

            Label lblDetail = new Label("Detail: " + check.discrepancyDetail());
            lblDetail.setWrapText(true);

            Label lblQuestion = new Label("❓ Interrogation: " + check.clinicalQuestion());
            lblQuestion.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            lblQuestion.setWrapText(true);

            TextArea txtExplanation = new TextArea();
            txtExplanation.setPromptText("Enter clinical justification (e.g. split water plussing under Organon §248, acute spasm crisis, palliative drainage)...");
            txtExplanation.setPrefRowCount(3);
            txtExplanation.setWrapText(true);

            checkCard.getChildren().addAll(lblRule, lblCanon, lblDetail, lblQuestion, txtExplanation);
            content.getChildren().add(checkCard);
            explanationAreas.add(txtExplanation);
        }

        ButtonType btnRevise = new ButtonType("Cancel & Revise Prescription", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType btnConfirm = new ButtonType("Confirm & Proceed", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(btnConfirm, btnRevise);
        dlg.getDialogPane().setContent(content);

        dlg.setResultConverter(btn -> {
            if (btn == btnConfirm) {
                for (int i = 0; i < checks.size(); i++) {
                    PrescriptionLogicAnalyzer.RuleCheck check = checks.get(i);
                    String explanation = explanationAreas.get(i).getText().trim();
                    PrescriptionLogicAnalyzer.ExplanationEvaluation eval = analyzer.evaluateDoctorExplanation(check, explanation);

                    if (eval.shouldLogToFaithDossier()) {
                        try {
                            AdminFaithDossierEntry entry = new AdminFaithDossierEntry(
                                    UUID.randomUUID().toString(),
                                    consultId,
                                    patient.id(),
                                    patient.firstName() + " " + patient.lastName(),
                                    doctor != null ? doctor.id() : "doc-unknown",
                                    doctor != null ? doctor.fullName() : "Attending Doctor",
                                    remedy.id(),
                                    remedy.fullName(),
                                    potencyField.getText().trim(),
                                    repetitionField.getText().trim(),
                                    check.ruleCode(),
                                    check.severity(),
                                    explanation,
                                    eval.riskGrade(),
                                    eval.predictedComplications(),
                                    false,
                                    Instant.now()
                            );
                            clinicDao.recordFaithDossierEntry(entry);
                            log.info("Confidential Report of Faith entry recorded: {}", check.ruleCode());
                        } catch (Exception ex) {
                            log.error("Failed to record faith dossier entry", ex);
                        }
                    }
                }
                return true;
            }
            return false;
        });

        Optional<Boolean> res = dlg.showAndWait();
        return res.orElse(false);
    }

    private void showCaseLogicAnalysisDialog() {
        PatientItem sel = patientCombo.getValue();
        if (sel == null) {
            showAlert("No Patient Selected", "Please select a patient to analyze case trajectory and logic.");
            return;
        }

        try {
            List<Consultation> history = clinicDao.getConsultationsForPatient(sel.patient.id());
            RemedyItem remItem = remedyCombo.getValue();
            PrescriptionLogicAnalyzer analyzer = new PrescriptionLogicAnalyzer();
            String synthesis = analyzer.analyzeHistoricalCaseTrajectory(
                    sel.patient.firstName() + " " + sel.patient.lastName(),
                    history,
                    remItem != null ? remItem.remedy : null,
                    potencyField.getText().trim()
            );

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Classical Case Trajectory & Logic Analyzer");
            alert.setHeaderText("Hering's Law and Longitudinal Case Progression Analysis");

            TextArea ta = new TextArea(synthesis);
            ta.setEditable(false);
            ta.setWrapText(true);
            ta.setPrefRowCount(14);
            ta.setPrefWidth(550);

            alert.getDialogPane().setContent(ta);
            alert.showAndWait();
        } catch (SQLException ex) {
            log.error("Failed to analyze case history", ex);
        }
    }

    private void showPatientFilterDialog() {
        Dialog<Patient> dlg = new Dialog<>();
        dlg.setTitle("Search & Filter Patients");
        dlg.setHeaderText("Filter patients by Category, Gender, Age group, Constitution, and Miasm");

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setPrefWidth(550);

        TextField txtSearch = new TextField();
        txtSearch.setPromptText("Search by Name, Case Number, or Phone...");

        ComboBox<String> comboCat = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                "ALL", "Student", "Executive", "Laborer", "Homemaker", "Teacher", "Retired", "General"
        ));
        comboCat.setValue("ALL");

        ComboBox<String> comboGender = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                "ALL", "Male", "Female", "Other"
        ));
        comboGender.setValue("ALL");

        ComboBox<String> comboAge = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                "ALL", "Pediatric (< 12 yrs)", "Adolescent (12-18 yrs)", "Adult (18-60 yrs)", "Geriatric (> 60 yrs)"
        ));
        comboAge.setValue("ALL");

        ComboBox<String> comboConst = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                "ALL", "Carbonic", "Phosphoric", "Fluoric", "Sulphuric", "Hydrogenoid", "Oxygenoid", "Carbo-nitrogenoid"
        ));
        comboConst.setValue("ALL");

        ComboBox<String> comboMiasm = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                "ALL", "PSORA", "SYCOSIS", "SYPHILIS", "TUBERCULAR", "CANCER"
        ));
        comboMiasm.setValue("ALL");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Search Text:"), txtSearch);
        grid.addRow(1, new Label("Category / Occupation:"), comboCat);
        grid.addRow(2, new Label("Gender:"), comboGender);
        grid.addRow(3, new Label("Age Group:"), comboAge);
        grid.addRow(4, new Label("Constitution:"), comboConst);
        grid.addRow(5, new Label("Miasm:"), comboMiasm);

        ListView<PatientItem> resultList = new ListView<>();
        resultList.setPrefHeight(180);

        Label lblCount = new Label("Matches: 0");

        Runnable runQuery = () -> {
            try {
                List<Patient> found = clinicDao.searchPatientsFiltered(
                        txtSearch.getText().trim(), comboCat.getValue(), comboGender.getValue(),
                        comboAge.getValue(), comboConst.getValue(), comboMiasm.getValue()
                );
                resultList.getItems().clear();
                for (Patient p : found) {
                    resultList.getItems().add(new PatientItem(p));
                }
                lblCount.setText("Matches found: " + found.size());
            } catch (SQLException ex) {
                log.error("Filter error", ex);
            }
        };

        txtSearch.textProperty().addListener((o, ov, nv) -> runQuery.run());
        comboCat.setOnAction(e -> runQuery.run());
        comboGender.setOnAction(e -> runQuery.run());
        comboAge.setOnAction(e -> runQuery.run());
        comboConst.setOnAction(e -> runQuery.run());
        comboMiasm.setOnAction(e -> runQuery.run());

        runQuery.run();

        root.getChildren().addAll(grid, new Label("Filtered Patient Results (Select to load):"), resultList, lblCount);
        dlg.getDialogPane().setContent(root);

        ButtonType btnSelect = new ButtonType("Select Patient", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(btnSelect, ButtonType.CANCEL);

        dlg.setResultConverter(btn -> {
            if (btn == btnSelect && resultList.getSelectionModel().getSelectedItem() != null) {
                return resultList.getSelectionModel().getSelectedItem().patient;
            }
            return null;
        });

        dlg.showAndWait().ifPresent(selectedPatient -> {
            loadInitialData();
            for (PatientItem item : patientCombo.getItems()) {
                if (item.patient.id().equals(selectedPatient.id())) {
                    patientCombo.getSelectionModel().select(item);
                    break;
                }
            }
            statusLabel.setText("Loaded filtered patient: " + selectedPatient.firstName() + " " + selectedPatient.lastName());
        });
    }

    private void triggerLanAutoConnect() {
        statusLabel.setText("Probing LAN for clinic database host beacon...");
        Optional<NetworkDiscoveryService.DiscoveredClinicHost> host = NetworkDiscoveryService.discoverClinicHost(2000);
        if (host.isPresent()) {
            NetworkDiscoveryService.DiscoveredClinicHost h = host.get();
            statusLabel.setText("Connected to " + h.clinicName() + " (" + h.hostIp() + ":" + h.port() + ")");
            showAlert("LAN Auto-Connect", "Successfully located Clinic LAN Host:\n\n" +
                    "• Clinic: " + h.clinicName() + "\n" +
                    "• Host: " + h.hostName() + " (" + h.hostIp() + ")\n" +
                    "• Port: " + h.port() + " | Mode: " + h.dbMode() + "\n" +
                    "• Latency: " + h.responseTimeMs() + " ms\n\n" +
                    "Workstation is synchronized with the shared clinic network database.");
        } else {
            statusLabel.setText("Operating on Local Embedded Database");
            showAlert("LAN Discovery", "No OpenHomeo Clinic Host detected on local Wi-Fi / subnet.\nOperating on Local Embedded Database.");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
