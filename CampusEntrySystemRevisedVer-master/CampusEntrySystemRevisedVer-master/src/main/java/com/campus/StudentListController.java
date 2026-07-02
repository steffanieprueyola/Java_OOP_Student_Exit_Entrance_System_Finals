package com.campus;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * StudentListController
 *
 * Displays all registered students in a searchable table.
 * Edit and Delete are CRUD action buttons below the table that
 * operate on the currently selected row.
 */
public class StudentListController {

    // ── Scene root (BorderPane wrapped in StackPane for overlay support) ─────
    @FXML private StackPane sceneRoot;

    // ── Table ─────────────────────────────────────────────────────────────────
    @FXML private TableView<Student>           studentTableView;
    @FXML private TableColumn<Student, String> colId;
    @FXML private TableColumn<Student, String> colFullName;
    @FXML private TableColumn<Student, String> colSex;
    @FXML private TableColumn<Student, String> colStatus;

    // ── CRUD action buttons ──────────────────────────────────────────────────
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    // ── Controls ──────────────────────────────────────────────────────────────
    @FXML private TextField searchField;
    @FXML private ComboBox<String> genderFilterComboBox;
    @FXML private Label     totalStudentsLabel;

    // ── Data ──────────────────────────────────────────────────────────────────
    private final StudentDAO studentDAO = new StudentDAO();
    private ObservableList<Student> allStudents = FXCollections.observableArrayList();

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        studentTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        studentTableView.setMaxWidth(Double.MAX_VALUE);

        setupColumns();
        setupSearch();
        setupGenderFilter();
        setupSelectionActions();
        loadStudents();
    }

    // ── Column setup ──────────────────────────────────────────────────────────
    private void setupColumns() {

        colId.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStudentId()));

        colFullName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFullName()));

        colSex.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getSex()));

        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().isBlockedNextDay() ? "⚠ Blocked — needs admin approval" : "Active"));
    }

    // ── Selection-driven CRUD actions ────────────────────────────────────────
    private void setupSelectionActions() {
        editButton.setDisable(true);
        deleteButton.setDisable(true);

        studentTableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldSelection, newSelection) -> {
                    boolean hasSelection = newSelection != null;
                    editButton.setDisable(!hasSelection);
                    deleteButton.setDisable(!hasSelection);
                });
    }

    // ── Search ────────────────────────────────────────────────────────────────
    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    // ── Gender filter ─────────────────────────────────────────────────────────
    private void setupGenderFilter() {
        genderFilterComboBox.setItems(FXCollections.observableArrayList(
                "All", "Male (M)", "Female (F)"));
        genderFilterComboBox.setValue("All");
        genderFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    /**
     * Applies the search text and gender selection together against
     * allStudents, and updates the table + total count.
     */
    private void applyFilters() {
        String query = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase();

        String genderChoice = genderFilterComboBox.getValue();
        String sexCode = switch (genderChoice == null ? "All" : genderChoice) {
            case "Male (M)"   -> "M";
            case "Female (F)" -> "F";
            default           -> null; // "All" or unset — no gender filter
        };

        ObservableList<Student> filtered = allStudents.stream()
                .filter(s -> query.isEmpty()
                        || s.getStudentId().toLowerCase().contains(query)
                        || s.getFullName().toLowerCase().contains(query))
                .filter(s -> sexCode == null || sexCode.equalsIgnoreCase(s.getSex()))
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        studentTableView.setItems(filtered);
        totalStudentsLabel.setText(String.valueOf(filtered.size()));
    }

    @FXML
    private void clearSearch() {
        searchField.clear();
        genderFilterComboBox.setValue("All");
        applyFilters();
    }

    // ── Data loading ──────────────────────────────────────────────────────────
    private void loadStudents() {
        try {
            List<Student> students = studentDAO.findAll();
            allStudents = FXCollections.observableArrayList(students);
            applyFilters();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error",
                    "Could not load students: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────
    @FXML
    private void handleEditSelected() {
        Student selected = studentTableView.getSelectionModel().getSelectedItem();
        if (selected != null) handleEdit(selected);
    }

    @FXML
    private void handleDeleteSelected() {
        Student selected = studentTableView.getSelectionModel().getSelectedItem();
        if (selected != null) handleDelete(selected);
    }

    private void handleEdit(Student student) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MainApp.class.getResource("/com/campus/edit-student-dialog.fxml"));
            StackPane overlay = loader.load();

            EditStudentDialogController dialogCtrl = loader.getController();
            dialogCtrl.setStudent(student);

            dialogCtrl.setOnSaved(() -> {
                sceneRoot.getChildren().remove(overlay);
                loadStudents();
            });

            dialogCtrl.setOnCancelled(() ->
                    sceneRoot.getChildren().remove(overlay));

            sceneRoot.getChildren().add(overlay);

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Navigation Error",
                    "Could not open edit form: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDelete(Student student) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Student");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Permanently delete \"" + student.getFullName() + "\" (" +
                        student.getStudentId() + ")?\n\nThis will also remove all their attendance records."
        );

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    studentDAO.delete(student.getStudentId());
                    allStudents.remove(student);
                    applyFilters();
                } catch (SQLException e) {
                    showAlert(Alert.AlertType.ERROR, "Delete Failed",
                            "Could not delete student: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    @FXML
    private void addStudent() {
        try {
            MainApp.switchScene("add-student.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void goBack() {
        try {
            MainApp.switchScene("dashboard.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}