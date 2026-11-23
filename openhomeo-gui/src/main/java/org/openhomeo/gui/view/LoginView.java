package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.model.User;
import org.openhomeo.core.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Modern In-Window Login View utilizing AtlantaFX styling.
 * Renders directly inside the primary application stage for guaranteed visibility and focus.
 */
public class LoginView extends StackPane {
    private static final Logger log = LoggerFactory.getLogger(LoginView.class);

    public LoginView(ClinicDao clinicDao, SecurityService securityService, Consumer<User> onLoginSuccess) {
        setStyle("-fx-background-color: #f1f5f9;");
        setAlignment(Pos.CENTER);

        // Center Login Card
        VBox card = new VBox(16);
        card.setMaxWidth(440);
        card.setPadding(new Insets(32, 36, 32, 36));
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
                authTitle, authDesc,
                userBox, passBox,
                roleBox,
                errorLabel,
                unlockBtn,
                sep2, footerBox
        );

        getChildren().add(card);
    }
}
