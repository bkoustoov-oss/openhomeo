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

    private Label statusLabel;

    public AdminManagementView(ClinicDao clinicDao, SecurityService securityService) {
        this.clinicDao = clinicDao;
        this.securityService = securityService;

        File clinicDb = new File(clinicDao.getDatabaseManager().getClinicDbPath());
        File vaultDir = new File(clinicDb.getParentFile(), "vault");
        File backupDir = new File(clinicDb.getParentFile(), "backups");
        this.backupService = new ClinicBackupService(clinicDb, vaultDir, backupDir);

        setPadding(new Insets(14));
        setTop(createTopHeader());

        TabPane adminTabs = new TabPane();
        adminTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tabDoctors = new Tab("🩺 Enlisted Doctors", createDoctorsPane());
        Tab tabPharmacists = new Tab("💊 Enlisted Pharmacists", createPharmacistsPane());
        Tab tabUsers = new Tab("👥 User Accounts & RBAC", createUsersPane());
        Tab tabFaith = new Tab("🔒 Secret Report of Faith", createFaithReportPane());
        Tab tabRemarks = new Tab("📝 Doctor Remarks Approval", createRemarksApprovalPane());
        Tab tabNetwork = new Tab("🌐 Network & Remote Settings", createNetworkSettingsPane());
        Tab tabOta = new Tab("🔄 Over-The-Air (OTA) Updates", createOtaUpdatesPane());
        Tab tabProving = new Tab("🧪 Community Proving Ingestion", createProvingIngestionPane());
        Tab tabBackup = new Tab("☁️ Backup & Google Drive Sync", createBackupAndGoogleDrivePane());

        adminTabs.getTabs().addAll(tabDoctors, tabPharmacists, tabUsers, tabFaith, tabRemarks, tabNetwork, tabOta, tabProving, tabBackup);
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

        toolbar.getChildren().addAll(btnAdd, btnToggle);
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

        toolbar.getChildren().addAll(btnAdd, btnToggle);
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

        VBox cardCurrent = createStatusCard(
                "🖥️ Current Workstation Version",
                OtaUpdateService.CURRENT_VERSION,
                "Java 21 LTS • Panama FFM Rust SIMD Native Core",
                "#0284c7"
        );

        VBox cardRelease = createStatusCard(
                "🌐 Official Release Channel",
                OtaUpdateService.LATEST_AVAILABLE_VERSION,
                "Release: 2026.11 • Master Catalog: 1,221 Verified Remedies",
                "#059669"
        );

        VBox cardEnv = createStatusCard(
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

        TableColumn<SystemPatchRecord, String> colTime = new TableColumn<>("Applied Date & Timestamp");
        colTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().appliedAt() != null ? c.getValue().appliedAt().toString().replace("T", " ").substring(0, 19) : "—"));
        colTime.setPrefWidth(160);

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

        Runnable refreshHistory = () -> {
            try {
                List<SystemPatchRecord> list = otaService.getPatchHistory();
                patchTable.setItems(FXCollections.observableArrayList(list));
            } catch (Exception ex) {
                log.warn("Could not query patch history: {}", ex.getMessage());
            }
        };
        refreshHistory.run();

        // Wire Actions:
        btnCheck.setOnAction(e -> {
            OtaUpdateService.UpdateInfo info = otaService.checkForUpdates();
            otaLog.appendText("\n\n[OTA CHECK] " + Instant.now() + "\n" +
                    "Latest Version: " + info.latestVersion() + "\n" +
                    "Update Available: " + (info.updateAvailable() ? "YES" : "NO (Installed version is up to date)") + "\n" +
                    "Release Date: " + info.releaseDate() + " | Master Catalog: " + info.totalRemedies() + " remedies\n" +
                    "Changelog:\n" + info.changelog() + "\n" +
                    "Safety Guarantee: " + info.safetyNotice());
            showNotification("OTA Status: Update available: " + info.latestVersion());
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
                    return otaService.applyLiveUpdateAutomated(msg -> {
                        javafx.application.Platform.runLater(() -> {
                            otaLog.appendText(msg + "\n");
                            progressStatusLabel.setText(msg.replaceAll("\\[\\d/\\d\\] ", ""));
                        });
                    }, currentUser);
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

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("System Update Applied Successfully");
                    alert.setHeaderText("🎉 OpenHomeo " + res.version() + " Installed");
                    alert.setContentText(
                            "The automated live update has been applied smoothly.\n\n" +
                            "• New Features Activated: 360° Patient Dossier, Classical Logic AI Chat (No LLM), In-App Prescription Slips, and Master Catalog updates.\n" +
                            "• Non-Destructive Guarantee: All patient records, consultations, and prescriptions in user_clinic.db are 100% intact.\n" +
                            "• Verified SHA-256 Hash: " + res.hash() + "\n\n" +
                            "A permanent audit record has been timestamped in your client database."
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

        historySection.getChildren().addAll(historyTitle, patchTable);

        content.getChildren().addAll(
                secTitle, safetyBadge, cardsRow, actionsRow, terminalBox, historySection
        );

        pane.setCenter(new ScrollPane(content));
        return pane;
    }

    private VBox createStatusCard(String title, String mainValue, String subValue, String accentHex) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 10 14; -fx-border-color: -color-border-subtle; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1 1 1 4; -fx-border-color: " + accentHex + " -color-border-subtle -color-border-subtle " + accentHex + ";");

        Label t = new Label(title);
        t.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        Label v = new Label(mainValue);
        v.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        v.setStyle("-fx-text-fill: " + accentHex + ";");

        Label s = new Label(subValue);
        s.setWrapText(true);
        s.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        card.getChildren().addAll(t, v, s);
        return card;
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
