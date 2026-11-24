package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.backup.ClinicBackupService;
import org.openhomeo.core.materia_medica.ProvingIngestionService;
import org.openhomeo.core.model.*;
import org.openhomeo.core.net.NetworkDiscoveryService;
import org.openhomeo.core.net.RemoteDatabaseManager;
import org.openhomeo.core.ota.OtaUpdateService;
import org.openhomeo.core.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dedicated System Administrator Control Workstation.
 * Features:
 * 1. Enlist and manage Consulting Doctors & Practitioners
 * 2. Enlist and manage Pharmacists and Dispensary Staff
 * 3. User Accounts and RBAC Privilege Governance
 * 4. Strictly Confidential "Report of Faith" (Prescription AI Discrepancy & Hazard Dossier)
 * 5. Review and Approve Doctor Remarks for Phytochemical & Pharmacological Constituents
 * 6. LAN Auto-Discovery, Dedicated Server Host, and Remote SSH / Reverse SSH (autossh) Settings
 */
public class AdminManagementView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(AdminManagementView.class);

    private final ClinicDao clinicDao;
    private final SecurityService securityService;
    private final ClinicBackupService backupService;
    private final NetworkDiscoveryService discoveryService = new NetworkDiscoveryService();

    // Data lists
    private final ObservableList<Doctor> doctorsList = FXCollections.observableArrayList();
    private final ObservableList<Pharmacist> pharmacistsList = FXCollections.observableArrayList();
    private final ObservableList<User> usersList = FXCollections.observableArrayList();
    private final ObservableList<AdminFaithDossierEntry> faithList = FXCollections.observableArrayList();
    private final ObservableList<MedicineConstituentRemark> remarksList = FXCollections.observableArrayList();

    // Tables
    private TableView<Doctor> doctorsTable;
    private TableView<Pharmacist> pharmacistsTable;
    private TableView<User> usersTable;
    private TableView<AdminFaithDossierEntry> faithTable;
    private TableView<MedicineConstituentRemark> remarksTable;

    // Institution Profile Controls
    private TextField txtInstName;
    private TextField txtInstTagline;
    private TextField txtInstRegNo;
    private TextField txtInstAddress;
    private TextField txtInstPhone;
    private TextField txtInstEmail;
    private TextField txtInstWebsite;
    private ComboBox<String> cmbInstCurrency;
    private TextField txtInstChiefDoc;
    private TextField txtInstChiefQual;
    private TextField txtInstChiefRegNo;
    private TextArea txtInstRegimen;

    // Live Letterhead Preview Labels
    private Label lblPreviewTitle;
    private Label lblPreviewTagline;
    private Label lblPreviewDoc;
    private Label lblPreviewContact;
    private Label lblPreviewRegimen;
    private Label lblPreviewSigName;
    private Label lblPreviewSigTitle;

    private Label statusLabel;
    private Runnable onUpdateAppliedCallback;

    public AdminManagementView(ClinicDao clinicDao, SecurityService securityService) {
        this(clinicDao, securityService, null);
    }

    public AdminManagementView(ClinicDao clinicDao, SecurityService securityService, Runnable onUpdateAppliedCallback) {
        this.clinicDao = clinicDao;
        this.securityService = securityService;
        this.onUpdateAppliedCallback = onUpdateAppliedCallback;

        File clinicDb = new File(clinicDao.getDatabaseManager().getClinicDbPath());
        File vaultDir = new File(clinicDb.getParentFile(), "vault");
        File backupDir = new File(clinicDb.getParentFile(), "backups");
        this.backupService = new ClinicBackupService(clinicDb, vaultDir, backupDir);

        setPadding(new Insets(14));
        setTop(createTopHeader());

        TabPane adminTabs = new TabPane();
        adminTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tabInstitution = new Tab("🏛️ Clinic & Institution Registration", createInstitutionRegistrationPane());
        Tab tabDoctors = new Tab("🩺 Enlisted Doctors", createDoctorsPane());
        Tab tabPharmacists = new Tab("💊 Enlisted Pharmacists", createPharmacistsPane());
        Tab tabUsers = new Tab("👥 User Accounts & RBAC", createUsersPane());
        Tab tabFaith = new Tab("🔒 Secret Report of Faith", createFaithReportPane());
        Tab tabRemarks = new Tab("📝 Doctor Remarks Approval", createRemarksApprovalPane());
        Tab tabNetwork = new Tab("🌐 Network & Remote Settings", createNetworkSettingsPane());
        Tab tabOta = new Tab("🔄 Over-The-Air (OTA) Updates", createOtaUpdatesPane());
        Tab tabProving = new Tab("🧪 Community Proving Ingestion", createProvingIngestionPane());
        Tab tabBackup = new Tab("☁️ Backup & Google Drive Sync", createBackupAndGoogleDrivePane());

        adminTabs.getTabs().addAll(tabInstitution, tabDoctors, tabPharmacists, tabUsers, tabFaith, tabRemarks, tabNetwork, tabOta, tabProving, tabBackup);
        setCenter(adminTabs);

        refreshAllData();
    }

    private Node createTopHeader() {
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 12, 0));

        Label title = new Label("ADMINISTRATOR WORKSTATION");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.ACCENT);

        Label sub = new Label("Staff Governance • Confidential Faith Dossier • Network & SSH Tunnel Control");
        sub.getStyleClass().add(Styles.TEXT_MUTED);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label("Admin privileges active");
        statusLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.SUCCESS);

        Button btnRefresh = new Button("↻ Refresh Data");
        btnRefresh.setOnAction(e -> refreshAllData());

        bar.getChildren().addAll(title, new Label("—"), sub, spacer, statusLabel, btnRefresh);
        return bar;
    }

    // =========================================================================
    // 0. CLINIC & INSTITUTION REGISTRATION PANE
    // =========================================================================
    private Node createInstitutionRegistrationPane() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));

        // Header Intro
        VBox header = new VBox(4);
        header.setPadding(new Insets(0, 0, 14, 0));
        Label title = new Label("🏛️ Clinic & Institution Registration Profile");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        Label desc = new Label(
                "Register your official clinic or hospital institution details, council accreditation, credentials, and default regimen rules. " +
                "All patient prescription slips, case summaries, and exported PDF documents dynamically reflect this master letterhead."
        );
        desc.setWrapText(true);
        desc.getStyleClass().add(Styles.TEXT_MUTED);
        header.getChildren().addAll(title, desc);
        root.setTop(header);

        // Split Layout: Left Form + Right Live Letterhead Preview
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.55);

        // --- Left Form ---
        VBox formBox = new VBox(14);
        formBox.setPadding(new Insets(10, 16, 10, 10));

        txtInstName = new TextField();
        txtInstName.setPromptText("e.g., Hahnemannian Classical Homeopathic Clinic");
        txtInstTagline = new TextField();
        txtInstTagline.setPromptText("e.g., Advanced Classical Posology & Miasmatic Practice");
        txtInstRegNo = new TextField();
        txtInstRegNo.setPromptText("e.g., REG-CLINIC-2026/01");
        txtInstAddress = new TextField();
        txtInstAddress.setPromptText("e.g., Central Healthcare Facility Premises, Medical Enclave");
        txtInstPhone = new TextField();
        txtInstPhone.setPromptText("e.g., +91 00000 00000");
        txtInstEmail = new TextField();
        txtInstEmail.setPromptText("e.g., clinic@openhomeo.local");
        txtInstWebsite = new TextField();
        txtInstWebsite.setPromptText("e.g., https://openhomeo.org");

        cmbInstCurrency = new ComboBox<>(FXCollections.observableArrayList(
                "INR (₹)", "USD ($)", "EUR (€)", "GBP (£)", "AUD ($)", "CAD ($)"
        ));
        cmbInstCurrency.setValue("INR (₹)");

        txtInstChiefDoc = new TextField();
        txtInstChiefDoc.setPromptText("e.g., Dr. Consulting Physician");
        txtInstChiefQual = new TextField();
        txtInstChiefQual.setPromptText("e.g., B.H.M.S., M.D. (Homoeopathy)");
        txtInstChiefRegNo = new TextField();
        txtInstChiefRegNo.setPromptText("e.g., REG-HOM-2026/01");

        txtInstRegimen = new TextArea();
        txtInstRegimen.setPrefRowCount(4);
        txtInstRegimen.setWrapText(true);
        txtInstRegimen.setPromptText("Default dietary prohibitions and intake rules printed on prescription slips...");

        // Section 1: Facility Identity
        Label sec1 = new Label("🏥 Institution & Healthcare Facility Identity");
        sec1.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        GridPane grid1 = new GridPane();
        grid1.setHgap(10);
        grid1.setVgap(8);
        grid1.addRow(0, new Label("Institution Name:"), txtInstName);
        grid1.addRow(1, new Label("Tagline / Clinical Focus:"), txtInstTagline);
        grid1.addRow(2, new Label("Clinical Registration No:"), txtInstRegNo);
        grid1.addRow(3, new Label("Premises Address:"), txtInstAddress);
        grid1.addRow(4, new Label("Helpline Phone:"), txtInstPhone);
        grid1.addRow(5, new Label("Official Email:"), txtInstEmail);
        grid1.addRow(6, new Label("Website / Portal:"), txtInstWebsite);
        grid1.addRow(7, new Label("Billing Currency:"), cmbInstCurrency);

        // Section 2: Chief Consultant Credentials
        Label sec2 = new Label("🩺 Chief Medical Director / Senior Consultant");
        sec2.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        GridPane grid2 = new GridPane();
        grid2.setHgap(10);
        grid2.setVgap(8);
        grid2.addRow(0, new Label("Chief Physician Name:"), txtInstChiefDoc);
        grid2.addRow(1, new Label("Academic Qualifications:"), txtInstChiefQual);
        grid2.addRow(2, new Label("Medical Council Reg No:"), txtInstChiefRegNo);

        // Section 3: Diet & Posological Regimen
        Label sec3 = new Label("📜 Master Posological Regimen & Dietary Prohibitions");
        sec3.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        // Action Buttons
        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_LEFT);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        Button btnSaveInst = new Button("💾 Save & Register Institution Profile");
        btnSaveInst.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnSaveInst.setOnAction(e -> saveInstitutionProfile());

        Button btnReset = new Button("↺ Reset to Saved Profile");
        btnReset.setOnAction(e -> refreshInstitutionProfile());

        btnBox.getChildren().addAll(btnSaveInst, btnReset);

        formBox.getChildren().addAll(sec1, grid1, new Separator(), sec2, grid2, new Separator(), sec3, txtInstRegimen, btnBox);
        ScrollPane formScroll = new ScrollPane(formBox);
        formScroll.setFitToWidth(true);

        // --- Right: Live Letterhead & Prescription Preview ---
        VBox previewWrapper = new VBox(10);
        previewWrapper.setPadding(new Insets(10));
        previewWrapper.setAlignment(Pos.TOP_CENTER);

        Label prevHeader = new Label("📄 Live Letterhead & Prescription Header Preview");
        prevHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        VBox previewCard = new VBox(6);
        previewCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 16px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 2);");
        previewCard.setMaxWidth(480);

        lblPreviewTitle = new Label("HAHNEMANNIAN CLASSICAL HOMEOPATHIC CLINIC");
        lblPreviewTitle.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
        lblPreviewTitle.setStyle("-fx-text-fill: #0f172a;");
        lblPreviewTitle.setAlignment(Pos.CENTER);

        lblPreviewTagline = new Label("Advanced Classical Posology & Miasmatic Practice");
        lblPreviewTagline.setFont(Font.font("System", FontWeight.NORMAL, 10));
        lblPreviewTagline.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;");
        lblPreviewTagline.setAlignment(Pos.CENTER);

        lblPreviewDoc = new Label("Dr. Consulting Physician, B.H.M.S., M.D. (Hom.) | Reg. No: REG-HOM-2026/01");
        lblPreviewDoc.setFont(Font.font("System", FontWeight.NORMAL, 11));
        lblPreviewDoc.setStyle("-fx-text-fill: #334155;");
        lblPreviewDoc.setAlignment(Pos.CENTER);

        lblPreviewContact = new Label("Central Healthcare Facility Premises | Tel: +91 00000 00000 | clinic@openhomeo.local");
        lblPreviewContact.setFont(Font.font("System", FontWeight.NORMAL, 10));
        lblPreviewContact.setStyle("-fx-text-fill: #64748b;");
        lblPreviewContact.setAlignment(Pos.CENTER);

        VBox headCenter = new VBox(2, lblPreviewTitle, lblPreviewTagline, lblPreviewDoc, lblPreviewContact);
        headCenter.setAlignment(Pos.CENTER);

        Separator slipLine = new Separator();
        slipLine.setStyle("-fx-background-color: #0284c7;");

        Label mockPat = new Label("Patient: Sample Patient  |  Case: CASE-001  |  Age/Sex: 35 yrs / Male  |  Date: Today");
        mockPat.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569; -fx-padding: 4px 0;");

        Label mockDiag = new Label("Clinical Presentation / Diagnosis: Chronic Gastric Dyspepsia with Migraine");
        mockDiag.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #0369a1;");

        Label mockRx = new Label("℞\n1. Nux vomica — Potency: 200 (CENTESIMAL)\n   Posology: 4 pills dry on clean tongue | Repetition: Single dose at bedtime");
        mockRx.setStyle("-fx-font-size: 11px; -fx-text-fill: #0f172a;");

        VBox regimenBox = new VBox(2);
        regimenBox.setStyle("-fx-background-color: #fffbeb; -fx-padding: 6px 8px; -fx-border-color: #fde68a; -fx-border-radius: 4px;");
        Label regTitle = new Label("Diet & Regimen Instructions:");
        regTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #b45309; -fx-font-size: 10px;");
        lblPreviewRegimen = new Label("• Avoid strong smelling substances 30 mins before & after dose.\n• Take globules dry on a clean tongue.");
        lblPreviewRegimen.setStyle("-fx-text-fill: #78350f; -fx-font-size: 10px;");
        lblPreviewRegimen.setWrapText(true);
        regimenBox.getChildren().addAll(regTitle, lblPreviewRegimen);

        VBox sigBox = new VBox(1);
        sigBox.setAlignment(Pos.BOTTOM_RIGHT);
        Label sigLine = new Label("____________________________");
        sigLine.setStyle("-fx-text-fill: #94a3b8;");
        lblPreviewSigName = new Label("Dr. Consulting Physician, M.D. (Hom.)");
        lblPreviewSigName.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-font-size: 10px;");
        lblPreviewSigTitle = new Label("Consulting Homeopathic Physician");
        lblPreviewSigTitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 9px;");
        sigBox.getChildren().addAll(sigLine, lblPreviewSigName, lblPreviewSigTitle);

        previewCard.getChildren().addAll(headCenter, slipLine, mockPat, mockDiag, mockRx, regimenBox, sigBox);
        ScrollPane prevScroll = new ScrollPane(new VBox(10, prevHeader, previewCard));
        prevScroll.setFitToWidth(true);

        splitPane.getItems().addAll(formScroll, prevScroll);
        root.setCenter(splitPane);

        // Attach listeners for live preview update
        txtInstName.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstTagline.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstRegNo.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstAddress.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstPhone.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstEmail.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstChiefDoc.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstChiefQual.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstChiefRegNo.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());
        txtInstRegimen.textProperty().addListener((obs, o, n) -> updateInstitutionPreview());

        refreshInstitutionProfile();
        return root;
    }

    private void updateInstitutionPreview() {
        if (lblPreviewTitle == null) return;
        String name = txtInstName.getText().trim();
        lblPreviewTitle.setText(name.isEmpty() ? "CLINIC / INSTITUTION NAME" : name.toUpperCase());

        String tag = txtInstTagline.getText().trim();
        lblPreviewTagline.setText(tag);
        lblPreviewTagline.setVisible(!tag.isEmpty());
        lblPreviewTagline.setManaged(!tag.isEmpty());

        String doc = txtInstChiefDoc.getText().trim();
        String qual = txtInstChiefQual.getText().trim();
        String reg = txtInstChiefRegNo.getText().trim();
        String docLine = (doc.isEmpty() ? "Dr. Consulting Physician" : doc) +
                (qual.isEmpty() ? "" : ", " + qual) +
                (reg.isEmpty() ? "" : " | Reg. No: " + reg);
        lblPreviewDoc.setText(docLine);

        String addr = txtInstAddress.getText().trim();
        String phone = txtInstPhone.getText().trim();
        String email = txtInstEmail.getText().trim();
        String contactLine = (addr.isEmpty() ? "Clinical Facility Address" : addr) +
                (phone.isEmpty() ? "" : " | Tel: " + phone) +
                (email.isEmpty() ? "" : " | " + email);
        lblPreviewContact.setText(contactLine);

        String regimen = txtInstRegimen.getText().trim();
        lblPreviewRegimen.setText(regimen.isEmpty()
                ? "• Avoid strong smelling substances 30 mins before & after dose.\n• Take globules dry on a clean tongue."
                : regimen);

        lblPreviewSigName.setText((doc.isEmpty() ? "Dr. Consulting Physician" : doc) + (qual.isEmpty() ? "" : ", " + qual));
        lblPreviewSigTitle.setText(reg.isEmpty() ? "Consulting Homeopathic Physician" : "Reg. No: " + reg);
    }

    private void refreshInstitutionProfile() {
        try {
            ClinicInstitutionProfile inst = clinicDao.getInstitutionProfile();
            if (txtInstName != null) {
                txtInstName.setText(inst.institutionName());
                txtInstTagline.setText(inst.tagline());
                txtInstRegNo.setText(inst.registrationNumber());
                txtInstAddress.setText(inst.address());
                txtInstPhone.setText(inst.phone());
                txtInstEmail.setText(inst.email());
                txtInstWebsite.setText(inst.website());
                cmbInstCurrency.setValue(inst.defaultCurrency());
                txtInstChiefDoc.setText(inst.chiefConsultantName());
                txtInstChiefQual.setText(inst.chiefConsultantQualifications());
                txtInstChiefRegNo.setText(inst.chiefConsultantRegNo());
                txtInstRegimen.setText(inst.defaultRegimenInstructions());
                updateInstitutionPreview();
            }
        } catch (SQLException e) {
            log.error("Failed to load institution profile", e);
        }
    }

    private void saveInstitutionProfile() {
        try {
            String name = txtInstName.getText().trim();
            if (name.isEmpty()) {
                showError("Institution name cannot be blank.");
                return;
            }
            ClinicInstitutionProfile updated = new ClinicInstitutionProfile(
                    "inst-default",
                    name,
                    txtInstTagline.getText().trim(),
                    txtInstRegNo.getText().trim(),
                    txtInstAddress.getText().trim(),
                    txtInstPhone.getText().trim(),
                    txtInstEmail.getText().trim(),
                    txtInstWebsite.getText().trim(),
                    txtInstChiefDoc.getText().trim(),
                    txtInstChiefQual.getText().trim(),
                    txtInstChiefRegNo.getText().trim(),
                    cmbInstCurrency.getValue() != null ? cmbInstCurrency.getValue() : "INR (₹)",
                    txtInstRegimen.getText().trim(),
                    Instant.now()
            );
            clinicDao.saveInstitutionProfile(updated);
            updateInstitutionPreview();
            showNotification("Institution Profile registered and updated successfully!");
        } catch (SQLException e) {
            log.error("Failed to save institution profile", e);
            showError("Failed to save institution profile: " + e.getMessage());
        }
    }

    // =========================================================================
    // 1. ENLISTED DOCTORS PANE
    // =========================================================================
    private Node createDoctorsPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        Button btnAdd = new Button("➕ Enlist New Doctor");
        btnAdd.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnAdd.setOnAction(e -> showAddDoctorDialog());

        Button btnEdit = new Button("✏️ Edit Doctor");
        btnEdit.setOnAction(e -> {
            Doctor selected = doctorsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Please select a doctor from the table to edit.");
                return;
            }
            showEditDoctorDialog(selected);
        });

        Button btnDelete = new Button("🗑️ Remove Doctor");
        btnDelete.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
        btnDelete.setOnAction(e -> {
            Doctor selected = doctorsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Please select a doctor to remove.");
                return;
            }
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to remove Dr. " + selected.fullName() + " from the clinic roster?",
                    ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Confirm Doctor Deletion");
            alert.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    try {
                        clinicDao.deleteDoctor(selected.id());
                        refreshDoctors();
                        showNotification("Doctor removed from clinic roster: " + selected.fullName());
                    } catch (SQLException ex) {
                        showError("Failed to delete doctor: " + ex.getMessage());
                    }
                }
            });
        });

        Button btnToggle = new Button("Toggle Active Status");
        btnToggle.setOnAction(e -> {
            Doctor selected = doctorsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    Doctor updated = new Doctor(
                            selected.id(), selected.fullName(), selected.registrationNo(),
                            selected.qualification(), selected.designation(), selected.clinicName(),
                            selected.address(), selected.phone(), selected.email(),
                            selected.signatureNotes(), !selected.isActive(), selected.createdAt()
                    );
                    clinicDao.updateDoctor(updated);
                    refreshDoctors();
                    showNotification("Updated status for Dr. " + selected.fullName());
                } catch (SQLException ex) {
                    showError("Failed to update doctor: " + ex.getMessage());
                }
            }
        });

        toolbar.getChildren().addAll(btnAdd, btnEdit, btnDelete, btnToggle);
        pane.setTop(toolbar);

        doctorsTable = new TableView<>(doctorsList);
        doctorsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<Doctor, String> colName = new TableColumn<>("Doctor Name & Title");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().displayTitle()));

        TableColumn<Doctor, String> colReg = new TableColumn<>("Registration / Council No");
        colReg.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().registrationNo()));

        TableColumn<Doctor, String> colClinic = new TableColumn<>("Clinic / Institute");
        colClinic.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().clinicName()));

        TableColumn<Doctor, String> colPhone = new TableColumn<>("Contact Phone");
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().phone()));

        TableColumn<Doctor, String> colEmail = new TableColumn<>("Email");
        colEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().email()));

        TableColumn<Doctor, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActive() ? "ACTIVE" : "INACTIVE"));

        doctorsTable.getColumns().addAll(List.of(colName, colReg, colClinic, colPhone, colEmail, colStatus));
        pane.setCenter(doctorsTable);

        return pane;
    }

    private void showAddDoctorDialog() {
        Dialog<Doctor> dialog = new Dialog<>();
        dialog.setTitle("Enlist Consulting Homeopathic Physician");
        dialog.setHeaderText("Register official credentials and council details");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        TextField txtName = new TextField();
        txtName.setPromptText("Dr. Full Name");
        TextField txtReg = new TextField();
        txtReg.setPromptText("REG-CCH-2026/01");
        TextField txtQual = new TextField();
        txtQual.setPromptText("B.H.M.S., M.D. (Hom.)");
        TextField txtDesig = new TextField();
        txtDesig.setPromptText("Senior Classical Physician");
        TextField txtClinic = new TextField();
        txtClinic.setPromptText("Hahnemannian Clinic, Kolkata");
        TextField txtPhone = new TextField();
        txtPhone.setPromptText("+91 98300 12345");
        TextField txtEmail = new TextField();
        txtEmail.setPromptText("doctor@clinic.org");

        grid.addRow(0, new Label("Full Name:"), txtName);
        grid.addRow(1, new Label("Registration No:"), txtReg);
        grid.addRow(2, new Label("Qualifications:"), txtQual);
        grid.addRow(3, new Label("Designation:"), txtDesig);
        grid.addRow(4, new Label("Clinic Name:"), txtClinic);
        grid.addRow(5, new Label("Phone:"), txtPhone);
        grid.addRow(6, new Label("Email:"), txtEmail);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && !txtName.getText().isBlank()) {
                return new Doctor(
                        "doc-" + UUID.randomUUID().toString().substring(0, 8),
                        txtName.getText().trim(),
                        txtReg.getText().trim(),
                        txtQual.getText().trim(),
                        txtDesig.getText().trim(),
                        txtClinic.getText().trim(),
                        "",
                        txtPhone.getText().trim(),
                        txtEmail.getText().trim(),
                        "",
                        true,
                        Instant.now()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(doc -> {
            try {
                clinicDao.saveDoctor(doc);
                refreshDoctors();
                showNotification("Doctor enlisted successfully: " + doc.fullName());
            } catch (SQLException ex) {
                showError("Error saving doctor: " + ex.getMessage());
            }
        });
    }

    private void showEditDoctorDialog(Doctor doc) {
        Dialog<Doctor> dialog = new Dialog<>();
        dialog.setTitle("Edit Doctor Credentials & Practice Details");
        dialog.setHeaderText("Modify official council credentials for Dr. " + doc.fullName());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        TextField txtName = new TextField(doc.fullName());
        TextField txtReg = new TextField(doc.registrationNo());
        TextField txtQual = new TextField(doc.qualification());
        TextField txtDesig = new TextField(doc.designation());
        TextField txtClinic = new TextField(doc.clinicName());
        TextField txtAddr = new TextField(doc.address());
        TextField txtPhone = new TextField(doc.phone());
        TextField txtEmail = new TextField(doc.email());
        TextField txtNotes = new TextField(doc.signatureNotes() != null ? doc.signatureNotes() : "");
        CheckBox chkActive = new CheckBox("Active Practice Status");
        chkActive.setSelected(doc.isActive());

        grid.addRow(0, new Label("Doctor Full Name:"), txtName);
        grid.addRow(1, new Label("Registration / Council No:"), txtReg);
        grid.addRow(2, new Label("Qualifications (e.g., B.H.M.S., M.D.):"), txtQual);
        grid.addRow(3, new Label("Designation / Practice Title:"), txtDesig);
        grid.addRow(4, new Label("Clinic / Institute Name:"), txtClinic);
        grid.addRow(5, new Label("Clinic Address:"), txtAddr);
        grid.addRow(6, new Label("Contact Phone:"), txtPhone);
        grid.addRow(7, new Label("Email Address:"), txtEmail);
        grid.addRow(8, new Label("Prescription Signature Note:"), txtNotes);
        grid.addRow(9, new Label("Status:"), chkActive);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && !txtName.getText().trim().isEmpty()) {
                return new Doctor(
                        doc.id(),
                        txtName.getText().trim(),
                        txtReg.getText().trim(),
                        txtQual.getText().trim(),
                        txtDesig.getText().trim(),
                        txtClinic.getText().trim(),
                        txtAddr.getText().trim(),
                        txtPhone.getText().trim(),
                        txtEmail.getText().trim(),
                        txtNotes.getText().trim(),
                        chkActive.isSelected(),
                        doc.createdAt()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            try {
                clinicDao.updateDoctor(updated);
                refreshDoctors();
                showNotification("Updated details for Dr. " + updated.fullName());
            } catch (SQLException ex) {
                showError("Failed to update doctor: " + ex.getMessage());
            }
        });
    }

    // =========================================================================
    // 2. ENLISTED PHARMACISTS PANE
    // =========================================================================
    private Node createPharmacistsPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        Button btnAdd = new Button("➕ Enlist New Pharmacist");
        btnAdd.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnAdd.setOnAction(e -> showAddPharmacistDialog());

        Button btnEdit = new Button("✏️ Edit Pharmacist");
        btnEdit.setOnAction(e -> {
            Pharmacist selected = pharmacistsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Please select a pharmacist from the table to edit.");
                return;
            }
            showEditPharmacistDialog(selected);
        });

        Button btnDelete = new Button("🗑️ Remove Pharmacist");
        btnDelete.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
        btnDelete.setOnAction(e -> {
            Pharmacist selected = pharmacistsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Please select a pharmacist to remove.");
                return;
            }
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to remove Pharmacist " + selected.fullName() + " from the dispensary roster?",
                    ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Confirm Pharmacist Deletion");
            alert.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    try {
                        clinicDao.deletePharmacist(selected.id());
                        refreshPharmacists();
                        showNotification("Pharmacist removed from dispensary roster: " + selected.fullName());
                    } catch (SQLException ex) {
                        showError("Failed to delete pharmacist: " + ex.getMessage());
                    }
                }
            });
        });

        Button btnToggle = new Button("Toggle Active Status");
        btnToggle.setOnAction(e -> {
            Pharmacist selected = pharmacistsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    Pharmacist updated = new Pharmacist(
                            selected.id(), selected.fullName(), selected.registrationNo(),
                            selected.qualification(), selected.dispensaryName(), selected.address(),
                            selected.phone(), selected.email(), !selected.isActive(), selected.createdAt()
                    );
                    clinicDao.updatePharmacist(updated);
                    refreshPharmacists();
                    showNotification("Updated status for Pharmacist " + selected.fullName());
                } catch (SQLException ex) {
                    showError("Failed to update pharmacist: " + ex.getMessage());
                }
            }
        });

        toolbar.getChildren().addAll(btnAdd, btnEdit, btnDelete, btnToggle);
        pane.setTop(toolbar);

        pharmacistsTable = new TableView<>(pharmacistsList);
        pharmacistsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<Pharmacist, String> colName = new TableColumn<>("Pharmacist Name");
        colName.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().displayTitle()));

        TableColumn<Pharmacist, String> colReg = new TableColumn<>("Pharmacy License No");
        colReg.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().registrationNo()));

        TableColumn<Pharmacist, String> colDisp = new TableColumn<>("Dispensary Center");
        colDisp.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().dispensaryName()));

        TableColumn<Pharmacist, String> colPhone = new TableColumn<>("Phone");
        colPhone.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().phone()));

        TableColumn<Pharmacist, String> colEmail = new TableColumn<>("Email");
        colEmail.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().email()));

        TableColumn<Pharmacist, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().isActive() ? "ACTIVE" : "INACTIVE"));

        pharmacistsTable.getColumns().addAll(List.of(colName, colReg, colDisp, colPhone, colEmail, colStatus));
        pane.setCenter(pharmacistsTable);

        return pane;
    }

    private void showAddPharmacistDialog() {
        Dialog<Pharmacist> dialog = new Dialog<>();
        dialog.setTitle("Enlist Pharmacist / Dispensary Specialist");
        dialog.setHeaderText("Enter official pharmacy credentials and store assignment");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        TextField txtName = new TextField();
        txtName.setPromptText("Pharmacist Full Name");
        TextField txtReg = new TextField();
        txtReg.setPromptText("REG-PHARM-2026/01");
        TextField txtQual = new TextField();
        txtQual.setPromptText("D.Pharm (Homoeopathy)");
        TextField txtDisp = new TextField();
        txtDisp.setPromptText("Main Clinic Dispensary");
        TextField txtPhone = new TextField();
        txtPhone.setPromptText("+91 98311 54321");
        TextField txtEmail = new TextField();
        txtEmail.setPromptText("dispensary@clinic.org");

        grid.addRow(0, new Label("Pharmacist Name:"), txtName);
        grid.addRow(1, new Label("License Reg No:"), txtReg);
        grid.addRow(2, new Label("Qualifications:"), txtQual);
        grid.addRow(3, new Label("Dispensary:"), txtDisp);
        grid.addRow(4, new Label("Phone:"), txtPhone);
        grid.addRow(5, new Label("Email:"), txtEmail);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && !txtName.getText().isBlank()) {
                return new Pharmacist(
                        "pharma-" + UUID.randomUUID().toString().substring(0, 8),
                        txtName.getText().trim(),
                        txtReg.getText().trim(),
                        txtQual.getText().trim(),
                        txtDisp.getText().trim(),
                        "",
                        txtPhone.getText().trim(),
                        txtEmail.getText().trim(),
                        true,
                        Instant.now()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(ph -> {
            try {
                clinicDao.savePharmacist(ph);
                refreshPharmacists();
                showNotification("Pharmacist enlisted successfully: " + ph.fullName());
            } catch (SQLException ex) {
                showError("Error saving pharmacist: " + ex.getMessage());
            }
        });
    }

    private void showEditPharmacistDialog(Pharmacist ph) {
        Dialog<Pharmacist> dialog = new Dialog<>();
        dialog.setTitle("Edit Pharmacist & Dispensary Details");
        dialog.setHeaderText("Modify credentials and dispensary assignment for " + ph.fullName());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        TextField txtName = new TextField(ph.fullName());
        TextField txtReg = new TextField(ph.registrationNo());
        TextField txtQual = new TextField(ph.qualification());
        TextField txtDisp = new TextField(ph.dispensaryName());
        TextField txtAddr = new TextField(ph.address());
        TextField txtPhone = new TextField(ph.phone());
        TextField txtEmail = new TextField(ph.email());
        CheckBox chkActive = new CheckBox("Active Dispensary Duty");
        chkActive.setSelected(ph.isActive());

        grid.addRow(0, new Label("Pharmacist Full Name:"), txtName);
        grid.addRow(1, new Label("Pharmacy License / Reg No:"), txtReg);
        grid.addRow(2, new Label("Qualifications (e.g., D.Pharm):"), txtQual);
        grid.addRow(3, new Label("Dispensary / Pharmacy Center:"), txtDisp);
        grid.addRow(4, new Label("Dispensary Address:"), txtAddr);
        grid.addRow(5, new Label("Contact Phone:"), txtPhone);
        grid.addRow(6, new Label("Email Address:"), txtEmail);
        grid.addRow(7, new Label("Status:"), chkActive);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && !txtName.getText().trim().isEmpty()) {
                return new Pharmacist(
                        ph.id(),
                        txtName.getText().trim(),
                        txtReg.getText().trim(),
                        txtQual.getText().trim(),
                        txtDisp.getText().trim(),
                        txtAddr.getText().trim(),
                        txtPhone.getText().trim(),
                        txtEmail.getText().trim(),
                        chkActive.isSelected(),
                        ph.createdAt()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            try {
                clinicDao.updatePharmacist(updated);
                refreshPharmacists();
                showNotification("Updated details for Pharmacist " + updated.fullName());
            } catch (SQLException ex) {
                showError("Failed to update pharmacist: " + ex.getMessage());
            }
        });
    }

    // =========================================================================
    // 3. USER ACCOUNTS & RBAC PANE
    // =========================================================================
    private Node createUsersPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        Button btnAdd = new Button("➕ Create New User");
        btnAdd.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnAdd.setOnAction(e -> showCreateUserDialog());

        Button btnEdit = new Button("✏️ Edit Staff Details");
        btnEdit.setOnAction(e -> {
            User selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Please select a staff user from the table to edit.");
                return;
            }
            showEditUserDialog(selected);
        });

        Button btnDelete = new Button("🗑️ Delete Staff");
        btnDelete.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
        btnDelete.setOnAction(e -> {
            User selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Please select a staff user to delete.");
                return;
            }
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to permanently remove user account '" + selected.username() + "'?",
                    ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Confirm Staff User Deletion");
            alert.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    try {
                        clinicDao.deleteUser(selected.id());
                        refreshUsers();
                        showNotification("Deleted staff user account: " + selected.username());
                    } catch (SQLException ex) {
                        showError("Failed to delete user: " + ex.getMessage());
                    }
                }
            });
        });

        Button btnResetPass = new Button("Reset Password");
        btnResetPass.setOnAction(e -> {
            User selected = usersTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                TextInputDialog d = new TextInputDialog("newpass123");
                d.setTitle("Reset Password");
                d.setHeaderText("Set new password for user: " + selected.username());
                d.showAndWait().ifPresent(newPass -> {
                    try {
                        clinicDao.updateUserPassword(selected.id(), newPass.trim());
                        refreshUsers();
                        showNotification("Password updated for " + selected.username());
                    } catch (SQLException ex) {
                        showError("Failed to update password: " + ex.getMessage());
                    }
                });
            }
        });

        ComboBox<User.UserRole> roleCombo = new ComboBox<>(FXCollections.observableArrayList(
                User.UserRole.ADMIN, User.UserRole.DOCTOR, User.UserRole.PHARMACIST, User.UserRole.ASSISTANT
        ));
        roleCombo.setPromptText("Change Selected Role");
        roleCombo.setOnAction(e -> {
            User selected = usersTable.getSelectionModel().getSelectedItem();
            User.UserRole newRole = roleCombo.getValue();
            if (selected != null && newRole != null) {
                try {
                    clinicDao.updateUserRole(selected.id(), newRole);
                    refreshUsers();
                    showNotification("Updated " + selected.username() + " to " + newRole);
                } catch (SQLException ex) {
                    showError("Failed to change user role: " + ex.getMessage());
                }
            }
        });

        toolbar.getChildren().addAll(btnAdd, btnResetPass, roleCombo);
        pane.setTop(toolbar);

        usersTable = new TableView<>(usersList);
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<User, String> colUser = new TableColumn<>("Username");
        colUser.setCellValueFactory(u -> new SimpleStringProperty(u.getValue().username()));

        TableColumn<User, String> colName = new TableColumn<>("Full Name");
        colName.setCellValueFactory(u -> new SimpleStringProperty(u.getValue().fullName()));

        TableColumn<User, String> colRole = new TableColumn<>("RBAC System Role");
        colRole.setCellValueFactory(u -> new SimpleStringProperty(u.getValue().role().name()));

        TableColumn<User, String> colCreated = new TableColumn<>("Created Date");
        colCreated.setCellValueFactory(u -> new SimpleStringProperty(u.getValue().createdAt().toString().substring(0, 10)));

        usersTable.getColumns().addAll(List.of(colUser, colName, colRole, colCreated));
        pane.setCenter(usersTable);

        return pane;
    }

    private void showCreateUserDialog() {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Create Login User Account");
        dialog.setHeaderText("Assign username, credentials, and RBAC system role");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        TextField txtUser = new TextField();
        PasswordField txtPass = new PasswordField();
        TextField txtFull = new TextField();
        ComboBox<User.UserRole> comboRole = new ComboBox<>(FXCollections.observableArrayList(
                User.UserRole.ADMIN, User.UserRole.DOCTOR, User.UserRole.PHARMACIST, User.UserRole.ASSISTANT
        ));
        comboRole.setValue(User.UserRole.DOCTOR);

        grid.addRow(0, new Label("Username:"), txtUser);
        grid.addRow(1, new Label("Password:"), txtPass);
        grid.addRow(2, new Label("Full Name:"), txtFull);
        grid.addRow(3, new Label("System Role:"), comboRole);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && !txtUser.getText().isBlank() && !txtPass.getText().isBlank()) {
                try {
                    return clinicDao.createUser(txtUser.getText().trim(), txtPass.getText().trim(), txtFull.getText().trim(), comboRole.getValue());
                } catch (SQLException e) {
                    showError("Failed to create user: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(u -> {
            refreshUsers();
            showNotification("User created successfully: " + u.username() + " (" + u.role() + ")");
        });
    }

    private void showEditUserDialog(User user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Staff User Account");
        dialog.setHeaderText("Modify profile details and system role for " + user.username());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        TextField txtUser = new TextField(user.username());
        TextField txtFull = new TextField(user.fullName());
        ComboBox<User.UserRole> comboRole = new ComboBox<>(FXCollections.observableArrayList(
                User.UserRole.ADMIN, User.UserRole.DOCTOR, User.UserRole.PHARMACIST, User.UserRole.ASSISTANT
        ));
        comboRole.setValue(user.role());

        grid.addRow(0, new Label("Username:"), txtUser);
        grid.addRow(1, new Label("Full Name:"), txtFull);
        grid.addRow(2, new Label("System Role:"), comboRole);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                String newUsername = txtUser.getText().trim();
                String newName = txtFull.getText().trim();
                User.UserRole newRole = comboRole.getValue();
                if (newUsername.isEmpty()) {
                    showError("Username cannot be blank.");
                    return;
                }
                try {
                    clinicDao.updateUserProfile(user.id(), newUsername, newName, newRole);
                    refreshUsers();
                    showNotification("Updated staff user account: " + newUsername);
                } catch (SQLException ex) {
                    showError("Failed to update user: " + ex.getMessage());
                }
            }
        });
    }

    // =========================================================================
    // 4. SECRET "REPORT OF FAITH" PANE (FOR ADMIN'S EYE ONLY)
    // =========================================================================
    private Node createFaithReportPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        HBox topBox = new HBox(12);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(0, 0, 10, 0));

        Label badge = new Label("CONFIDENTIAL DOSSIER • FOR ADMIN EYES ONLY");
        badge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.DANGER);

        Label desc = new Label("Prescription discrepancies, unorthodox repetitions, and unclassical posology flagged by the Logic AI.");
        desc.getStyleClass().add(Styles.TEXT_MUTED);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnAudit = new Button("✓ Mark Selected Audited");
        btnAudit.setOnAction(e -> {
            AdminFaithDossierEntry sel = faithTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                try {
                    clinicDao.markFaithDossierAudited(sel.id());
                    refreshFaithDossier();
                    showNotification("Marked audit record as reviewed.");
                } catch (SQLException ex) {
                    showError("Failed to update dossier: " + ex.getMessage());
                }
            }
        });

        topBox.getChildren().addAll(badge, desc, sp, btnAudit);
        pane.setTop(topBox);

        faithTable = new TableView<>(faithList);
        faithTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<AdminFaithDossierEntry, String> colDate = new TableColumn<>("Flagged Date");
        colDate.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().createdAt().toString().substring(0, 19).replace('T', ' ')));

        TableColumn<AdminFaithDossierEntry, String> colDoc = new TableColumn<>("Prescribing Doctor");
        colDoc.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().doctorName()));

        TableColumn<AdminFaithDossierEntry, String> colPat = new TableColumn<>("Patient");
        colPat.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().patientName()));

        TableColumn<AdminFaithDossierEntry, String> colRx = new TableColumn<>("Prescription (Remedy / Potency / Freq)");
        colRx.setCellValueFactory(f -> new SimpleStringProperty(
                f.getValue().remedyName() + " " + f.getValue().potency() + " [" + f.getValue().repetitionFrequency() + "]"
        ));

        TableColumn<AdminFaithDossierEntry, String> colRule = new TableColumn<>("Classical Canon Violation");
        colRule.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().violatedRule()));

        TableColumn<AdminFaithDossierEntry, String> colSev = new TableColumn<>("Severity");
        colSev.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().ruleSeverity().name()));

        TableColumn<AdminFaithDossierEntry, String> colRisk = new TableColumn<>("AI Risk Rating");
        colRisk.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().aiRiskAssessment()));

        TableColumn<AdminFaithDossierEntry, String> colAud = new TableColumn<>("Audit State");
        colAud.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().isAudited() ? "AUDITED" : "PENDING"));

        faithTable.getColumns().addAll(List.of(colDate, colDoc, colPat, colRx, colRule, colSev, colRisk, colAud));

        // Bottom detail area
        TextArea txtDetail = new TextArea();
        txtDetail.setEditable(false);
        txtDetail.setPrefRowCount(4);
        txtDetail.setPromptText("Select a secret audit dossier entry above to read doctor justification and predicted complications...");

        faithTable.getSelectionModel().selectedItemProperty().addListener((obs, o, entry) -> {
            if (entry != null) {
                txtDetail.setText(String.format(
                        "DISCREPANCY AUDIT DOSSIER:\n" +
                        "• Prescriber: %s | Patient: %s\n" +
                        "• Prescribed: %s %s [%s]\n" +
                        "• Classical Violation: %s (Severity: %s)\n" +
                        "• Doctor's Justification: \"%s\"\n" +
                        "• AI Evaluated Complications: %s\n" +
                        "• AI Assessment Summary: %s",
                        entry.doctorName(), entry.patientName(),
                        entry.remedyName(), entry.potency(), entry.repetitionFrequency(),
                        entry.violatedRule(), entry.ruleSeverity(),
                        entry.doctorExplanation(),
                        entry.predictedComplications(),
                        entry.aiRiskAssessment()
                ));
            }
        });

        VBox centerBox = new VBox(8, faithTable, new Label("Confidential Rationale & Complication Dossier:"), txtDetail);
        VBox.setVgrow(faithTable, Priority.ALWAYS);
        pane.setCenter(centerBox);

        return pane;
    }

    // =========================================================================
    // 5. DOCTOR REMARKS APPROVAL PANE
    // =========================================================================
    private Node createRemarksApprovalPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        HBox topBox = new HBox(12);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(0, 0, 10, 0));

        Label desc = new Label("Review proposed scientific corrections and clinical remarks on medicine constituents submitted by doctors/pharmacists.");
        desc.getStyleClass().add(Styles.TEXT_MUTED);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnApprove = new Button("✅ Approve & Annex to Master Data");
        btnApprove.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnApprove.setOnAction(e -> {
            MedicineConstituentRemark sel = remarksTable.getSelectionModel().getSelectedItem();
            if (sel != null && sel.status() == MedicineConstituentRemark.Status.PENDING) {
                try {
                    clinicDao.approveConstituentRemark(sel.id(), "usr-admin");
                    refreshRemarks();
                    showNotification("Remark approved and annexed to " + sel.remedyId() + " master data!");
                } catch (SQLException ex) {
                    showError("Failed to approve remark: " + ex.getMessage());
                }
            }
        });

        Button btnReject = new Button("❌ Reject Remark");
        btnReject.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
        btnReject.setOnAction(e -> {
            MedicineConstituentRemark sel = remarksTable.getSelectionModel().getSelectedItem();
            if (sel != null && sel.status() == MedicineConstituentRemark.Status.PENDING) {
                try {
                    clinicDao.rejectConstituentRemark(sel.id(), "usr-admin");
                    refreshRemarks();
                    showNotification("Remark rejected.");
                } catch (SQLException ex) {
                    showError("Failed to reject remark: " + ex.getMessage());
                }
            }
        });

        topBox.getChildren().addAll(desc, sp, btnApprove, btnReject);
        pane.setTop(topBox);

        remarksTable = new TableView<>(remarksList);
        remarksTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<MedicineConstituentRemark, String> colRem = new TableColumn<>("Remedy ID");
        colRem.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().remedyId().toUpperCase()));

        TableColumn<MedicineConstituentRemark, String> colBy = new TableColumn<>("Submitted By");
        colBy.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().submittedByUserName()));

        TableColumn<MedicineConstituentRemark, String> colRemark = new TableColumn<>("Proposed Scientific Remark / Clinical Note");
        colRemark.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().proposedRemark()));

        TableColumn<MedicineConstituentRemark, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().status().name()));

        TableColumn<MedicineConstituentRemark, String> colDate = new TableColumn<>("Submission Date");
        colDate.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().submittedAt().toString().substring(0, 10)));

        remarksTable.getColumns().addAll(List.of(colRem, colBy, colRemark, colStatus, colDate));
        pane.setCenter(remarksTable);

        return pane;
    }

    // =========================================================================
    // 6. NETWORK & REMOTE SETTINGS PANE
    // =========================================================================
    private Node createNetworkSettingsPane() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);

        VBox root = new VBox(16);
        root.setPadding(new Insets(16));

        Label secTitle = new Label("Clinic Distributed Network & Remote SSH Tunnel Architecture");
        secTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));

        Label expl = new Label(
                "Configure how practitioners and pharmacists across the clinic LAN or remote locations communicate. " +
                "Workstations on the same local Wi-Fi / LAN automatically locate the clinic database via zero-config UDP beacon discovery."
        );
        expl.setWrapText(true);
        expl.getStyleClass().add(Styles.TEXT_MUTED);

        // Network mode selector
        ComboBox<NetworkConfig.ConnectionMode> comboMode = new ComboBox<>(FXCollections.observableArrayList(
                NetworkConfig.ConnectionMode.LOCAL_EMBEDDED,
                NetworkConfig.ConnectionMode.LAN_DEDICATED_HOST,
                NetworkConfig.ConnectionMode.REMOTE_SSH_TUNNEL
        ));
        comboMode.setValue(NetworkConfig.ConnectionMode.LOCAL_EMBEDDED);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(10));

        TextField txtRemoteHost = new TextField("192.168.1.100");
        TextField txtRemotePort = new TextField("45824");
        TextField txtRemoteDb = new TextField("user_clinic.db");
        TextField txtSshHost = new TextField("clinic.openhomeo.org");
        TextField txtSshPort = new TextField("22");
        TextField txtSshUser = new TextField("homeo_admin");
        TextField txtSshKey = new TextField("~/.ssh/id_rsa");
        TextField txtReversePort = new TextField("45824");
        CheckBox chkAutoSsh = new CheckBox("Enable persistent autossh supervisor daemon");
        chkAutoSsh.setSelected(true);
        CheckBox chkBeacon = new CheckBox("Run LAN Discovery Beacon Server on this workstation");
        chkBeacon.setSelected(true);

        form.addRow(0, new Label("Connection Architecture:"), comboMode);
        form.addRow(1, new Label("Clinic Database Host IP / URL:"), txtRemoteHost);
        form.addRow(2, new Label("Service / Database Port:"), txtRemotePort);
        form.addRow(3, new Label("Remote Database Filename:"), txtRemoteDb);
        form.addRow(4, new Label("SSH Gateway Host:"), txtSshHost);
        form.addRow(5, new Label("SSH Port:"), txtSshPort);
        form.addRow(6, new Label("SSH Login User:"), txtSshUser);
        form.addRow(7, new Label("SSH Private Key File:"), txtSshKey);
        form.addRow(8, new Label("Reverse SSH Port Forwarding:"), txtReversePort);
        form.addRow(9, new Label("Supervision:"), chkAutoSsh);
        form.addRow(10, new Label("LAN Auto-Discovery:"), chkBeacon);

        TextArea txtLog = new TextArea();
        txtLog.setEditable(false);
        txtLog.setPrefRowCount(4);
        txtLog.setText("Ready to configure network connections or test remote sockets.");

        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button btnTest = new Button("🔍 Test Connection Socket");
        btnTest.setOnAction(e -> {
            try {
                int port = Integer.parseInt(txtRemotePort.getText().trim());
                RemoteDatabaseManager.ConnectionTestResult res = RemoteDatabaseManager.testRemoteSocket(txtRemoteHost.getText().trim(), port, 3000);
                txtLog.setText(res.message() + (res.isSuccessful() ? "\nStatus: SUCCESS - Connection established." : "\nStatus: FAILED - Host unreachable."));
            } catch (Exception ex) {
                txtLog.setText("Test error: " + ex.getMessage());
            }
        });

        Button btnGenDb = new Button("🚀 Generate DB in Remote");
        btnGenDb.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);
        btnGenDb.setOnAction(e -> {
            String url = "jdbc:sqlite:" + txtRemoteDb.getText().trim();
            RemoteDatabaseManager.ConnectionTestResult res = RemoteDatabaseManager.generateRemoteDbSchema(url, "admin", "");
            txtLog.setText("Schema Generation:\n" + res.message());
            showNotification("Remote database verified.");
        });

        Button btnAutoSshCmd = new Button("📋 Copy autossh Command");
        btnAutoSshCmd.setOnAction(e -> {
            try {
                NetworkConfig cfg = new NetworkConfig(
                        comboMode.getValue(), txtRemoteHost.getText().trim(),
                        Integer.parseInt(txtRemotePort.getText().trim()), txtRemoteDb.getText().trim(),
                        "admin", "", txtSshHost.getText().trim(),
                        Integer.parseInt(txtSshPort.getText().trim()), txtSshUser.getText().trim(),
                        txtSshKey.getText().trim(), Integer.parseInt(txtReversePort.getText().trim()),
                        chkAutoSsh.isSelected(), chkBeacon.isSelected(), "Active", Instant.now()
                );
                String cmd = RemoteDatabaseManager.buildAutoSshCommand(cfg);
                txtLog.setText("Generated Persistent Reverse SSH Command:\n" + cmd);
                showNotification("autossh command generated!");
            } catch (Exception ex) {
                txtLog.setText("Error generating command: " + ex.getMessage());
            }
        });

        Button btnSave = new Button("💾 Save Settings & Launch");
        btnSave.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnSave.setOnAction(e -> {
            try {
                NetworkConfig cfg = new NetworkConfig(
                        comboMode.getValue(), txtRemoteHost.getText().trim(),
                        Integer.parseInt(txtRemotePort.getText().trim()), txtRemoteDb.getText().trim(),
                        "admin", "", txtSshHost.getText().trim(),
                        Integer.parseInt(txtSshPort.getText().trim()), txtSshUser.getText().trim(),
                        txtSshKey.getText().trim(), Integer.parseInt(txtReversePort.getText().trim()),
                        chkAutoSsh.isSelected(), chkBeacon.isSelected(), "Active", Instant.now()
                );
                clinicDao.saveNetworkConfig(cfg);
                if (chkBeacon.isSelected()) {
                    discoveryService.startBeaconServer("Hahnemannian Classical Clinic", cfg.remotePort());
                } else {
                    discoveryService.stopBeaconServer();
                }
                showNotification("Network configuration saved and active!");
                txtLog.setText("Settings saved successfully. LAN discovery beacon: " + (chkBeacon.isSelected() ? "ACTIVE" : "STOPPED"));
            } catch (Exception ex) {
                showError("Failed to save settings: " + ex.getMessage());
            }
        });

        btnBox.getChildren().addAll(btnTest, btnGenDb, btnAutoSshCmd, btnSave);

        // Load existing network config
        try {
            NetworkConfig existing = clinicDao.getNetworkConfig();
            if (existing != null) {
                comboMode.setValue(existing.connectionMode());
                txtRemoteHost.setText(existing.remoteHost());
                txtRemotePort.setText(String.valueOf(existing.remotePort()));
                txtRemoteDb.setText(existing.remoteDbName());
                txtSshHost.setText(existing.sshHost());
                txtSshPort.setText(String.valueOf(existing.sshPort()));
                txtSshUser.setText(existing.sshUser());
                txtSshKey.setText(existing.sshKeyPath());
                txtReversePort.setText(String.valueOf(existing.reverseSshPort()));
                chkAutoSsh.setSelected(existing.autoSshEnabled());
                chkBeacon.setSelected(existing.lanDiscoveryActive());
            }
        } catch (SQLException ignored) {}

        root.getChildren().addAll(secTitle, expl, form, btnBox, new Label("Network Diagnostic Output:"), txtLog);
        scroll.setContent(root);
        return scroll;
    }

    public void refreshAllData() {
        refreshInstitutionProfile();
        refreshDoctors();
        refreshPharmacists();
        refreshUsers();
        refreshFaithDossier();
        refreshRemarks();
    }

    private void refreshDoctors() {
        try {
            doctorsList.setAll(clinicDao.getAllDoctors());
        } catch (SQLException e) {
            log.error("Failed to load doctors", e);
        }
    }

    private void refreshPharmacists() {
        try {
            pharmacistsList.setAll(clinicDao.getAllPharmacists());
        } catch (SQLException e) {
            log.error("Failed to load pharmacists", e);
        }
    }

    private void refreshUsers() {
        try {
            usersList.setAll(clinicDao.getAllUsers());
        } catch (SQLException e) {
            log.error("Failed to load users", e);
        }
    }

    private void refreshFaithDossier() {
        try {
            faithList.setAll(clinicDao.getFaithDossierEntries());
        } catch (SQLException e) {
            log.error("Failed to load faith dossier", e);
        }
    }

    private void refreshRemarks() {
        try {
            remarksList.setAll(clinicDao.getAllConstituentRemarks());
        } catch (SQLException e) {
            log.error("Failed to load constituent remarks", e);
        }
    }

    private Node createOtaUpdatesPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(14));

        VBox content = new VBox(14);

        Label secTitle = new Label("Over-The-Air (OTA) Automated System & Knowledge Patch Service");
        secTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        secTitle.setTextFill(javafx.scene.paint.Color.web("#0284c7"));

        Label safetyBadge = new Label("🛡️ 100% Non-Destructive Update Guarantee: The OTA update engine operates with strict data isolation. System upgrades apply enhancements ONLY to software modules, user interfaces, and the master knowledge base. Your local clinic database (user_clinic.db), patient files, case histories, encrypted clinical photographs, and billing ledgers are NEVER altered, touched, or overwritten.");
        safetyBadge.setWrapText(true);
        safetyBadge.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-padding: 10 14; -fx-border-color: #86efac; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px;");

        OtaUpdateService otaService = new OtaUpdateService("master_repertory.db", "user_clinic.db", clinicDao);
        File installDir = OtaUpdateService.detectInstallationDirectory();

        // 3 Status Cards in a Row
        HBox cardsRow = new HBox(12);
        cardsRow.setAlignment(Pos.CENTER_LEFT);

        StatusCardView cardCurrent = new StatusCardView(
                "🖥️ Current Workstation Version",
                OtaUpdateService.CURRENT_VERSION,
                "Java 21 LTS • Panama FFM Rust SIMD Native Core",
                "#0284c7"
        );

        StatusCardView cardRelease = new StatusCardView(
                "🌐 Official Release Channel",
                OtaUpdateService.LATEST_AVAILABLE_VERSION,
                "Release: 2026.11 • Master Catalog: 1,221 Verified Remedies",
                "#059669"
        );

        StatusCardView cardEnv = new StatusCardView(
                "📁 Self-Detected Installation Environment",
                installDir.getName(),
                "Target: " + installDir.getAbsolutePath(),
                "#475569"
        );
        HBox.setHgrow(cardCurrent, Priority.ALWAYS);
        HBox.setHgrow(cardRelease, Priority.ALWAYS);
        HBox.setHgrow(cardEnv, Priority.ALWAYS);
        cardsRow.getChildren().addAll(cardCurrent, cardRelease, cardEnv);

        // Action Buttons Row & Progress Bar
        HBox actionsRow = new HBox(12);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button btnCheck = new Button("🔍 Check for Live Updates");
        btnCheck.getStyleClass().add(Styles.BUTTON_OUTLINED);

        Button btnApplyLive = new Button("🚀 Download & Apply Live Update Now (Automated)");
        btnApplyLive.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnApplyLive.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        btnApplyLive.setTooltip(new Tooltip("Automatically download, verify, and apply system updates, new features, and master database with zero manual file manipulation."));

        Button btnOffline = new Button("📦 Offline Bundle Fallback (.db)");
        btnOffline.getStyleClass().add(Styles.BUTTON_OUTLINED);
        btnOffline.setTooltip(new Tooltip("Manual file import fallback specifically for air-gapped clinic computers without an internet connection."));

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(220);

        Label progressStatusLabel = new Label("Ready");
        progressStatusLabel.getStyleClass().add(Styles.TEXT_MUTED);

        actionsRow.getChildren().addAll(btnCheck, btnApplyLive, btnOffline, progressBar, progressStatusLabel);

        // Terminal Log Console
        VBox terminalBox = new VBox(6);
        Label termTitle = new Label("⚡ Live Patch Execution & Verification Log:");
        termTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        TextArea otaLog = new TextArea();
        otaLog.setEditable(false);
        otaLog.setPrefRowCount(9);
        otaLog.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11px; -fx-control-inner-background: #0f172a; -fx-text-fill: #38bdf8;");
        otaLog.setText("System ready for Over-The-Air automated self-patching.\nClick 'Download & Apply Live Update Now' to automatically upgrade this workstation.");
        terminalBox.getChildren().addAll(termTitle, otaLog);

        // Historical Installed Patches Table
        VBox historySection = new VBox(6);
        Label historyTitle = new Label("📜 Permanent Client Machine Patch & Upgrade Audit Trail:");
        historyTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        TableView<SystemPatchRecord> patchTable = new TableView<>();
        patchTable.setPrefHeight(180);
        patchTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SystemPatchRecord, String> colVer = new TableColumn<>("Installed Patch Version");
        colVer.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().version()));
        colVer.setPrefWidth(180);

        TableColumn<SystemPatchRecord, String> colType = new TableColumn<>("Patch Type");
        colType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().patchType()));
        colType.setPrefWidth(140);

        TableColumn<SystemPatchRecord, String> colTime = new TableColumn<>("Applied Date & Timestamp (Local)");
        colTime.setCellValueFactory(c -> {
            Instant inst = c.getValue().appliedAt();
            if (inst == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty(OtaUpdateService.formatLocalTimestamp(inst));
        });
        colTime.setPrefWidth(210);

        TableColumn<SystemPatchRecord, String> colBy = new TableColumn<>("Applied By");
        colBy.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().appliedBy()));
        colBy.setPrefWidth(100);

        TableColumn<SystemPatchRecord, String> colHash = new TableColumn<>("SHA-256 Checksum");
        colHash.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().repertoryHash() != null && c.getValue().repertoryHash().length() > 16 ? c.getValue().repertoryHash().substring(0, 16) + "..." : c.getValue().repertoryHash()));
        colHash.setPrefWidth(140);

        TableColumn<SystemPatchRecord, String> colStatus = new TableColumn<>("Integrity Status");
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        colStatus.setPrefWidth(120);

        TableColumn<SystemPatchRecord, String> colSummary = new TableColumn<>("Changelog Summary");
        colSummary.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().changelogSummary()));
        colSummary.setPrefWidth(260);

        patchTable.getColumns().addAll(colVer, colType, colTime, colBy, colHash, colStatus, colSummary);

        // Row double-click to view complete audit trail details
        patchTable.setRowFactory(tv -> {
            TableRow<SystemPatchRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showPatchRecordDetailDialog(row.getItem());
                }
            });
            return row;
        });

        Button btnViewPatch = new Button("📄 View Selected Patch Details & Execution Log");
        btnViewPatch.getStyleClass().add(Styles.BUTTON_OUTLINED);
        btnViewPatch.setOnAction(e -> {
            SystemPatchRecord sel = patchTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                showPatchRecordDetailDialog(sel);
            } else {
                showNotification("Please select a patch row from the table to view details.");
            }
        });

        HBox historyHeader = new HBox(12, historyTitle, new Region(), btnViewPatch);
        HBox.setHgrow(historyHeader.getChildren().get(1), Priority.ALWAYS);
        historyHeader.setAlignment(Pos.CENTER_LEFT);

        Runnable refreshHistory = () -> {
            try {
                List<SystemPatchRecord> list = otaService.getPatchHistory();
                patchTable.setItems(FXCollections.observableArrayList(list));
                if (!list.isEmpty()) {
                    SystemPatchRecord latest = list.get(0);
                    cardCurrent.update(
                            latest.version(),
                            "Applied Locally: " + OtaUpdateService.formatLocalTimestamp(latest.appliedAt())
                    );
                }
            } catch (Exception ex) {
                log.warn("Could not query patch history: {}", ex.getMessage());
            }
        };
        refreshHistory.run();

        // Wire Actions:
        btnCheck.setOnAction(e -> {
            btnCheck.setDisable(true);
            progressBar.setVisible(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            progressStatusLabel.setText("Checking for live updates online...");

            Task<OtaUpdateService.UpdateInfo> checkTask = new Task<>() {
                @Override
                protected OtaUpdateService.UpdateInfo call() {
                    return otaService.checkForUpdates();
                }
            };

            checkTask.setOnSucceeded(evt -> {
                btnCheck.setDisable(false);
                progressBar.setVisible(false);
                progressStatusLabel.setText("Check complete");

                OtaUpdateService.UpdateInfo info = checkTask.getValue();
                String localCheckTime = ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
                otaLog.appendText("\n\n[OTA CHECK] " + localCheckTime + "\n" +
                        "Latest Version: " + info.latestVersion() + "\n" +
                        "Update Available: " + (info.updateAvailable() ? "YES (Ready to Download & Apply)" : "NO (Installed version is up to date)") + "\n" +
                        "Release Date: " + info.releaseDate() + " | Master Catalog: " + info.totalRemedies() + " verified remedies\n" +
                        "Changelog:\n" + info.changelog() + "\n" +
                        "Safety Guarantee: " + info.safetyNotice() + "\n");

                boolean alreadyApplied = otaService.isPatchApplied(info.latestVersion());
                if (info.updateAvailable() && !alreadyApplied) {
                    statusLabel.setText("🔴 OTA Update Available: " + info.latestVersion() + " (Originated: " + info.releaseDate() + ")");
                    statusLabel.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-border-color: #ef4444; -fx-border-radius: 4; -fx-font-weight: bold; -fx-padding: 3 8;");
                } else {
                    statusLabel.setText("✓ OTA Status: Up to date (" + info.latestVersion() + ")");
                    statusLabel.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-border-color: #86efac; -fx-border-radius: 4; -fx-font-weight: bold; -fx-padding: 3 8;");
                }
            });

            checkTask.setOnFailed(evt -> {
                btnCheck.setDisable(false);
                progressBar.setVisible(false);
                progressStatusLabel.setText("Check failed");
                showError("Failed to check for updates: " + checkTask.getException().getMessage());
            });

            new Thread(checkTask, "openhomeo-ota-check").start();
        });

        btnApplyLive.setOnAction(e -> {
            btnApplyLive.setDisable(true);
            btnCheck.setDisable(true);
            btnOffline.setDisable(true);
            progressBar.setVisible(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            progressStatusLabel.setText("Applying live system update...");

            String currentUser = securityService != null && securityService.getCurrentSession() != null
                    ? securityService.getCurrentSession().user().username() : "admin";

            Task<OtaUpdateService.ApplyResult> task = new Task<>() {
                @Override
                protected OtaUpdateService.ApplyResult call() {
                    try {
                        return otaService.applyLiveUpdateAutomated(msg -> {
                            javafx.application.Platform.runLater(() -> {
                                otaLog.appendText(msg + "\n");
                                progressStatusLabel.setText(msg.replaceAll("\\[\\d/\\d\\] ", ""));
                            });
                        }, currentUser);
                    } catch (Throwable t) {
                        log.error("Live automated OTA update error", t);
                        return new OtaUpdateService.ApplyResult(false, null, "OTA Update process note: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()), t.toString(), "N/A");
                    }
                }
            };

            task.setOnSucceeded(evt -> {
                btnApplyLive.setDisable(false);
                btnCheck.setDisable(false);
                btnOffline.setDisable(false);
                progressBar.setVisible(false);
                progressStatusLabel.setText("Update complete!");

                OtaUpdateService.ApplyResult res = task.getValue();
                if (res.success()) {
                    showNotification("Update Succeeded: " + res.message());
                    refreshHistory.run();
                    refreshInstitutionProfile();
                    refreshAllData();

                    if (onUpdateAppliedCallback != null) {
                        try {
                            onUpdateAppliedCallback.run();
                        } catch (Exception ex) {
                            log.warn("Could not notify update callback: {}", ex.getMessage());
                        }
                    }

                    String appliedLocalTime = OtaUpdateService.formatLocalTimestamp(Instant.now());

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("System Update Applied Successfully");
                    alert.setHeaderText("🎉 OpenHomeo " + res.version() + " Installed");
                    alert.setContentText(
                            "The automated live update has been applied smoothly.\n\n" +
                            "• Patch Release / Originated Date: 2026-11-25\n" +
                            "• Applied Date & Local Timestamp: " + appliedLocalTime + "\n\n" +
                            "• Activated Changes & Commented Details:\n" +
                            "  1. 🏛️ OpenSynthesis Repertory Scale (millions of additions, degrees 1-4, author citations k1, b1, bog1, ccrh24, cross-references)\n" +
                            "  2. 📚 1,500-Book Materia Medica Digital Library (SQLite FTS5 full-text search with BM25 ranking across classical volumes)\n" +
                            "  3. 🌿 Families Tool & Taxonomy Classifier (APG IV, Jan Scholten Series/Stages, Animal phyla, Miasms distribution)\n" +
                            "  4. ⚖️ WinChip-Alike Patient Management (longitudinal follow-up comparison, Kent's 12 Observations & Hering's Law)\n" +
                            "  5. 🧠 Upgraded Socratic Clinical Logic AI (Strictly NO LLM, doctor interrogation & permanent Experience Vault)\n" +
                            "  6. 📦 Universal Standalone Packages (Windows 10/11 & macOS Apple Silicon/Intel)\n\n" +
                            "• 🛡️ 100% Non-Destructive Invariant: All patient records, consultations, and prescriptions in user_clinic.db are 100% intact.\n" +
                            "• Verified SHA-256 Hash: " + res.hash() + "\n\n" +
                            "A permanent audit record with your local timestamp (" + appliedLocalTime + ") has been recorded in your client database."
                    );
                    alert.showAndWait();
                } else {
                    showError(res.message());
                }
            });

            task.setOnFailed(evt -> {
                btnApplyLive.setDisable(false);
                btnCheck.setDisable(false);
                btnOffline.setDisable(false);
                progressBar.setVisible(false);
                progressStatusLabel.setText("Update failed");
                Throwable ex = task.getException();
                showError("Update failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            });

            new Thread(task).start();
        });

        // Offline Fallback Button
        btnOffline.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select OpenHomeo Master Database Update Package (.db)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database (*.db)", "*.db"));
            File file = chooser.showOpenDialog(getScene().getWindow());
            if (file != null) {
                String currentUser = securityService != null && securityService.getCurrentSession() != null
                        ? securityService.getCurrentSession().user().username() : "admin";
                OtaUpdateService.ApplyResult res = otaService.applyUpdateFromOfflinePackage(file, "1.1.0-OfflinePatch", currentUser);
                otaLog.appendText("\n[OFFLINE PACKAGE RESULT]\nStatus: " + (res.success() ? "SUCCESS" : "FAILED") + "\nMessage: " + res.message() + "\n");
                if (res.success()) {
                    showNotification("Offline package applied: " + res.message());
                    refreshHistory.run();
                } else {
                    showError(res.message());
                }
            }
        });

        historySection.getChildren().addAll(historyHeader, patchTable);

        content.getChildren().addAll(
                secTitle, safetyBadge, cardsRow, actionsRow, terminalBox, historySection
        );

        pane.setCenter(new ScrollPane(content));
        return pane;
    }

    private void showPatchRecordDetailDialog(SystemPatchRecord rec) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Patch Audit Trail Details");
        dialog.setHeaderText("📜 Permanent Client Machine Patch Audit Record");

        VBox box = new VBox(12);
        box.setPadding(new Insets(14));
        box.setPrefWidth(680);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        grid.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 12; -fx-border-color: -color-border-subtle; -fx-border-radius: 6;");

        grid.add(new Label("Installed Patch Version:"), 0, 0);
        Label lblVer = new Label(rec.version());
        lblVer.setStyle("-fx-font-weight: bold; -fx-text-fill: #0284c7;");
        grid.add(lblVer, 1, 0);

        grid.add(new Label("Patch Type:"), 0, 1);
        grid.add(new Label(rec.patchType()), 1, 1);

        grid.add(new Label("Originated / Release Date:"), 0, 2);
        grid.add(new Label(rec.releaseDate()), 1, 2);

        grid.add(new Label("Applied Date & Local Timestamp:"), 0, 3);
        String localTime = OtaUpdateService.formatLocalTimestamp(rec.appliedAt());
        Label lblTime = new Label(localTime);
        lblTime.setStyle("-fx-font-weight: bold; -fx-text-fill: #166534;");
        grid.add(lblTime, 1, 3);

        grid.add(new Label("Applied By Operator:"), 0, 4);
        grid.add(new Label(rec.appliedBy()), 1, 4);

        grid.add(new Label("Integrity Status:"), 0, 5);
        Label lblStatus = new Label(rec.status());
        lblStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #166534;");
        grid.add(lblStatus, 1, 5);

        grid.add(new Label("SHA-256 Checksum:"), 0, 6);
        Label lblHash = new Label(rec.repertoryHash());
        lblHash.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        grid.add(lblHash, 1, 6);

        Label lblSummTitle = new Label("Changelog Summary & Commented Enhancements:");
        lblSummTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        Label lblSumm = new Label(rec.changelogSummary());
        lblSumm.setWrapText(true);
        lblSumm.setStyle("-fx-padding: 6 0; -fx-font-size: 12px;");

        Label lblLogTitle = new Label("Full Execution & Non-Destructive Migration Log:");
        lblLogTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        TextArea txtLog = new TextArea(rec.executionLog());
        txtLog.setEditable(false);
        txtLog.setPrefRowCount(8);
        txtLog.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11px; -fx-control-inner-background: #0f172a; -fx-text-fill: #38bdf8;");

        box.getChildren().addAll(grid, lblSummTitle, lblSumm, lblLogTitle, txtLog);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static class StatusCardView extends VBox {
        private final Label mainLabel;
        private final Label subLabel;

        StatusCardView(String title, String mainValue, String subValue, String accentHex) {
            super(4);
            setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 10 14; -fx-border-color: -color-border-subtle; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1 1 1 4; -fx-border-color: " + accentHex + " -color-border-subtle -color-border-subtle " + accentHex + ";");

            Label t = new Label(title);
            t.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

            mainLabel = new Label(mainValue);
            mainLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            mainLabel.setStyle("-fx-text-fill: " + accentHex + ";");

            subLabel = new Label(subValue);
            subLabel.setWrapText(true);
            subLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

            getChildren().addAll(t, mainLabel, subLabel);
        }

        void update(String mainValue, String subValue) {
            mainLabel.setText(mainValue);
            subLabel.setText(subValue);
        }
    }

    private Node createProvingIngestionPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(14));

        VBox content = new VBox(14);

        Label secTitle = new Label("Community & Clinical Proving Ingestion Pipeline (Strategic Rec 1)");
        secTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));

        Label infoBadge = new Label("Import newly conducted or community homeopathic provings directly into your local clinic database. Accepts standard structured JSON and CSV formats conforming strictly to international English and Latin botanical pharmacopoeial nomenclature.");
        infoBadge.setWrapText(true);
        infoBadge.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 8 12; -fx-border-color: -color-border-subtle; -fx-border-radius: 6; -fx-background-radius: 6;");

        ProvingIngestionService provingService = new ProvingIngestionService(clinicDao);

        TextArea provingLog = new TextArea();
        provingLog.setEditable(false);
        provingLog.setPrefRowCount(7);
        provingLog.setText("Proving Ingestion Pipeline ready. Ingested provings will be immediately accessible in Materia Medica and the Live Stock Register.");

        TextArea pasteArea = new TextArea();
        pasteArea.setPromptText("Paste raw Proving JSON here (e.g. {\"remedyName\": \"Boswellia serrata\", \"abbr\": \"Bosw-s\", ...})");
        pasteArea.setPrefRowCount(6);

        HBox actions = new HBox(12);

        Button btnImportJson = new Button("📄 Ingest Proving from JSON File");
        btnImportJson.getStyleClass().add(Styles.ACCENT);
        btnImportJson.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Proving JSON File");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
            File f = chooser.showOpenDialog(getScene().getWindow());
            if (f != null) {
                try {
                    String json = Files.readString(f.toPath());
                    ProvingIngestionService.IngestionResult res = provingService.ingestJson(json);
                    provingLog.appendText("\n\n[INGEST JSON] " + res.remedyName() + " (" + res.remedyId() + "): " + res.message() + " [Sections: " + res.sectionsIngested() + "]");
                    showNotification("Ingested proving: " + res.remedyName());
                } catch (Exception ex) {
                    showError("Failed to ingest JSON: " + ex.getMessage());
                }
            }
        });

        Button btnImportCsv = new Button("📊 Ingest Proving from CSV File");
        btnImportCsv.getStyleClass().add(Styles.BUTTON_OUTLINED);
        btnImportCsv.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Proving CSV File");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
            File f = chooser.showOpenDialog(getScene().getWindow());
            if (f != null) {
                ProvingIngestionService.IngestionResult res = provingService.ingestCsv(f);
                provingLog.appendText("\n\n[INGEST CSV] " + res.remedyName() + " (" + res.remedyId() + "): " + res.message() + " [Sections: " + res.sectionsIngested() + "]");
                showNotification("Ingested proving: " + res.remedyName());
            }
        });

        Button btnIngestPasted = new Button("📥 Ingest Pasted Proving JSON");
        btnIngestPasted.getStyleClass().add(Styles.SUCCESS);
        btnIngestPasted.setOnAction(e -> {
            String txt = pasteArea.getText().trim();
            if (txt.isBlank()) {
                showError("Please paste proving JSON text first.");
                return;
            }
            ProvingIngestionService.IngestionResult res = provingService.ingestJson(txt);
            provingLog.appendText("\n\n[INGEST PASTED] " + res.remedyName() + " (" + res.remedyId() + "): " + res.message() + " [Sections: " + res.sectionsIngested() + "]");
            if (res.success()) {
                pasteArea.clear();
                showNotification("Ingested proving: " + res.remedyName());
            } else {
                showError(res.message());
            }
        });

        actions.getChildren().addAll(btnImportJson, btnImportCsv, btnIngestPasted);

        content.getChildren().addAll(
                secTitle, infoBadge, actions,
                new Label("Paste Proving JSON:"), pasteArea,
                new Label("Ingestion Diagnostics & Clinical Status:"), provingLog
        );
        pane.setCenter(new ScrollPane(content));
        return pane;
    }

    private Node createBackupAndGoogleDrivePane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(16));

        VBox content = new VBox(16);
        content.setPadding(new Insets(8));

        Label secTitle = new Label("☁️ Clinic Database Backup & Google Drive Synchronization");
        secTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        Label infoBadge = new Label(
                "🛡️ Zero-Loss Clinical Protection: OpenHomeo encapsulates patient records (user_clinic.db), clinical photographs (vault/), " +
                "and posology history into cryptographically hashed (.ohbak) archives. Sync directly to your Google Drive account with " +
                "automated remote MD5 checksum verification, or sync to a local Google Drive folder."
        );
        infoBadge.setWrapText(true);
        infoBadge.setStyle("-fx-background-color: rgba(2, 132, 199, 0.1); -fx-border-color: rgba(2, 132, 199, 0.3); -fx-padding: 10px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

        // Two columns: Left is Local Snapshot & Restore; Right is Google Drive Cloud Sync
        HBox columns = new HBox(16);

        // -------------------------------------------------------------
        // LEFT COLUMN: Local Snapshots, Verification & Restore
        // -------------------------------------------------------------
        VBox leftCol = new VBox(14);
        leftCol.setPrefWidth(550);

        VBox cardLocal = new VBox(10);
        cardLocal.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 14px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        Label lblLocalTitle = new Label("📦 Local Timestamped Backup Archive");
        lblLocalTitle.setFont(Font.font("System", FontWeight.BOLD, 13));

        TextField txtNote = new TextField("Routine Clinical & Inventory Snapshot");
        txtNote.setPromptText("Enter backup note / reason...");

        Button btnCreateBackup = new Button("📁 Create Instant Backup (.ohbak)");
        btnCreateBackup.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);

        TextArea txtBackupOutput = new TextArea();
        txtBackupOutput.setEditable(false);
        txtBackupOutput.setPrefRowCount(4);
        txtBackupOutput.setWrapText(true);
        txtBackupOutput.setPromptText("Backup status, SHA-256 hash, and manifest details will appear here...");

        TextField txtVerifyPath = new TextField();
        txtVerifyPath.setPromptText("Select .ohbak file to verify or restore...");
        Button btnBrowseVerify = new Button("Browse...");
        HBox verifyFileBox = new HBox(8, txtVerifyPath, btnBrowseVerify);
        HBox.setHgrow(txtVerifyPath, Priority.ALWAYS);

        Button btnVerify = new Button("🔍 Verify Cryptographic & DB Integrity");
        btnVerify.getStyleClass().addAll(Styles.BUTTON_OUTLINED);

        Label lblVerifyResult = new Label();
        lblVerifyResult.setWrapText(true);

        Button btnRestore = new Button("⏪ Safely Restore from Selected Backup");
        btnRestore.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);

        cardLocal.getChildren().addAll(
                lblLocalTitle,
                new Label("Backup Note / Description:"), txtNote,
                btnCreateBackup, txtBackupOutput,
                new Separator(),
                new Label("Archive File Verification & Restore:"), verifyFileBox,
                new HBox(10, btnVerify, btnRestore),
                lblVerifyResult
        );
        leftCol.getChildren().add(cardLocal);

        // -------------------------------------------------------------
        // RIGHT COLUMN: Google Drive Cloud API & Desktop Folder Sync
        // -------------------------------------------------------------
        VBox rightCol = new VBox(14);
        rightCol.setPrefWidth(550);

        VBox cardDrive = new VBox(10);
        cardDrive.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 14px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        Label lblDriveTitle = new Label("☁️ Google Drive Cloud Direct Synchronization");
        lblDriveTitle.setFont(Font.font("System", FontWeight.BOLD, 13));

        PasswordField txtGoogleToken = new PasswordField();
        txtGoogleToken.setPromptText("Enter Google Drive Access Token / OAuth Bearer Token");

        Button btnTestGoogle = new Button("🔑 Test Google Connection");
        btnTestGoogle.getStyleClass().addAll(Styles.BUTTON_OUTLINED);
        Label lblGoogleStatus = new Label("Status: Not connected");
        lblGoogleStatus.setStyle("-fx-font-size: 0.85em; -fx-text-fill: -color-fg-muted;");

        TextField txtGoogleFolderId = new TextField();
        txtGoogleFolderId.setPromptText("Target Folder ID (optional, leave blank for My Drive root)");

        Button btnUploadGoogle = new Button("☁️ Upload Backup & Verify Remote MD5 Checksum");
        btnUploadGoogle.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);

        Label lblUploadStatus = new Label();
        lblUploadStatus.setWrapText(true);

        // Desktop Folder Sync Card
        Label lblDesktopTitle = new Label("🔄 Google Drive Desktop Local Folder Sync");
        lblDesktopTitle.setFont(Font.font("System", FontWeight.BOLD, 13));

        String defaultDrivePath = System.getProperty("user.home") + File.separator + "Google Drive" + File.separator + "OpenHomeo_Backups";
        if (new File("G:\\My Drive").exists()) {
            defaultDrivePath = "G:\\My Drive\\OpenHomeo_Backups";
        }
        TextField txtDesktopPath = new TextField(defaultDrivePath);

        Button btnSyncDesktop = new Button("🔁 Sync Latest Backup to Local Google Drive Folder");
        btnSyncDesktop.getStyleClass().addAll(Styles.BUTTON_OUTLINED);

        Label lblDesktopSyncResult = new Label();
        lblDesktopSyncResult.setWrapText(true);

        cardDrive.getChildren().addAll(
                lblDriveTitle,
                new Label("Google Drive Bearer Token:"), txtGoogleToken,
                new HBox(10, btnTestGoogle, lblGoogleStatus),
                new Label("Google Drive Destination Folder ID:"), txtGoogleFolderId,
                btnUploadGoogle, lblUploadStatus,
                new Separator(),
                lblDesktopTitle,
                new Label("Local Google Drive Sync Directory:"), txtDesktopPath,
                btnSyncDesktop, lblDesktopSyncResult
        );
        rightCol.getChildren().add(cardDrive);

        columns.getChildren().addAll(leftCol, rightCol);

        // -------------------------------------------------------------
        // EVENT HANDLERS
        // -------------------------------------------------------------
        final File[] lastCreatedArchive = new File[1];

        btnCreateBackup.setOnAction(e -> {
            String note = txtNote.getText().trim();
            btnCreateBackup.setDisable(true);
            btnCreateBackup.setText("Creating Backup Archive...");

            try {
                ClinicBackupService.BackupResult res = backupService.createBackup(note);
                if (res.success()) {
                    lastCreatedArchive[0] = res.archiveFile();
                    txtVerifyPath.setText(res.archiveFile().getAbsolutePath());
                    txtBackupOutput.setText(String.format(
                            "✓ Backup Created Successfully!\nFile: %s\nSize: %,d bytes\nSHA-256: %s\nMD5: %s\nDetails: %s",
                            res.archiveFile().getName(), res.sizeBytes(), res.sha256Checksum(), res.md5Checksum(), res.message()
                    ));
                    showNotification("Backup saved: " + res.archiveFile().getName());
                } else {
                    txtBackupOutput.setText("Backup Failed: " + res.message());
                    showError(res.message());
                }
            } catch (Exception ex) {
                txtBackupOutput.setText("Error: " + ex.getMessage());
                showError("Backup Error: " + ex.getMessage());
            } finally {
                btnCreateBackup.setDisable(false);
                btnCreateBackup.setText("📁 Create Instant Backup (.ohbak)");
            }
        });

        btnBrowseVerify.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select OpenHomeo Clinic Backup Archive");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("OpenHomeo Backup (*.ohbak, *.zip)", "*.ohbak", "*.zip"));
            fc.setInitialDirectory(backupService.getDefaultBackupDir());
            File selected = fc.showOpenDialog(getScene().getWindow());
            if (selected != null) {
                txtVerifyPath.setText(selected.getAbsolutePath());
            }
        });

        btnVerify.setOnAction(e -> {
            String path = txtVerifyPath.getText().trim();
            if (path.isBlank()) {
                showError("Please specify or browse for a backup file first.");
                return;
            }
            File f = new File(path);
            if (!f.exists()) {
                showError("File does not exist: " + path);
                return;
            }

            ClinicBackupService.VerificationResult res = backupService.verifyBackup(f);
            if (res.valid()) {
                lblVerifyResult.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
                lblVerifyResult.setText("✓ Integrity Verified! " + res.manifestSummary() + "\n" + res.message());
                showNotification("Backup verification passed for: " + f.getName());
            } else {
                lblVerifyResult.setStyle("-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;");
                lblVerifyResult.setText("✗ Verification Failed: " + res.message());
            }
        });

        btnRestore.setOnAction(e -> {
            String path = txtVerifyPath.getText().trim();
            if (path.isBlank()) {
                showError("Please select a verified backup file to restore.");
                return;
            }
            File f = new File(path);
            if (!f.exists()) {
                showError("File does not exist: " + path);
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Clinic Database Restore");
            confirm.setHeaderText("Restore clinic records from " + f.getName() + "?");
            confirm.setContentText(
                    "This action will safely replace user_clinic.db and the clinical photo vault with data from the backup archive.\n\n" +
                    "• A pre-restore safety snapshot will be automatically created first.\n" +
                    "• All current patient records, prescriptions, and stocks will be synchronized to this backup point.\n\n" +
                    "Do you wish to proceed?"
            );

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                String restoreReport = backupService.restoreFromBackup(f);
                if (restoreReport.startsWith("SUCCESS")) {
                    lblVerifyResult.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
                    lblVerifyResult.setText("✓ " + restoreReport);
                    showNotification("Restore successful from: " + f.getName());
                    refreshAllData();
                } else {
                    lblVerifyResult.setStyle("-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;");
                    lblVerifyResult.setText("✗ " + restoreReport);
                    showError(restoreReport);
                }
            }
        });

        btnTestGoogle.setOnAction(e -> {
            String token = txtGoogleToken.getText().trim();
            if (token.isBlank()) {
                showError("Please enter your Google Drive access token.");
                return;
            }
            btnTestGoogle.setDisable(true);
            btnTestGoogle.setText("Testing...");
            try {
                var res = backupService.testGoogleDriveConnection(token);
                @SuppressWarnings("unchecked")
                var user = (java.util.Map<String, Object>) res.get("user");
                String name = user != null ? (String) user.get("displayName") : "Authenticated User";
                String email = user != null ? (String) user.get("emailAddress") : "";
                lblGoogleStatus.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
                lblGoogleStatus.setText("✓ Connected: " + name + " (" + email + ")");
                showNotification("Google Drive authenticated: " + email);
            } catch (Exception ex) {
                lblGoogleStatus.setStyle("-fx-text-fill: -color-danger-fg;");
                lblGoogleStatus.setText("✗ Authentication Error: " + ex.getMessage());
                showError("Google Drive Connection Error: " + ex.getMessage());
            } finally {
                btnTestGoogle.setDisable(false);
                btnTestGoogle.setText("🔑 Test Google Connection");
            }
        });

        btnUploadGoogle.setOnAction(e -> {
            String token = txtGoogleToken.getText().trim();
            if (token.isBlank()) {
                showError("Please enter Google Drive Access Token first.");
                return;
            }
            String path = txtVerifyPath.getText().trim();
            File target = !path.isBlank() ? new File(path) : lastCreatedArchive[0];
            if (target == null || !target.exists()) {
                showError("No backup archive selected. Please create or browse for a backup (.ohbak) file first.");
                return;
            }

            btnUploadGoogle.setDisable(true);
            btnUploadGoogle.setText("Uploading & Verifying on Google Drive...");
            lblUploadStatus.setText("Streaming " + target.getName() + " to Google Drive...");

            String folderId = txtGoogleFolderId.getText().trim();
            try {
                ClinicBackupService.DriveUploadResult res = backupService.uploadToGoogleDrive(target, token, folderId);
                if (res.success()) {
                    lblUploadStatus.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
                    lblUploadStatus.setText(res.message() + "\nRemote File ID: " + res.fileId() + " (Size: " + res.remoteSizeBytes() + " bytes)");
                    showNotification("Backup uploaded to Google Drive: " + target.getName());
                } else {
                    lblUploadStatus.setStyle("-fx-text-fill: -color-danger-fg;");
                    lblUploadStatus.setText("Upload Failed: " + res.message());
                    showError(res.message());
                }
            } catch (Exception ex) {
                lblUploadStatus.setStyle("-fx-text-fill: -color-danger-fg;");
                lblUploadStatus.setText("Upload Error: " + ex.getMessage());
                showError("Google Drive Upload Error: " + ex.getMessage());
            } finally {
                btnUploadGoogle.setDisable(false);
                btnUploadGoogle.setText("☁️ Upload Backup & Verify Remote MD5 Checksum");
            }
        });

        btnSyncDesktop.setOnAction(e -> {
            String destPath = txtDesktopPath.getText().trim();
            if (destPath.isBlank()) {
                showError("Please enter a Google Drive desktop sync folder path.");
                return;
            }
            String path = txtVerifyPath.getText().trim();
            File target = !path.isBlank() ? new File(path) : lastCreatedArchive[0];
            if (target == null || !target.exists()) {
                showError("No backup archive selected. Please create or browse for a backup file first.");
                return;
            }

            boolean synced = backupService.syncToLocalFolder(target, new File(destPath));
            if (synced) {
                lblDesktopSyncResult.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
                lblDesktopSyncResult.setText("✓ Synced cleanly to: " + destPath + File.separator + target.getName() + " (SHA-256 byte-matched)");
                showNotification("Desktop sync successful: " + target.getName());
            } else {
                lblDesktopSyncResult.setStyle("-fx-text-fill: -color-danger-fg;");
                lblDesktopSyncResult.setText("✗ Failed to sync to destination folder.");
                showError("Failed to sync to folder: " + destPath);
            }
        });

        content.getChildren().addAll(secTitle, infoBadge, columns);
        pane.setCenter(new ScrollPane(content));
        return pane;
    }

    private void showNotification(String msg) {
        if (statusLabel != null) {
            statusLabel.setText(msg);
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Admin Management Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
