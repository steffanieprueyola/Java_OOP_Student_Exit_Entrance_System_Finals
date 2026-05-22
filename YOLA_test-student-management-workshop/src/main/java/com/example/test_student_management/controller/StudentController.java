package com.example.test_student_management.controller;

import com.example.test_student_management.model.Student;
import com.example.test_student_management.repository.StudentRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Region;

import java.sql.SQLException;

public class StudentController {
    private static final int PAGE_SIZE = 10;

    @FXML private TextField idField;
    @FXML private TextField studentNumberField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField courseField;
    @FXML private TextField yearLevelField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TableView<Student> studentTable;
    @FXML private TableColumn<Student, Integer> idColumn;
    @FXML private TableColumn<Student, String> studentNumberColumn;
    @FXML private TableColumn<Student, String> firstNameColumn;
    @FXML private TableColumn<Student, String> lastNameColumn;
    @FXML private TableColumn<Student, String> courseColumn;
    @FXML private TableColumn<Student, Integer> yearLevelColumn;
    @FXML private TableColumn<Student, String> emailColumn;
    @FXML private TableColumn<Student, String> phoneColumn;
    @FXML private Pagination studentPagination;

    private final StudentRepository repository = new StudentRepository();
    private final ObservableList<Student> students = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        studentNumberColumn.setCellValueFactory(new PropertyValueFactory<>("studentNumber"));
        firstNameColumn.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        lastNameColumn.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        courseColumn.setCellValueFactory(new PropertyValueFactory<>("course"));
        yearLevelColumn.setCellValueFactory(new PropertyValueFactory<>("yearLevel"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        studentTable.setItems(students);
        studentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> populateForm(selected));
        studentPagination.setPageFactory(this::createPage);
        studentPagination.currentPageIndexProperty().addListener((obs, oldIndex, newIndex) -> {
            try {
                loadPage(newIndex.intValue());
            } catch (SQLException e) {
                showError("Database error", e.getMessage());
            }
        });
        refreshPagination();
    }

    @FXML
    private void handleAdd(ActionEvent event) {
        if (!validateForm(true)) {
            return;
        }
        try {
            repository.insert(buildStudent(false));
            clearForm();
            refreshPagination();
        } catch (Exception e) {
            showError("Unable to add student", e.getMessage());
        }
    }

    @FXML
    private void handleUpdate(ActionEvent event) {
        if (!validateForm(false)) {
            return;
        }
        try {
            repository.update(buildStudent(true));
            clearForm();
            refreshPagination();
        } catch (Exception e) {
            showError("Unable to update student", e.getMessage());
        }
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        Student selected = studentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No selection", "Choose a student to delete.");
            return;
        }
        try {
            repository.delete(selected.getId());
            clearForm();
            refreshPagination();
        } catch (Exception e) {
            showError("Unable to delete student", e.getMessage());
        }
    }

    @FXML
    private void handleClear(ActionEvent event) {
        clearForm();
    }

    private void refreshPagination() {
        try {
            int totalStudents = repository.countAll();
            int pageCount = Math.max(1, (int) Math.ceil(totalStudents / (double) PAGE_SIZE));
            studentPagination.setPageCount(pageCount);
            int pageIndex = Math.min(studentPagination.getCurrentPageIndex(), pageCount - 1);
            studentPagination.setCurrentPageIndex(pageIndex);
            loadPage(pageIndex);
        } catch (SQLException e) {
            showError("Database error", e.getMessage());
        }
    }

    private javafx.scene.Node createPage(int pageIndex) {
        return new Region();
    }

    private void loadPage(int pageIndex) throws SQLException {
        int offset = pageIndex * PAGE_SIZE;
        students.setAll(repository.findPage(PAGE_SIZE, offset));
        studentTable.getSelectionModel().clearSelection();
    }

    private Student buildStudent(boolean includeId) {
        Student student = new Student();
        if (includeId) {
            student.setId(Integer.parseInt(idField.getText().trim()));
        }
        student.setStudentNumber(studentNumberField.getText().trim());
        student.setFirstName(firstNameField.getText().trim());
        student.setLastName(lastNameField.getText().trim());
        student.setCourse(courseField.getText().trim());
        student.setYearLevel(Integer.parseInt(yearLevelField.getText().trim()));
        student.setEmail(emailField.getText().trim());
        student.setPhone(phoneField.getText().trim());
        return student;
    }

    private boolean validateForm(boolean allowEmptyId) {
        if (!allowEmptyId && idField.getText().trim().isEmpty()) {
            showError("Missing selection", "Select a student from the table first.");
            return false;
        }
        if (studentNumberField.getText().trim().isEmpty()
                || firstNameField.getText().trim().isEmpty()
                || lastNameField.getText().trim().isEmpty()
                || courseField.getText().trim().isEmpty()
                || yearLevelField.getText().trim().isEmpty()) {
            showError("Missing data", "Student number, name, course, and year level are required.");
            return false;
        }
        try {
            Integer.parseInt(yearLevelField.getText().trim());
            if (!allowEmptyId) {
                Integer.parseInt(idField.getText().trim());
            }
        } catch (NumberFormatException e) {
            showError("Invalid number", "ID and year level must be numeric.");
            return false;
        }
        return true;
    }

    private void populateForm(Student student) {
        if (student == null) {
            return;
        }
        idField.setText(String.valueOf(student.getId()));
        studentNumberField.setText(student.getStudentNumber());
        firstNameField.setText(student.getFirstName());
        lastNameField.setText(student.getLastName());
        courseField.setText(student.getCourse());
        yearLevelField.setText(String.valueOf(student.getYearLevel()));
        emailField.setText(student.getEmail());
        phoneField.setText(student.getPhone());
    }

    private void clearForm() {
        idField.clear();
        studentNumberField.clear();
        firstNameField.clear();
        lastNameField.clear();
        courseField.clear();
        yearLevelField.clear();
        emailField.clear();
        phoneField.clear();
        studentTable.getSelectionModel().clearSelection();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
