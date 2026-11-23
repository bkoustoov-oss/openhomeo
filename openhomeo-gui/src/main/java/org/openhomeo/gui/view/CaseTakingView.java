package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.model.*;
import org.openhomeo.core.pdf.PrescriptionPdfService;
import org.openhomeo.core.repertory.SymptomClipboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Classical Case-Taking Workstation (L-S-M-C Framework, Enlisted Doctor Management,
 * Posology Prescription Generator, and Structured PDF Printing with Auto-Fit).
 */
public class CaseTakingView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(CaseTakingView.class);

    private final ClinicDao clinicDao;
    private final MasterKnowledgeDao masterDao;
    private final SymptomClipboard clipboard;
    private final PrescriptionPdfService pdfService = new PrescriptionPdfService();

    private final ComboBox<PatientItem> patientCombo;
    private final ComboBox<DoctorItem> doctorCombo;

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

    // Printing Controls
    private final CheckBox autoFitCheck;

    private final ListView<ConsultationItem> historyList;
    private final Label statusLabel;

    public record PatientItem(Patient patient) {
        @Override
        public String toString() {
            return String.format("[%s] %s %s", patient.caseNumber(), patient.firstName(), patient.lastName());
        }
    }

    public record DoctorItem(Doctor doctor) {
        @Override
        public String toString() {
            return doctor.displayTitle();
        }
    }

    public record RemedyItem(Remedy remedy) {
        @Override
        public String toString() {
            String common = remedy.commonName() != null ? " - " + remedy.commonName() : "";
            return String.format("%s (%s)%s", remedy.fullName(), remedy.abbr(), common);
        }
    }

    public record ConsultationItem(Consultation consultation, String displaySummary) {
        @Override
        public String toString() {
            return displaySummary;
        }
    }

    public CaseTakingView(ClinicDao clinicDao, MasterKnowledgeDao masterDao, SymptomClipboard clipboard) {
        this.clinicDao = clinicDao;
        this.masterDao = masterDao;
        this.clipboard = clipboard;

        // Top Bar: Patient Selection & Doctor Selection
        ToolBar topBar = new ToolBar();

        patientCombo = new ComboBox<>();
        patientCombo.setMinWidth(260);
        patientCombo.setPromptText("Select Patient...");
        patientCombo.setOnAction(e -> loadPatientHistory());

        Button newPatBtn = new Button("New Patient");
        newPatBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        newPatBtn.setOnAction(e -> showNewPatientDialog());

        doctorCombo = new ComboBox<>();
        doctorCombo.setMinWidth(250);
        doctorCombo.setPromptText("Consulting Doctor...");

        Button manageDocsBtn = new Button("Enlist / Manage Doctors");
        manageDocsBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        manageDocsBtn.setOnAction(e -> showDoctorManagementDialog());

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add(Styles.TEXT_MUTED);

        topBar.getItems().addAll(
                new Label("Patient:"), patientCombo, newPatBtn,
                new Separator(),
                new Label("Doctor:"), doctorCombo, manageDocsBtn,
                new Separator(),
                statusLabel
        );
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
        prescPane.setText("Prescription (℞) & Posology Dispensation");
        GridPane prescGrid = new GridPane();
        prescGrid.setHgap(10);
        prescGrid.setVgap(8);
        prescGrid.setPadding(new Insets(10));

        remedyCombo = new ComboBox<>();
        remedyCombo.setMinWidth(300);
        remedyCombo.setPromptText("Select Prescribed Remedy...");

        potencyField = new TextField("200C");
        potencyField.setPromptText("Potency (e.g. 30C, 200C, 1M, LM 0/1)");

        scaleCombo = new ComboBox<>();
        scaleCombo.getItems().addAll(Prescription.PotencyScale.values());
        scaleCombo.setValue(Prescription.PotencyScale.CENTESIMAL);

        posologyField = new TextField("4 globules dry on tongue");
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

        // Action Toolbar
        HBox actionBox = new HBox(12);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Button saveConsultBtn = new Button("Save Consultation");
        saveConsultBtn.getStyleClass().addAll(Styles.SUCCESS, Styles.LARGE);
        saveConsultBtn.setOnAction(e -> saveCurrentConsultation(false));

        Button saveAndPrintBtn = new Button("Save & Print PDF");
        saveAndPrintBtn.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);
        saveAndPrintBtn.setOnAction(e -> saveCurrentConsultation(true));

        autoFitCheck = new CheckBox("Auto-Fit to 1 Page (Prescription Slip)");
        autoFitCheck.setSelected(true);
        autoFitCheck.setTooltip(new Tooltip("Fits all consultation and prescription details onto exactly one A4 page without awkward page breaks"));

        actionBox.getChildren().addAll(saveConsultBtn, saveAndPrintBtn, autoFitCheck);

        formBox.getChildren().addAll(
                new Label("Chief Complaint:"), chiefComplaintArea,
                new Label("Hahnemannian L-S-M-C Framework:"),
                locationField, sensationField, modalityField, concomitantField,
                new Label("Mind & Emotional Portrait:"), mindArea,
                new Label("Physical Generals:"), physicalsArea,
                new Label("Longitudinal Hering's Law Assessment:"), heringsLawArea,
                prescPane,
                actionBox
        );

        ScrollPane formScroll = new ScrollPane(formBox);
        formScroll.setFitToWidth(true);

        // Right Panel: Consultation History
        VBox historyBox = new VBox(10);
        historyBox.setPadding(new Insets(15));
        historyBox.setMinWidth(340);

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
        splitPane.setDividerPositions(0.65);
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

            // Load doctors
            loadDoctors();

            // Load remedies for prescription
            remedyCombo.getItems().clear();
            for (Remedy r : masterDao.getAllRemedies()) {
                remedyCombo.getItems().add(new RemedyItem(r));
            }
        } catch (Exception e) {
            log.error("Failed to load initial clinical data", e);
        }
    }

    private void loadDoctors() {
        doctorCombo.getItems().clear();
        try {
            List<Doctor> docs = clinicDao.getActiveDoctors();
            for (Doctor d : docs) {
                doctorCombo.getItems().add(new DoctorItem(d));
            }
            if (!doctorCombo.getItems().isEmpty()) {
                doctorCombo.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            log.error("Failed to load doctors", e);
        }
    }

    private void loadPatientHistory() {
        historyList.getItems().clear();
        PatientItem sel = patientCombo.getValue();
        if (sel == null) return;

        try {
            List<Consultation> consults = clinicDao.getConsultationsForPatient(sel.patient.id());
            for (Consultation c : consults) {
                String docTitle = "Dr. Practitioner";
                if (c.doctorId() != null) {
                    Optional<Doctor> dOpt = clinicDao.getDoctorById(c.doctorId());
                    if (dOpt.isPresent()) {
                        docTitle = dOpt.get().fullName();
                    }
                }

                String desc = String.format("[%s] Doctor: %s\nComplaint: %s",
                        c.consultDate().toString().substring(0, 10),
                        docTitle,
                        c.chiefComplaint());
                if (c.prescriptions() != null && !c.prescriptions().isEmpty()) {
                    Prescription p = c.prescriptions().get(0);
                    desc += String.format("\n℞: %s %s (%s)", p.remedyName(), p.potency(), p.scale());
                }
                historyList.getItems().add(new ConsultationItem(c, desc));
            }
        } catch (Exception e) {
            log.error("Failed to load consultations for patient", e);
        }
    }

    private void saveCurrentConsultation(boolean thenPrintPdf) {
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

        DoctorItem docItem = doctorCombo.getValue();
        String doctorId = docItem != null ? docItem.doctor.id() : null;

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
                    consultId, sel.patient.id(), doctorId, Instant.now(),
                    complaint, locationField.getText().trim(), sensationField.getText().trim(),
                    modalityField.getText().trim(), concomitantField.getText().trim(),
                    mindArea.getText().trim(), physicalsArea.getText().trim(),
                    heringsLawArea.getText().trim(), "Routine clinical follow-up",
                    selectedRubrics, prescriptions
            );

            clinicDao.saveConsultation(c);
            statusLabel.setText("Consultation saved successfully at " + LocalDate.now());
            loadPatientHistory();

            if (thenPrintPdf) {
                exportPrescriptionToPdf(docItem != null ? docItem.doctor : null, sel.patient, c);
            } else {
                showAlert("Success", "Consultation and prescription saved to patient record.");
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
            chooser.setTitle("Save Homeopathic Prescription PDF");
            String defaultName = String.format("Prescription_%s_%s.pdf",
                    patient.caseNumber().replace(" ", "_"),
                    LocalDate.now().toString());
            chooser.setInitialFileName(defaultName);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));

            File targetFile = chooser.showSaveDialog(getScene().getWindow());
            if (targetFile != null) {
                boolean autoFit = autoFitCheck.isSelected();
                pdfService.generatePrescriptionPdf(doctor, patient, consultation, autoFit, targetFile);

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
        grid.setHgap(8);
        grid.setVgap(8);

        TextField nameField = new TextField();
        nameField.setPromptText("Dr. Full Name (e.g. Dr. A. K. Roy)");

        TextField regField = new TextField();
        regField.setPromptText("Registration No. (e.g. WBH-12345 / CCH)");

        TextField qualField = new TextField();
        qualField.setPromptText("Qualifications (e.g. B.H.M.S., M.D. Hom.)");

        TextField desigField = new TextField("Consulting Homeopathic Physician");
        desigField.setPromptText("Designation");

        TextField clinicField = new TextField("Classical Homeopathy Wellness Clinic");
        clinicField.setPromptText("Clinic / Hospital Name");

        TextField addrField = new TextField();
        addrField.setPromptText("Address");

        TextField phoneField = new TextField();
        phoneField.setPromptText("Contact Phone");

        TextField emailField = new TextField();
        emailField.setPromptText("Email Address");

        TextField sigField = new TextField("Authorized Signatory");
        sigField.setPromptText("Signature / Stamp Notes");

        grid.add(new Label("Full Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Reg. No:"), 0, 1); grid.add(regField, 1, 1);
        grid.add(new Label("Qualification:"), 0, 2); grid.add(qualField, 1, 2);
        grid.add(new Label("Designation:"), 0, 3); grid.add(desigField, 1, 3);
        grid.add(new Label("Clinic Name:"), 0, 4); grid.add(clinicField, 1, 4);
        grid.add(new Label("Address:"), 0, 5); grid.add(addrField, 1, 5);
        grid.add(new Label("Phone:"), 0, 6); grid.add(phoneField, 1, 6);
        grid.add(new Label("Email:"), 0, 7); grid.add(emailField, 1, 7);
        grid.add(new Label("Sign Seal:"), 0, 8); grid.add(sigField, 1, 8);

        Button enlistBtn = new Button("Enlist This Doctor");
        enlistBtn.getStyleClass().addAll(Styles.ACCENT);
        enlistBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isBlank()) {
                showAlert("Validation", "Doctor full name is required.");
                return;
            }
            try {
                Doctor newDoc = new Doctor(
                        UUID.randomUUID().toString(),
                        name,
                        regField.getText().trim(),
                        qualField.getText().trim(),
                        desigField.getText().trim(),
                        clinicField.getText().trim(),
                        addrField.getText().trim(),
                        phoneField.getText().trim(),
                        emailField.getText().trim(),
                        sigField.getText().trim(),
                        true,
                        Instant.now()
                );
                clinicDao.saveDoctor(newDoc);
                refreshList.run();
                loadDoctors();
                nameField.clear();
                regField.clear();
                qualField.clear();
                showAlert("Doctor Enlisted", "Doctor " + name + " enlisted successfully.");
            } catch (Exception ex) {
                log.error("Failed to save doctor", ex);
                showAlert("Error", "Could not enlist doctor: " + ex.getMessage());
            }
        });

        content.getChildren().addAll(
                new Label("Currently Enlisted Doctors:"),
                docListView,
                new Separator(),
                new Label("Enlist New Doctor:"),
                grid,
                enlistBtn
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
        TextField constField = new TextField("Phosphorus / Calcarea");
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
        grid.add(new Label("Constitution:"), 0, 8); grid.add(constField, 1, 8);
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
                            constField.getText().trim(),
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
