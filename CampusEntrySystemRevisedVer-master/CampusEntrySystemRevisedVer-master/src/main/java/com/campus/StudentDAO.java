package com.campus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * StudentDAO — CRUD operations for the students table.
 */
public class StudentDAO {

    // ── CREATE ───────────────────────────────────────────────────────────────

    public void insert(Student s) throws SQLException {
        validate(s);

        if (exists(s.getStudentId())) {
            throw new IllegalArgumentException(
                    "Student ID " + s.getStudentId() + " is already registered.");
        }

        String sql = """
            INSERT INTO students (student_id, full_name, sex)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, s.getStudentId());
            ps.setString(2, s.getFullName());
            ps.setString(3, s.getSex());
            ps.executeUpdate();
        }
    }

    // ── BULK INSERT (for CSV/Excel import) ───────────────────────────────────

    /**
     * Insert multiple students, skipping duplicates.
     * Returns count of newly inserted rows.
     */
    public int insertBatch(List<Student> students) throws SQLException {
        String sql = """
            INSERT INTO students (student_id, full_name, sex)
            VALUES (?, ?, ?)
            ON CONFLICT (student_id) DO NOTHING
            """;

        int inserted = 0;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            for (Student s : students) {
                try {
                    validate(s);
                    ps.setString(1, s.getStudentId());
                    ps.setString(2, s.getFullName());
                    ps.setString(3, s.getSex());
                    ps.addBatch();
                    inserted++;
                } catch (IllegalArgumentException ignored) {
                    // skip invalid rows
                }
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        }
        return inserted;
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    public Optional<Student> findById(String studentId) throws SQLException {
        String sql = "SELECT * FROM students WHERE student_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }

        return Optional.empty();
    }

    /** Same as findById but swallows SQLException and returns null — convenient for lambdas. */
    public Student findByIdSafe(String studentId) {
        try {
            return findById(studentId).orElse(null);
        } catch (SQLException e) {
            return null;
        }
    }

    public List<Student> findAll() throws SQLException {
        String sql = "SELECT * FROM students ORDER BY full_name";
        List<Student> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(map(rs));
            }
        }

        return list;
    }

    public boolean exists(String studentId) throws SQLException {
        String sql = "SELECT 1 FROM students WHERE student_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    public void update(Student s) throws SQLException {
        validate(s);

        if (!exists(s.getStudentId())) {
            throw new IllegalArgumentException(
                    "Student ID " + s.getStudentId() + " not found.");
        }

        String sql = """
            UPDATE students
               SET full_name = ?,
                   sex       = ?,
                   blocked_next_day = ?
             WHERE student_id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, s.getFullName());
            ps.setString(2, s.getSex());
            ps.setBoolean(3, s.isBlockedNextDay());
            ps.setString(4, s.getStudentId());
            ps.executeUpdate();
        }
    }

    /** Set/clear the blocked_next_day flag for a student. */
    public void setBlocked(String studentId, boolean blocked) throws SQLException {
        String sql = "UPDATE students SET blocked_next_day = ? WHERE student_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, blocked);
            ps.setString(2, studentId);
            ps.executeUpdate();
        }
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    public void delete(String studentId) throws SQLException {
        if (!exists(studentId)) {
            throw new IllegalArgumentException(
                    "Student ID " + studentId + " not found.");
        }

        String sql = "DELETE FROM students WHERE student_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            ps.executeUpdate();
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Student map(ResultSet rs) throws SQLException {
        boolean blocked = false;
        try { blocked = rs.getBoolean("blocked_next_day"); } catch (SQLException ignored) {}
        return new Student(
                rs.getString("student_id"),
                rs.getString("full_name"),
                rs.getString("sex"),
                blocked
        );
    }

    private void validate(Student s) {
        if (s == null)
            throw new IllegalArgumentException("Student cannot be null.");

        assertNotBlank(s.getStudentId(), "Student ID");
        assertNotBlank(s.getFullName(),  "Full Name");
        assertNotBlank(s.getSex(),       "Sex");

        String sex = s.getSex().toUpperCase();
        if (!sex.equals("M") && !sex.equals("F")) {
            throw new IllegalArgumentException("Sex must be M or F.");
        }
        s.setSex(sex);
    }

    private void assertNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
    }
}
