package com.campus;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.sql.SQLException;

/**
 * EditStudentDialogController
 *
 * Controls the overlay popup for editing a student's details.
 * Opened by StudentListController.showEditDialog(student).
 * On save, updates the DB and notifies the caller via onSaved callback.
 */
public class EditStudentDialogController {

    // ── FXML fields ───────────────────────────────────────────────────────────
    @FXML private StackPane    dialogRoot;        // the semi-transparent overlay
    @FXML private Label        dialogSubtitle;
    @FXML private TextField    fieldStudentId;
    @FXML private TextField    fieldFullName;
    @FXML private TextField    fieldCourse;
    @FXML private ComboBox<String> comboYearLevel;
    @FXML private TextField    fieldContact;
    @FXML private TextField    fieldEmail;
    @FXML private Label        errorLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private Student     student;
    private Runnable    onSaved;          // called after a successful save
    private Runnable    onCancelled;      // called when the dialog is dismissed

    private final StudentDAO studentDAO = new StudentDAO();

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        comboYearLevel.setItems(FXCollections.observableArrayList(
                "1st", "2nd", "3rd", "4th"
        ));

        // Close overlay when clicking the dark backdrop (outside the card)
        dialogRoot.setOnMouseClicked(e -> {
            if (e.getTarget() == dialogRoot) handleCancel();
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Populate the form with a student's current data.
     * Must be called before the dialog is shown.
     */
    public void setStudent(Student s) {
        this.student = s;

        dialogSubtitle.setText("Editing: " + s.getFullName() + " (" + s.getStudentId() + ")");

        fieldStudentId.setText(s.getStudentId());
        fieldFullName.setText(s.getFullName());
        fieldCourse.setText(s.getCourse());
        fieldContact.setText(s.getContact());
        fieldEmail.setText(s.getEmail());

        String year = s.getYearLevel();
        if (comboYearLevel.getItems().contains(year)) {
            comboYearLevel.setValue(year);
        } else {
            comboYearLevel.setValue(null);
        }

        clearError();
    }

    /** Callback fired after the record is successfully saved. */
    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    /** Callback fired when the dialog is cancelled without saving. */
    public void setOnCancelled(Runnable onCancelled) {
        this.onCancelled = onCancelled;
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML
    private void handleSave() {
        if (!validate()) return;

        student.setFullName(fieldFullName.getText().trim());
        student.setCourse(fieldCourse.getText().trim());
        student.setYearLevel(comboYearLevel.getValue());
        student.setContact(fieldContact.getText().trim());
        student.setEmail(fieldEmail.getText().trim());

        try {
            studentDAO.update(student);
            if (onSaved != null) onSaved.run();
        } catch (SQLException e) {
            showError("Save failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCancel() {
        if (onCancelled != null) onCancelled.run();
    }

    // ── Validation ────────────────────────────────────────────────────────────
    private boolean validate() {
        clearError();

        if (fieldFullName.getText().trim().isEmpty()) {
            showError("Full name is required.");
            fieldFullName.requestFocus();
            return false;
        }
        if (fieldCourse.getText().trim().isEmpty()) {
            showError("Course is required.");
            fieldCourse.requestFocus();
            return false;
        }
        if (comboYearLevel.getValue() == null) {
            showError("Please select a year level.");
            comboYearLevel.requestFocus();
            return false;
        }
        String contact = fieldContact.getText().trim();
        if (!contact.isEmpty() && !contact.matches("\\d{7,15}")) {
            showError("Contact number must be 7–15 digits.");
            fieldContact.requestFocus();
            return false;
        }
        String email = fieldEmail.getText().trim();
        if (!email.isEmpty() && !email.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            showError("Enter a valid email address.");
            fieldEmail.requestFocus();
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
}
