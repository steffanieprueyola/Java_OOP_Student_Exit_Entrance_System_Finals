package com.campus;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.campus.Student;
import com.campus.AttendanceRecord;

/**
 * DashboardController
 */
public class DashboardController {

    // ── Table ────────────────────────────────────────────────────────────────
    @FXML private TableView<StudentDayRecord> entryTableView;
    @FXML private TableColumn<StudentDayRecord, String> colStudentNumber;
    @FXML private TableColumn<StudentDayRecord, String> colStudentName;
    @FXML private TableColumn<StudentDayRecord, String> colSex;
    @FXML private TableColumn<StudentDayRecord, String> colStatus;
    @FXML private TableColumn<StudentDayRecord, String> colTimeIn;
    @FXML private TableColumn<StudentDayRecord, String> colTimeOut;
    @FXML private TableColumn<StudentDayRecord, String> colDate;

    // ── CRUD action buttons ──────────────────────────────────────────────────
    @FXML private Button deleteButton;

    // ── Date + gender filter (export) ────────────────────────────────────────
    @FXML private DatePicker exportDatePicker;
    @FXML private Button exportByDateButton;
    @FXML private ComboBox<String> exportGenderComboBox;
    @FXML private Button exportByGenderButton;

    // ── Sex filter (currently timed-in) ──────────────────────────────────────
    @FXML private ComboBox<String> sexFilterComboBox;

    // ── Stats ────────────────────────────────────────────────────────────────
    @FXML private Label totalEntriesLabel;
    @FXML private Label timeInLabel;
    @FXML private Label timeOutLabel;
    @FXML private Label currentlyInLabel;
    @FXML private Label currentlyInMaleLabel;
    @FXML private Label currentlyInFemaleLabel;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final AttendanceDAO attendanceDAO = new AttendanceDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    private ObservableList<StudentDayRecord> allRows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        entryTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        entryTableView.setMaxWidth(Double.MAX_VALUE);

        // Default the date picker to today
        exportDatePicker.setValue(LocalDate.now());

        if (sexFilterComboBox != null) {
            sexFilterComboBox.setItems(FXCollections.observableArrayList(
                    "All", "Male Only", "Female Only"));
            sexFilterComboBox.setValue("All");
            sexFilterComboBox.valueProperty().addListener((obs, oldV, newV) -> applySexFilter());
        }

        // Gender combo used for export filtering (values match students.sex: M/F)
        if (exportGenderComboBox != null) {
            exportGenderComboBox.setItems(FXCollections.observableArrayList("M", "F"));
        }

        setupColumns();
        setupSelectionActions();
        loadData();
    }

    // ── Columns ──────────────────────────────────────────────────────────────
    private void setupColumns() {

        colStudentNumber.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().studentId));

        colStudentName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().studentName));

        if (colSex != null) {
            colSex.setCellValueFactory(c ->
                    new SimpleStringProperty(c.getValue().sex));
        }

        colStatus.setCellValueFactory(c -> {
            StudentDayRecord r = c.getValue();
            String status =
                    (r.timeIn != null && r.timeOut != null) ? "Complete"
                            : (r.timeIn != null) ? "Timed In"
                              : "Incomplete";
            return new SimpleStringProperty(status);
        });

        colTimeIn.setCellValueFactory(c -> {
            LocalDateTime t = c.getValue().timeIn;
            return new SimpleStringProperty(t != null ? t.format(TIME_FMT) : "—");
        });

        colTimeOut.setCellValueFactory(c -> {
            LocalDateTime t = c.getValue().timeOut;
            return new SimpleStringProperty(t != null ? t.format(TIME_FMT) : "—");
        });

        colDate.setCellValueFactory(c -> {
            LocalDate d = c.getValue().date;
            return new SimpleStringProperty(d != null ? d.format(DATE_FMT) : "—");
        });
    }

    // ── Selection-driven CRUD actions ────────────────────────────────────────
    private void setupSelectionActions() {
        deleteButton.setDisable(true);

        entryTableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldSelection, newSelection) -> {
                    boolean hasSelection = newSelection != null;
                    deleteButton.setDisable(!hasSelection);
                });
    }

    // ── Sex filter (table + currently-in counts) ─────────────────────────────
    private void applySexFilter() {
        if (sexFilterComboBox == null) return;
        String choice = sexFilterComboBox.getValue();

        if (choice == null || choice.equals("All")) {
            entryTableView.setItems(allRows);
        } else if (choice.equals("Male Only")) {
            entryTableView.setItems(filterBySex("M"));
        } else {
            entryTableView.setItems(filterBySex("F"));
        }
    }

    private ObservableList<StudentDayRecord> filterBySex(String sex) {
        return allRows.stream()
                .filter(r -> sex.equalsIgnoreCase(r.sex))
                .collect(java.util.stream.Collectors.toCollection(FXCollections::observableArrayList));
    }

    // ── Handlers ─────────────────────────────────────────────────────────────
    @FXML
    private void handleDeleteSelected() {
        StudentDayRecord selected = entryTableView.getSelectionModel().getSelectedItem();
        if (selected != null) handleDelete(selected);
    }

    private void handleDelete(StudentDayRecord record) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setContentText("Delete record for " + record.studentName + "?");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    attendanceDAO.deleteByStudentId(record.studentId);
                    loadData();
                } catch (SQLException e) {
                    showAlert(Alert.AlertType.ERROR, "Delete Failed", e.getMessage());
                }
            }
        });
    }

    // ── Load Data ────────────────────────────────────────────────────────────
    private void loadData() {
        try {
            List<AttendanceRecord> records = attendanceDAO.findAll();

            Map<String, StudentDayRecord> grouped = new LinkedHashMap<>();

            for (AttendanceRecord r : records) {
                String key = r.getStudentId();

                grouped.computeIfAbsent(key, k -> {
                    Student s = studentDAO.findByIdSafe(k);
                    return new StudentDayRecord(
                            k,
                            s != null ? s.getFullName() : "Unknown",
                            s != null ? s.getSex() : "—",
                            r.getTimestamp().toLocalDate()
                    );
                });

                StudentDayRecord row = grouped.get(key);

                if ("TIME_IN".equals(r.getAction()) && row.timeIn == null)
                    row.timeIn = r.getTimestamp();

                if ("TIME_OUT".equals(r.getAction()) && row.timeOut == null)
                    row.timeOut = r.getTimestamp();
            }

            allRows = FXCollections.observableArrayList(grouped.values());
            applySexFilter();

            long ins  = allRows.stream().filter(r -> r.timeIn != null).count();
            long outs = allRows.stream().filter(r -> r.timeOut != null).count();
            long currentlyIn = allRows.stream()
                    .filter(r -> r.timeIn != null && r.timeOut == null).count();

            totalEntriesLabel.setText(String.valueOf(allRows.size()));
            timeInLabel.setText(String.valueOf(ins));
            timeOutLabel.setText(String.valueOf(outs));
            if (currentlyInLabel != null) currentlyInLabel.setText(String.valueOf(currentlyIn));

            // Gender breakdown of who is currently timed-in (live, DB-driven)
            int[] genderCounts = attendanceDAO.countCurrentlyInBySex();
            if (currentlyInMaleLabel != null) currentlyInMaleLabel.setText(String.valueOf(genderCounts[0]));
            if (currentlyInFemaleLabel != null) currentlyInFemaleLabel.setText(String.valueOf(genderCounts[1]));

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    /**
     * Exports today's attendance log (original behaviour).
     */
    @FXML private void exportCSV() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Export Folder");
        File dir = chooser.showDialog(MainApp.getStage());

        if (dir == null) return;

        try {
            File csv = LogExporter.exportToday(Path.of(dir.toURI()));
            showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                    "CSV saved to:\n" + csv.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Exports attendance log filtered by the date chosen in the DatePicker.
     */
    @FXML private void exportCSVByDate() {
        LocalDate selectedDate = exportDatePicker.getValue();

        if (selectedDate == null) {
            showAlert(Alert.AlertType.WARNING, "No Date Selected",
                    "Please pick a date before exporting.");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Export Folder");
        File dir = chooser.showDialog(MainApp.getStage());

        if (dir == null) return;

        try {
            File csv = LogExporter.exportByDate(Path.of(dir.toURI()), selectedDate);
            showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                    "CSV for " + selectedDate.format(DATE_FMT) + " saved to:\n"
                            + csv.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Exports attendance log filtered by the date chosen in the DatePicker,
     * and (optionally) by the gender chosen in exportGenderComboBox.
     * If no gender is selected, falls back to date-only export.
     */
    @FXML private void exportCSVByDateAndGender() {
        LocalDate selectedDate = exportDatePicker.getValue();

        if (selectedDate == null) {
            showAlert(Alert.AlertType.WARNING, "No Date Selected",
                    "Please pick a date before exporting.");
            return;
        }

        String gender = exportGenderComboBox != null ? exportGenderComboBox.getValue() : null;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Export Folder");
        File dir = chooser.showDialog(MainApp.getStage());

        if (dir == null) return;

        try {
            File csv = (gender == null)
                    ? LogExporter.exportByDate(Path.of(dir.toURI()), selectedDate)
                    : LogExporter.exportByDateAndGender(Path.of(dir.toURI()), selectedDate, gender);

            showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                    "CSV for " + selectedDate.format(DATE_FMT)
                            + (gender != null ? " (" + gender + ")" : "")
                            + " saved to:\n" + csv.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Exports attendance log filtered only by the gender chosen in
     * exportGenderComboBox (ignores the date picker).
     */
    @FXML private void exportCSVByGender() {
        String gender = exportGenderComboBox != null ? exportGenderComboBox.getValue() : null;

        if (gender == null) {
            showAlert(Alert.AlertType.WARNING, "No Gender Selected",
                    "Please pick a gender before exporting.");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Export Folder");
        File dir = chooser.showDialog(MainApp.getStage());

        if (dir == null) return;

        try {
            File csv = LogExporter.exportByGender(gender, Path.of(dir.toURI()));
            showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                    "CSV for gender " + gender + " saved to:\n" + csv.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML private void addStudent() throws Exception {
        MainApp.switchScene("add-student.fxml");
    }

    @FXML private void viewRegisteredStudents() throws Exception {
        MainApp.switchScene("student-list.fxml");
    }

    @FXML private void refreshTable() {
        loadData();
    }

    @FXML private void logout() {
        try {
            MainApp.setCurrentStudentId(null);
            MainApp.switchScene("role-selection.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ── Inner Class ──────────────────────────────────────────────────────────
    private static class StudentDayRecord {
        String studentId;
        String studentName;
        String sex;
        LocalDateTime timeIn;
        LocalDateTime timeOut;
        LocalDate date;

        StudentDayRecord(String id, String name, String sex, LocalDate date) {
            this.studentId = id;
            this.studentName = name;
            this.sex = sex;
            this.date = date;
        }
    }
}
