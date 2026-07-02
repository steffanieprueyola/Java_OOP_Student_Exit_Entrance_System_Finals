package com.campus;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.sql.SQLException;

/**
 * EditStudentDialogController
 *
 * Overlay popup for editing a student's details (name + sex).
 * Also allows admin to approve a blocked student's manual time-in.
 */
public class EditStudentDialogController {

    @FXML private StackPane        dialogRoot;
    @FXML private Label            dialogSubtitle;
    @FXML private TextField        fieldStudentId;
    @FXML private TextField        fieldFullName;
    @FXML private ComboBox<String> comboSex;
    @FXML private Label            blockedStatusLabel;
    @FXML private Button           approveManualBtn;
    @FXML private Label            errorLabel;

    private Student     student;
    private Runnable    onSaved;
    private Runnable    onCancelled;

    private final StudentDAO    studentDAO    = new StudentDAO();
    private final AttendanceDAO attendanceDAO = new AttendanceDAO();

    @FXML
    public void initialize() {
        comboSex.setItems(FXCollections.observableArrayList("M", "F"));

        dialogRoot.setOnMouseClicked(e -> {
            if (e.getTarget() == dialogRoot) handleCancel();
        });
    }

    public void setStudent(Student s) {
        this.student = s;

        dialogSubtitle.setText("Editing: " + s.getFullName() + " (" + s.getStudentId() + ")");
        fieldStudentId.setText(s.getStudentId());
        fieldFullName.setText(s.getFullName());
        comboSex.setValue(s.getSex());

        if (s.isBlockedNextDay()) {
            blockedStatusLabel.setText("⚠ This student is BLOCKED (missed time-out). Approve manual time-in below.");
            blockedStatusLabel.setVisible(true);
            blockedStatusLabel.setManaged(true);
            if (approveManualBtn != null) {
                approveManualBtn.setVisible(true);
                approveManualBtn.setManaged(true);
            }
        } else {
            if (blockedStatusLabel != null) {
                blockedStatusLabel.setVisible(false);
                blockedStatusLabel.setManaged(false);
            }
            if (approveManualBtn != null) {
                approveManualBtn.setVisible(false);
                approveManualBtn.setManaged(false);
            }
        }

        clearError();
    }

    public void setOnSaved(Runnable onSaved)         { this.onSaved = onSaved; }
    public void setOnCancelled(Runnable onCancelled) { this.onCancelled = onCancelled; }

    @FXML
    private void handleSave() {
        if (!validate()) return;

        student.setFullName(fieldFullName.getText().trim());
        student.setSex(comboSex.getValue());

        try {
            studentDAO.update(student);
            if (onSaved != null) onSaved.run();
        } catch (SQLException e) {
            showError("Save failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleApproveManual() {
        try {
            attendanceDAO.approveManualTimeIn(student.getStudentId());
            student.setBlockedNextDay(false);
            showAlert(Alert.AlertType.INFORMATION, "Approved",
                    student.getFullName() + " can now time-in today.");
            if (onSaved != null) onSaved.run();
        } catch (SQLException e) {
            showError("Approval failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        if (onCancelled != null) onCancelled.run();
    }

    private boolean validate() {
        clearError();

        if (fieldFullName.getText().trim().isEmpty()) {
            showError("Full name is required.");
            fieldFullName.requestFocus();
            return false;
        }
        if (comboSex.getValue() == null) {
            showError("Please select a sex (M/F).");
            comboSex.requestFocus();
            return false;
        }
        return true;
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
