package com.campus;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * AddStudentController
 *
 * Handles:
 *  1. Manual student registration (ID, Full Name, Sex)
 *  2. Bulk import via CSV or Excel (xlsx) file
 */
public class AddStudentController {

    @FXML private TextField    nameField;
    @FXML private TextField    studentIdField;
    @FXML private ComboBox<String> sexComboBox;

    private final StudentDAO studentDAO = new StudentDAO();

    @FXML
    public void initialize() {
        sexComboBox.getItems().addAll("M", "F");
    }

    /** Register a single student manually. */
    @FXML
    private void registerStudent() {

        if (isAnyBlank(nameField, studentIdField) || sexComboBox.getValue() == null) {
            showAlert(Alert.AlertType.ERROR, "Validation Error",
                    "All fields are required. Please complete the form.");
            return;
        }

        Student student = new Student(
                studentIdField.getText().trim(),
                nameField.getText().trim(),
                sexComboBox.getValue()
        );

        try {
            studentDAO.insert(student);
            showAlert(Alert.AlertType.INFORMATION, "Registration Successful",
                    "Student " + student.getFullName() + " has been registered.");
            clearForm();

        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Database Error",
                    "Could not save student: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Import students from a CSV or Excel file.
     *
     * Expected column order: student_id, full_name, sex
     * First row may be a header (auto-detected).
     * Excel (.xlsx) is read as plain CSV using openpyxl-style row iteration
     * via Apache POI — since POI is not in the pom, we accept .xlsx exported
     * as CSV, or the user can save-as CSV from Excel.
     *
     * We support both CSV and .xlsx (read via simple XML parse).
     */
    @FXML
    private void importFromFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Student Import File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV / Excel", "*.csv", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = chooser.showOpenDialog(MainApp.getStage());
        if (file == null) return;

        try {
            List<Student> students;

            if (file.getName().toLowerCase().endsWith(".xlsx")) {
                students = parseXlsx(file);
            } else {
                students = parseCsv(file);
            }

            if (students.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "No Data Found",
                        "The file had no valid student rows.\n\n" +
                        "Expected columns: student_id, full_name, sex (M/F)");
                return;
            }

            int inserted = studentDAO.insertBatch(students);
            int skipped  = students.size() - inserted;

            showAlert(Alert.AlertType.INFORMATION, "Import Complete",
                    "✅ " + inserted + " student(s) imported successfully.\n" +
                    (skipped > 0 ? "⚠ " + skipped + " row(s) skipped (duplicate ID or invalid data)." : ""));

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Import Failed",
                    "Could not read file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── CSV parser ───────────────────────────────────────────────────────────

    private List<Student> parseCsv(File file) throws IOException {
        List<Student> result = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 3) continue;

                String idVal   = cols[0].trim().replaceAll("^\"|\"$", "");
                String nameVal = cols[1].trim().replaceAll("^\"|\"$", "");
                String sexVal  = cols[2].trim().replaceAll("^\"|\"$", "").toUpperCase();

                // Skip header row
                if (firstLine) {
                    firstLine = false;
                    if (idVal.equalsIgnoreCase("student_id") ||
                        idVal.equalsIgnoreCase("id") ||
                        idVal.equalsIgnoreCase("studentid")) continue;
                }

                if (idVal.isEmpty() || nameVal.isEmpty()) continue;
                if (!sexVal.equals("M") && !sexVal.equals("F")) continue;

                result.add(new Student(idVal, nameVal, sexVal));
            }
        }

        return result;
    }

    // ── XLSX parser (reads shared-strings XML without Apache POI) ────────────

    private List<Student> parseXlsx(File file) throws IOException {
        // Use Java's built-in ZIP support to read xlsx without external deps
        List<String> sharedStrings = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();

        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {

            // 1. Read shared strings
            java.util.zip.ZipEntry ssEntry = zip.getEntry("xl/sharedStrings.xml");
            if (ssEntry != null) {
                try (InputStream is = zip.getInputStream(ssEntry)) {
                    String xml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("<t(?:\\s[^>]*)?>([^<]*)</t>").matcher(xml);
                    while (m.find()) sharedStrings.add(m.group(1));
                }
            }

            // 2. Read worksheet
            java.util.zip.ZipEntry wsEntry = zip.getEntry("xl/worksheets/sheet1.xml");
            if (wsEntry == null) {
                throw new IOException("Could not find sheet1 in the xlsx file.");
            }

            try (InputStream is = zip.getInputStream(wsEntry)) {
                String xml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

                // Parse rows
                java.util.regex.Matcher rowMatcher = java.util.regex.Pattern.compile("<row[^>]*>(.*?)</row>").matcher(xml);
                while (rowMatcher.find()) {
                    String rowXml = rowMatcher.group(1);
                    List<String> cells = new ArrayList<>();

                    java.util.regex.Matcher cellMatcher = java.util.regex.Pattern
                            .compile("<c[^>]*t=\"s\"[^>]*><v>(\\d+)</v></c>|<c[^>]*><v>([^<]*)</v></c>")
                            .matcher(rowXml);

                    while (cellMatcher.find()) {
                        if (cellMatcher.group(1) != null) {
                            int idx = Integer.parseInt(cellMatcher.group(1));
                            cells.add(idx < sharedStrings.size() ? sharedStrings.get(idx) : "");
                        } else {
                            cells.add(cellMatcher.group(2) != null ? cellMatcher.group(2) : "");
                        }
                    }

                    if (!cells.isEmpty()) {
                        rows.add(cells.toArray(new String[0]));
                    }
                }
            }
        }

        // 3. Convert to Student list
        List<Student> result = new ArrayList<>();
        boolean firstRow = true;

        for (String[] row : rows) {
            if (row.length < 3) continue;

            String id   = row[0].trim();
            String name = row[1].trim();
            String sex  = row[2].trim().toUpperCase();

            // Skip header
            if (firstRow) {
                firstRow = false;
                if (id.equalsIgnoreCase("student_id") || id.equalsIgnoreCase("id")) continue;
            }

            if (id.isEmpty() || name.isEmpty()) continue;
            if (!sex.equals("M") && !sex.equals("F")) continue;

            result.add(new Student(id, name, sex));
        }

        return result;
    }

    // ── Form helpers ─────────────────────────────────────────────────────────

    @FXML
    private void clearForm() {
        nameField.clear();
        studentIdField.clear();
        sexComboBox.setValue(null);
    }

    @FXML
    private void goBack() {
        try {
            MainApp.switchScene("dashboard.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isAnyBlank(TextField... fields) {
        for (TextField f : fields) {
            if (f.getText() == null || f.getText().isBlank()) return true;
        }
        return false;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
