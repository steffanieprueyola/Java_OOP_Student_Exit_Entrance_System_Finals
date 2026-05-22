package com.example.test_student_management.controller;

import com.example.test_student_management.factory.AuthWindowFactory;
import com.example.test_student_management.repository.AuthRepository;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class SignUpController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;

    private final AuthRepository authRepository = new AuthRepository();

    @FXML
    private void handleCreateAccount(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Missing data", "Fill in all fields.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Password mismatch", "Passwords do not match.");
            return;
        }
        try {
            if (authRepository.register(username, password)) {
                showInfo("Account created", "You can now log in.");
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(AuthWindowFactory.createLoginScene());
                stage.setTitle("Login");
            } else {
                showError("Username taken", "Choose another username.");
            }
        } catch (Exception e) {
            showError("Sign up error", e.getMessage());
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) throws Exception {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.setScene(AuthWindowFactory.createLoginScene());
        stage.setTitle("Login");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
