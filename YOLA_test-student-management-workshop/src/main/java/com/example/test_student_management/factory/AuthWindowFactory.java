package com.example.test_student_management.factory;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;

import java.io.IOException;

public final class AuthWindowFactory {
    private AuthWindowFactory() {
    }

    public static Scene createLoginScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(AuthWindowFactory.class.getResource("/com/example/test_student_management/login-view.fxml"));
        return new Scene(loader.load(), 420, 300);
    }

    public static Scene createSignUpScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(AuthWindowFactory.class.getResource("/com/example/test_student_management/signup-view.fxml"));
        return new Scene(loader.load(), 420, 360);
    }
}
