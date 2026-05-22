module com.example.test_student_management {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires io.github.cdimascio.dotenv.java;

    opens com.example.test_student_management.controller to javafx.fxml;
    opens com.example.test_student_management.model to javafx.base;
    exports com.example.test_student_management.app;
}
