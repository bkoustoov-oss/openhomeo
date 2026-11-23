package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.model.User;
import org.openhomeo.core.security.SecurityService;

import java.util.Optional;

/**
 * Modern Login Dialog utilizing AtlantaFX components.
 */
public class LoginDialog extends Dialog<User> {

    public LoginDialog(ClinicDao clinicDao, SecurityService securityService) {
        setTitle("OpenHomeo Security Vault");
        setHeaderText("Practitioner Authentication\nEnter your credentials to unlock your clinic database.");

        ButtonType loginButtonType = new ButtonType("Unlock Clinic", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 30, 10, 30));

        TextField usernameField = new TextField("admin");
        usernameField.setPromptText("Username");

        PasswordField passwordField = new PasswordField();
        passwordField.setText("admin123");
        passwordField.setPromptText("Master Passphrase");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add(Styles.DANGER);
        errorLabel.setVisible(false);

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Passphrase:"), 0, 1);
        grid.add(passwordField, 1, 1);

        VBox content = new VBox(10, grid, errorLabel);
        content.setAlignment(Pos.CENTER);
        getDialogPane().setContent(content);

        // Convert the result to a user when the login button is clicked
        setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                String u = usernameField.getText().trim();
                String p = passwordField.getText();

                try {
                    Optional<User> userOpt = clinicDao.findUserByUsername(u);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        if (securityService.verifyPassword(p, user.passwordHash())) {
                            return user;
                        }
                    }
                } catch (Exception e) {
                    errorLabel.setText("Database error: " + e.getMessage());
                    errorLabel.setVisible(true);
                    return null;
                }

                errorLabel.setText("Invalid username or passphrase.");
                errorLabel.setVisible(true);
                return null;
            }
            return null;
        });
    }
}
