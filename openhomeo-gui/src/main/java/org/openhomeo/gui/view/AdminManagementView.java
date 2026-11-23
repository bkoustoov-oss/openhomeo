package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.model.*;
import org.openhomeo.core.net.NetworkDiscoveryService;
import org.openhomeo.core.net.RemoteDatabaseManager;
import org.openhomeo.core.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        adminTabs.getTabs().addAll(tabDoctors, tabPharmacists, tabUsers, tabFaith, tabRemarks, tabNetwork);
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
        txtName.setPromptText("Dr. S. K. Mukherjee");
        TextField txtReg = new TextField();
        txtReg.setPromptText("CCH-2018 / WBH-14285");
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
        txtName.setPromptText("A. K. Sharma");
        TextField txtReg = new TextField();
        txtReg.setPromptText("WBCP-8921 / PH-2016");
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
