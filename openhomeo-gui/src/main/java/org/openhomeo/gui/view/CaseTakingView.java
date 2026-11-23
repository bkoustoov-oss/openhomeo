package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.Consultation;
import org.openhomeo.core.model.Patient;
import org.openhomeo.core.model.Prescription;
import org.openhomeo.core.model.Remedy;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Classical Case-Taking Workstation (L-S-M-C Framework & Posology Prescription Generator).
 */
public class CaseTakingView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(CaseTakingView.class);

    private final ClinicDao clinicDao;
    private final MasterKnowledgeDao masterDao;
    private final SymptomClipboard clipboard;

    private final ComboBox<PatientItem> patientCombo;
    private final TextArea chiefComplaintArea;
    private final TextField locationField;
    private final TextField sensationField;
    private final TextField modalityField;
    private final TextField concomitantField;
    private final TextArea mindArea;
    private final TextArea physicalsArea;
    private final TextArea heringsLawArea;

    // Prescription Controls
    private final ComboBox<RemedyItem> remedyCombo;
    private final TextField potencyField;
    private final ComboBox<Prescription.PotencyScale> scaleCombo;
    private final TextField posologyField;
    private final TextField repetitionField;

    private final ListView<String> historyList;
    private final Label statusLabel;

    public record PatientItem(Patient patient) {
        @Override
        public String toString() {
            return String.format("[%s] %s %s", patient.caseNumber(), patient.firstName(), patient.lastName());
        }
    }

    public record RemedyItem(Remedy remedy) {
        @Override
        public String toString() {
            return String.format("%s (%s)", remedy.fullName(), remedy.abbr());
        }
    }

    public CaseTakingView(ClinicDao clinicDao, MasterKnowledgeDao masterDao, SymptomClipboard clipboard) {
        this.clinicDao = clinicDao;
        this.masterDao = masterDao;
        this.clipboard = clipboard;

        // Top Bar: Patient Selection & New Patient
        ToolBar topBar = new ToolBar();
        patientCombo = new ComboBox<>();
        patientCombo.setMinWidth(300);
        patientCombo.setPromptText("Select Patient...");
        patientCombo.setOnAction(e -> loadPatientHistory());

        Button newPatBtn = new Button("New Patient");
        newPatBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        newPatBtn.setOnAction(e -> showNewPatientDialog());

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add(Styles.TEXT_MUTED);

        topBar.getItems().addAll(new Label("Patient:"), patientCombo, newPatBtn, new Separator(), statusLabel);
        setTop(topBar);

        // Center: L-S-M-C Form + Prescriptions in SplitPane
        SplitPane splitPane = new SplitPane();

        // Left Form: Case Taking
        VBox formBox = new VBox(10);
        formBox.setPadding(new Insets(15));

        chiefComplaintArea = new TextArea();
        chiefComplaintArea.setPromptText("Chief complaints, presenting symptoms, and clinical onset narrative...");
        chiefComplaintArea.setPrefRowCount(3);

        locationField = new TextField();
        locationField.setPromptText("L: Anatomical site, lateralization (Left/Right/Diagonal)...");

        sensationField = new TextField();
        sensationField.setPromptText("S: Sensation character (burning, throbbing, stitching, cramping)...");

        modalityField = new TextField();
        modalityField.setPromptText("M: Modalities (< worse / > better: time, temperature, motion, weather)...");

        concomitantField = new TextField();
        concomitantField.setPromptText("C: Concomitants (accompanying un-related symptoms)...");

        mindArea = new TextArea();
        mindArea.setPromptText("Mind & Disposition: Mental generals, emotional triggers, fears, weeping, irritability...");
        mindArea.setPrefRowCount(2);

        physicalsArea = new TextArea();
        physicalsArea.setPromptText("Physical Generals: Thermals (hot/chilly), thirst, cravings, aversions, sleep, dreams...");
        physicalsArea.setPrefRowCount(2);

        heringsLawArea = new TextArea();
        heringsLawArea.setPromptText("Hering's Law Evaluation: Direction of cure progression (inside-out, above-downward)...");
        heringsLawArea.setPrefRowCount(2);

        // Prescription Box
        TitledPane prescPane = new TitledPane();
        prescPane.setText("Prescription & Posology");
        GridPane prescGrid = new GridPane();
        prescGrid.setHgap(10);
        prescGrid.setVgap(8);
        prescGrid.setPadding(new Insets(10));

        remedyCombo = new ComboBox<>();
        remedyCombo.setMinWidth(220);
        remedyCombo.setPromptText("Select Prescribed Remedy...");

        potencyField = new TextField("200C");
        potencyField.setPromptText("Potency (e.g. 30C, 200C, 1M, LM 0/1)");

        scaleCombo = new ComboBox<>();
        scaleCombo.getItems().addAll(Prescription.PotencyScale.values());
        scaleCombo.setValue(Prescription.PotencyScale.CENTESIMAL);

        posologyField = new TextField("3 globules dry on tongue");
        repetitionField = new TextField("Single dose, wait and watch");

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

        Button saveConsultBtn = new Button("Save Consultation & Prescription");
        saveConsultBtn.getStyleClass().addAll(Styles.SUCCESS, Styles.LARGE);
        saveConsultBtn.setOnAction(e -> saveCurrentConsultation());

        formBox.getChildren().addAll(
                new Label("Chief Complaint:"), chiefComplaintArea,
                new Label("Hahnemannian L-S-M-C Framework:"),
                locationField, sensationField, modalityField, concomitantField,
                new Label("Mind & Emotional Portrait:"), mindArea,
                new Label("Physical Generals:"), physicalsArea,
                new Label("Longitudinal Hering's Law Assessment:"), heringsLawArea,
                prescPane,
                saveConsultBtn
        );

        ScrollPane formScroll = new ScrollPane(formBox);
        formScroll.setFitToWidth(true);

        // Right Panel: Consultation History
        VBox historyBox = new VBox(10);
        historyBox.setPadding(new Insets(15));
        historyBox.setMinWidth(320);

        Label histHeader = new Label("Consultation History");
        histHeader.getStyleClass().add(Styles.TITLE_4);

        historyList = new ListView<>();
        historyBox.getChildren().addAll(histHeader, historyList);

        splitPane.getItems().addAll(formScroll, historyBox);
        splitPane.setDividerPositions(0.68);
        setCenter(splitPane);

        loadInitialData();
    }

    private void loadInitialData() {
        try {
            // Load patients
            patientCombo.getItems().clear();
            for (Patient p : clinicDao.listPatients(null)) {
                patientCombo.getItems().add(new PatientItem(p));
            }
            if (!patientCombo.getItems().isEmpty()) {
                patientCombo.getSelectionModel().select(0);
                loadPatientHistory();
            }

            // Load remedies for prescription
            remedyCombo.getItems().clear();
            for (Remedy r : masterDao.getAllRemedies()) {
                remedyCombo.getItems().add(new RemedyItem(r));
            }
        } catch (Exception e) {
            log.error("Failed to load initial clinical data", e);
        }
    }

    private void loadPatientHistory() {
        historyList.getItems().clear();
        PatientItem sel = patientCombo.getValue();
        if (sel == null) return;

        try {
            List<Consultation> consults = clinicDao.getConsultationsForPatient(sel.patient.id());
            for (Consultation c : consults) {
                String desc = String.format("%s\nComplaint: %s",
                        c.consultDate().toString().substring(0, 10),
                        c.chiefComplaint());
                if (!c.prescriptions().isEmpty()) {
                    Prescription p = c.prescriptions().get(0);
                    desc += String.format("\nRx: %s %s (%s)", p.remedyName(), p.potency(), p.scale());
                }
                historyList.getItems().add(desc);
            }
        } catch (Exception e) {
            log.error("Failed to load consultations for patient", e);
        }
    }

    private void saveCurrentConsultation() {
        PatientItem sel = patientCombo.getValue();
        if (sel == null) {
            showAlert("No Patient Selected", "Please select or create a patient before saving.");
            return;
        }

        String complaint = chiefComplaintArea.getText().trim();
        if (complaint.isBlank()) {
            showAlert("Empty Complaint", "Please enter the chief complaint.");
            return;
        }

        try {
            String consultId = UUID.randomUUID().toString();

            // Link clipboard rubrics
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
                    consultId, sel.patient.id(), Instant.now(),
                    complaint, locationField.getText().trim(), sensationField.getText().trim(),
                    modalityField.getText().trim(), concomitantField.getText().trim(),
                    mindArea.getText().trim(), physicalsArea.getText().trim(),
                    heringsLawArea.getText().trim(), "Routine clinical follow-up",
                    selectedRubrics, prescriptions
            );

            clinicDao.saveConsultation(c);
            statusLabel.setText("Consultation saved successfully at " + LocalDate.now());
            loadPatientHistory();
            showAlert("Success", "Consultation and prescription saved to patient record.");

        } catch (Exception e) {
            log.error("Failed to save consultation", e);
            showAlert("Error", "Failed to save consultation: " + e.getMessage());
        }
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

        TextField caseNumField = new TextField("CASE-" + System.currentTimeMillis() % 100000);
        TextField fNameField = new TextField();
        TextField lNameField = new TextField();
        DatePicker dobPicker = new DatePicker(LocalDate.of(1990, 1, 1));
        ComboBox<String> genderCombo = new ComboBox<>();
        genderCombo.getItems().addAll("Male", "Female", "Other");
        genderCombo.setValue("Female");

        TextField phoneField = new TextField();
        TextField emailField = new TextField();
        ComboBox<String> constitutionCombo = new ComboBox<>();
        constitutionCombo.getItems().addAll("Carbonic", "Phosphoric", "Fluoric", "Hydrogenoid", "Oxygenoid");
        constitutionCombo.setValue("Carbonic");

        ComboBox<String> miasmCombo = new ComboBox<>();
        miasmCombo.getItems().addAll("PSORA", "SYCOSIS", "SYPHILIS", "TUBERCULAR");
        miasmCombo.setValue("PSORA");

        grid.add(new Label("Case Number:"), 0, 0); grid.add(caseNumField, 1, 0);
        grid.add(new Label("First Name:"), 0, 1); grid.add(fNameField, 1, 1);
        grid.add(new Label("Last Name:"), 0, 2); grid.add(lNameField, 1, 2);
        grid.add(new Label("Date of Birth:"), 0, 3); grid.add(dobPicker, 1, 3);
        grid.add(new Label("Gender:"), 0, 4); grid.add(genderCombo, 1, 4);
        grid.add(new Label("Phone:"), 0, 5); grid.add(phoneField, 1, 5);
        grid.add(new Label("Constitution:"), 0, 6); grid.add(constitutionCombo, 1, 6);
        grid.add(new Label("Primary Miasm:"), 0, 7); grid.add(miasmCombo, 1, 7);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType) {
                try {
                    return clinicDao.createPatient(
                            caseNumField.getText().trim(), fNameField.getText().trim(), lNameField.getText().trim(),
                            dobPicker.getValue(), genderCombo.getValue(), "General",
                            phoneField.getText().trim(), emailField.getText().trim(), "Clinic Address",
                            "O+", constitutionCombo.getValue(), miasmCombo.getValue()
                    );
                } catch (Exception ex) {
                    log.error("Failed to create patient", ex);
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(p -> {
            patientCombo.getItems().add(new PatientItem(p));
            patientCombo.setValue(new PatientItem(p));
            loadPatientHistory();
        });
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }
}
