package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.DatabaseManager;
import org.openhomeo.core.image.ImageVaultService;
import org.openhomeo.core.model.User;
import org.openhomeo.core.net.DeploymentProfile;
import org.openhomeo.core.net.NetworkDiscoveryService;
import org.openhomeo.core.net.RemoteDatabaseManager;
import org.openhomeo.core.security.SecurityService;
import org.openhomeo.core.server.MobileCompanionServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Modern In-Window Login View utilizing AtlantaFX styling.
 * Renders directly inside the primary application stage with integrated Client/Server deployment switcher.
 */
public class LoginView extends StackPane {
    private static final Logger log = LoggerFactory.getLogger(LoginView.class);

    public LoginView(ClinicDao clinicDao, SecurityService securityService, Consumer<User> onLoginSuccess) {
        this(clinicDao, securityService, clinicDao != null ? clinicDao.getDatabaseManager() : null, null, onLoginSuccess);
    }

    public LoginView(ClinicDao clinicDao,
                     SecurityService securityService,
                     DatabaseManager dbManager,
                     Runnable onModeChanged,
                     Consumer<User> onLoginSuccess) {
        DatabaseManager activeDbManager = dbManager != null ? dbManager : (clinicDao != null ? clinicDao.getDatabaseManager() : null);
        setStyle("-fx-background-color: #f1f5f9;");
        setAlignment(Pos.CENTER);

        // Center Login Card
        VBox card = new VBox(14);
        card.setMaxWidth(470);
        card.setPadding(new Insets(28, 34, 28, 34));
        card.setAlignment(Pos.CENTER);
        card.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-background-radius: 12px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 12px; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 20, 0, 0, 4);"
        );

        // Header Branding
        Label brandTitle = new Label("OPENHOMEO");
        brandTitle.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 28));
        brandTitle.setTextFill(Color.web("#0284c7"));

        Label brandSubtitle = new Label("Classical Homeopathy Professional Workstation");
        brandSubtitle.getStyleClass().add(Styles.TEXT_MUTED);
        brandSubtitle.setFont(Font.font("System", FontWeight.NORMAL, 13));

        Separator sep1 = new Separator();
        sep1.setPadding(new Insets(4, 0, 4, 0));

        Label authTitle = new Label("Practitioner Authentication");
        authTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        Label authDesc = new Label("Unlock your clinic database and patient records");
        authDesc.getStyleClass().add(Styles.TEXT_MUTED);
        authDesc.setFont(Font.font("System", 12));

        // Form Fields
        VBox userBox = new VBox(6);
        userBox.setAlignment(Pos.CENTER_LEFT);
        Label userLabel = new Label("Username");
        userLabel.getStyleClass().add(Styles.TEXT_BOLD);
        TextField usernameField = new TextField("admin");
        usernameField.setPromptText("Enter username");
        usernameField.setPrefHeight(38);
        userBox.getChildren().addAll(userLabel, usernameField);

        VBox passBox = new VBox(6);
        passBox.setAlignment(Pos.CENTER_LEFT);
        Label passLabel = new Label("Master Passphrase");
        passLabel.getStyleClass().add(Styles.TEXT_BOLD);
        PasswordField passwordField = new PasswordField();
        passwordField.setText("admin123");
        passwordField.setPromptText("Enter master passphrase");
        passwordField.setPrefHeight(38);
        passBox.getChildren().addAll(passLabel, passwordField);

        // Error Feedback Label
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add(Styles.DANGER);
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // Unlock Button
        Button unlockBtn = new Button("Unlock Clinic Database");
        unlockBtn.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);
        unlockBtn.setMaxWidth(Double.MAX_VALUE);
        unlockBtn.setPrefHeight(42);
        unlockBtn.setDefaultButton(true);

        Runnable performLogin = () -> {
            String u = usernameField.getText() != null ? usernameField.getText().trim() : "";
            String p = passwordField.getText() != null ? passwordField.getText() : "";

            if (u.isBlank() || p.isEmpty()) {
                errorLabel.setText("Please enter both username and passphrase.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                return;
            }

            try {
                Optional<User> userOpt = clinicDao.findUserByUsername(u);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (securityService.verifyPassword(p, user.passwordHash())) {
                        log.info("Practitioner authenticated successfully: {}", u);
                        onLoginSuccess.accept(user);
                        return;
                    }
                }
            } catch (Exception ex) {
                log.error("Authentication failed due to database error", ex);
                errorLabel.setText("Database access error: " + ex.getMessage());
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                return;
            }

            errorLabel.setText("Invalid credentials. Please verify your username and passphrase.");
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        };

        unlockBtn.setOnAction(e -> performLogin.run());
        usernameField.setOnAction(e -> performLogin.run());
        passwordField.setOnAction(e -> performLogin.run());

        // Role Presets Buttons
        HBox roleBox = new HBox(8);
        roleBox.setAlignment(Pos.CENTER);
        Button adminBtn = new Button("Admin");
        adminBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        adminBtn.setOnAction(e -> {
            usernameField.setText("admin");
            passwordField.setText("admin123");
        });

        Button doctorBtn = new Button("Doctor");
        doctorBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        doctorBtn.setOnAction(e -> {
            usernameField.setText("doctor");
            passwordField.setText("doctor123");
        });

        Button pharmaBtn = new Button("Pharmacist");
        pharmaBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        pharmaBtn.setOnAction(e -> {
            usernameField.setText("pharmacist");
            passwordField.setText("pharma123");
        });

        roleBox.getChildren().addAll(new Label("Quick Switch:"), adminBtn, doctorBtn, pharmaBtn);

        // Workstation Deployment Mode Strip (Standalone / Shared Clinic Server / Client Terminal)
        VBox modeStrip = new VBox(6);
        modeStrip.setPadding(new Insets(8, 12, 8, 12));
        modeStrip.setAlignment(Pos.CENTER);

        Label modeBadge = new Label();
        modeBadge.setWrapText(true);
        modeBadge.setFont(Font.font("System", FontWeight.BOLD, 11));

        Button configNetBtn = new Button("⚙ Configure Server / Client Mode");
        configNetBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);

        Runnable refreshModeStrip = () -> {
            if (activeDbManager == null) {
                modeBadge.setText("🖥️ Mode: Standalone Workstation (Local DB)");
                modeStrip.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8;");
                return;
            }
            DeploymentProfile p = activeDbManager.getDeploymentProfile();
            if (p.mode() == DeploymentProfile.DeploymentMode.SERVER) {
                String lanIp = MobileCompanionServer.getLocalLanIp();
                modeBadge.setText("🟢 Clinic DB Server HOST Active  •  LAN IP: " + lanIp + ":" + p.serverPort() + "  •  Pairing Key: " + p.pairingKey());
                modeBadge.setStyle("-fx-text-fill: #166534;");
                modeStrip.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #86efac; -fx-border-radius: 8; -fx-background-radius: 8;");
                unlockBtn.setText("Start Server & Unlock Clinic DB");
            } else if (p.mode() == DeploymentProfile.DeploymentMode.CLIENT) {
                modeBadge.setText("🌐 Client Terminal -> Shared Server " + p.serverHost() + ":" + p.serverPort() + " (No Isolated Local DB)");
                modeBadge.setStyle("-fx-text-fill: #1d4ed8;");
                modeStrip.setStyle("-fx-background-color: #eff6ff; -fx-border-color: #93c5fd; -fx-border-radius: 8; -fx-background-radius: 8;");
                unlockBtn.setText("Connect & Unlock Shared Clinic DB");
            } else {
                modeBadge.setText("🖥️ Mode: Standalone Workstation (Local Clinic Database)");
                modeBadge.setStyle("-fx-text-fill: #334155;");
                modeStrip.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8;");
                unlockBtn.setText("Unlock Clinic Database");
            }
        };
        refreshModeStrip.run();

        configNetBtn.setOnAction(e -> showDeploymentConfigDialog(activeDbManager, refreshModeStrip, onModeChanged, errorLabel));
        modeStrip.getChildren().addAll(modeBadge, configNetBtn);

        // Footer info
        Separator sep2 = new Separator();
        HBox footerBox = new HBox(8);
        footerBox.setAlignment(Pos.CENTER);
        Label hintLabel = new Label("RBAC Privileges: Admin | Doctor | Pharmacist");
        hintLabel.getStyleClass().add(Styles.TEXT_MUTED);
        hintLabel.setFont(Font.font("System", 11));
        footerBox.getChildren().add(hintLabel);

        card.getChildren().addAll(
                brandTitle, brandSubtitle, sep1,
                modeStrip,
                authTitle, authDesc,
                userBox, passBox,
                roleBox,
                errorLabel,
                unlockBtn,
                sep2, footerBox
        );

        getChildren().add(card);
    }

    private void showDeploymentConfigDialog(DatabaseManager dbManager,
                                            Runnable refreshModeStrip,
                                            Runnable onModeChanged,
                                            Label loginErrorLabel) {
        if (dbManager == null) return;
        DeploymentProfile current = dbManager.getDeploymentProfile();

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Clinic Client / Server Network Configuration");
        dlg.setHeaderText("Choose how this OpenHomeo Workstation stores & shares patient records");

        VBox box = new VBox(12);
        box.setPadding(new Insets(14));
        box.setPrefWidth(540);

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton rbStandalone = new RadioButton("Standalone Workstation (Single PC local database)");
        RadioButton rbServer = new RadioButton("Clinic Database SERVER Host (Master clinic DB shared with Doctors/Pharmacists)");
        RadioButton rbClient = new RadioButton("CLIENT Terminal (Connect directly to Clinic Server DB over LAN / SSH)");
        rbStandalone.setToggleGroup(modeGroup);
        rbServer.setToggleGroup(modeGroup);
        rbClient.setToggleGroup(modeGroup);

        if (current.mode() == DeploymentProfile.DeploymentMode.SERVER) rbServer.setSelected(true);
        else if (current.mode() == DeploymentProfile.DeploymentMode.CLIENT) rbClient.setSelected(true);
        else rbStandalone.setSelected(true);

        VBox radiosBox = new VBox(6, rbStandalone, rbServer, rbClient);
        radiosBox.setStyle("-fx-background-color: #f8fafc; -fx-padding: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        TextField txtLabel = new TextField(current.clinicLabel());
        TextField txtHost = new TextField(current.serverHost());
        TextField txtPort = new TextField(String.valueOf(current.serverPort() > 0 ? current.serverPort() : DeploymentProfile.DEFAULT_RPC_PORT));
        TextField txtKey = new TextField(current.pairingKey());
        Button btnNewKey = new Button("🎲 New Key");
        btnNewKey.setOnAction(ev -> txtKey.setText(DeploymentProfile.generatePairingKey()));
        HBox keyRow = new HBox(6, txtKey, btnNewKey);
        HBox.setHgrow(txtKey, Priority.ALWAYS);

        Label lblLanHint = new Label("This PC's LAN IP: " + MobileCompanionServer.getLocalLanIp());
        lblLanHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #0369a1; -fx-font-weight: bold;");

        grid.addRow(0, new Label("Clinic Server Name:"), txtLabel);
        grid.addRow(1, new Label("Server Host IP / DNS:"), txtHost);
        grid.addRow(2, new Label("RPC Port (default 45825):"), txtPort);
        grid.addRow(3, new Label("Clinic Pairing Key:"), keyRow);
        grid.add(lblLanHint, 1, 4);

        Label probeStatus = new Label("Select Client mode to scan LAN or test connectivity.");
        probeStatus.setWrapText(true);
        probeStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Button btnScanLan = new Button("📡 Scan LAN for Clinic Server");
        btnScanLan.getStyleClass().add(Styles.BUTTON_OUTLINED);
        btnScanLan.setOnAction(ev -> {
            btnScanLan.setDisable(true);
            probeStatus.setText("Scanning local network subnet for OpenHomeo Clinic Server beacon...");
            Thread t = new Thread(() -> {
                Optional<NetworkDiscoveryService.DiscoveredClinicHost> found = NetworkDiscoveryService.discoverClinicHost(2500);
                Platform.runLater(() -> {
                    btnScanLan.setDisable(false);
                    if (found.isPresent()) {
                        NetworkDiscoveryService.DiscoveredClinicHost h = found.get();
                        txtHost.setText(h.hostIp());
                        txtPort.setText(String.valueOf(h.port()));
                        txtLabel.setText(h.clinicName());
                        rbClient.setSelected(true);
                        probeStatus.setText("✓ Discovered '" + h.clinicName() + "' at " + h.hostIp() + ":" + h.port() + " (" + h.responseTimeMs() + " ms). Enter Pairing Key from Server & click Apply.");
                        probeStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #166534; -fx-font-weight: bold;");
                    } else {
                        probeStatus.setText("⚠ No Clinic Server beacon answered on this LAN. Ensure the Server workstation is in 'SERVER' mode and UDP port 45824 / TCP port 45825 are allowed.");
                        probeStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #b45309;");
                    }
                });
            }, "openhomeo-login-lan-scan");
            t.setDaemon(true);
            t.start();
        });

        Button btnTestRpc = new Button("🔌 Test Server Connection");
        btnTestRpc.getStyleClass().add(Styles.BUTTON_OUTLINED);
        btnTestRpc.setOnAction(ev -> {
            int pNum;
            try { pNum = Integer.parseInt(txtPort.getText().trim()); }
            catch (Exception ex) { probeStatus.setText("Invalid port number."); return; }
            String h = txtHost.getText().trim();
            String k = txtKey.getText().trim();
            btnTestRpc.setDisable(true);
            probeStatus.setText("Testing authenticated RPC endpoint at " + h + ":" + pNum + "...");
            Thread t = new Thread(() -> {
                RemoteDatabaseManager.ConnectionTestResult res = RemoteDatabaseManager.testClinicRpcEndpoint(h, pNum, k, 3000);
                Platform.runLater(() -> {
                    btnTestRpc.setDisable(false);
                    probeStatus.setText((res.isSuccessful() ? "✓ " : "✗ ") + res.message());
                    probeStatus.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + (res.isSuccessful() ? "#166534;" : "#b91c1c;"));
                });
            }, "openhomeo-login-rpc-test");
            t.setDaemon(true);
            t.start();
        });

        Runnable updateFieldStates = () -> {
            boolean isServer = rbServer.isSelected();
            boolean isClient = rbClient.isSelected();
            txtLabel.setDisable(!isServer && !isClient);
            txtHost.setDisable(!isClient);
            txtPort.setDisable(!isServer && !isClient);
            txtKey.setDisable(!isServer && !isClient);
            btnNewKey.setDisable(!isServer);
            btnScanLan.setDisable(!isClient);
            btnTestRpc.setDisable(!isClient);
            if (isServer && ("127.0.0.1".equals(txtHost.getText().trim()) || txtHost.getText().isBlank())) {
                txtHost.setText(MobileCompanionServer.getLocalLanIp());
            }
        };
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> updateFieldStates.run());
        updateFieldStates.run();

        HBox actionRow = new HBox(10, btnScanLan, btnTestRpc);
        box.getChildren().addAll(radiosBox, grid, actionRow, probeStatus);

        dlg.getDialogPane().setContent(box);
        ButtonType applyType = new ButtonType("Apply Mode", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

        dlg.showAndWait().ifPresent(bt -> {
            if (bt != applyType) return;
            DeploymentProfile.DeploymentMode selectedMode = DeploymentProfile.DeploymentMode.STANDALONE;
            if (rbServer.isSelected()) selectedMode = DeploymentProfile.DeploymentMode.SERVER;
            else if (rbClient.isSelected()) selectedMode = DeploymentProfile.DeploymentMode.CLIENT;

            int port = DeploymentProfile.DEFAULT_RPC_PORT;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
                if (port < 1024 || port > 65535) port = DeploymentProfile.DEFAULT_RPC_PORT;
            } catch (Exception ignored) {}

            String host = selectedMode == DeploymentProfile.DeploymentMode.SERVER
                    ? MobileCompanionServer.getLocalLanIp()
                    : txtHost.getText().trim();
            if (host.isBlank()) host = "127.0.0.1";

            String key = txtKey.getText().trim();
            if (key.isBlank()) key = DeploymentProfile.generatePairingKey();
            String label = txtLabel.getText().trim().isBlank() ? "OpenHomeo Clinic Server" : txtLabel.getText().trim();

            DeploymentProfile updated = new DeploymentProfile(selectedMode, host, port, key, label);
            ImageVaultService localVault = new ImageVaultService(dbManager.getBaseDataDir() + File.separator + "vault");
            dbManager.reconfigureDeployment(updated, localVault);
            if (onModeChanged != null) {
                onModeChanged.run();
            }
            if (loginErrorLabel != null) {
                loginErrorLabel.setVisible(false);
                loginErrorLabel.setManaged(false);
            }
            refreshModeStrip.run();
        });
    }
}
