package com.campus;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AttendanceDAO — entry-log operations for attendance table.
 */
public class AttendanceDAO {

    private final StudentDAO studentDAO = new StudentDAO();

    // ── TIME IN ─────────────────────────────────────────────
    public long logTimeIn(String studentId) throws SQLException {
        requireStudentExists(studentId);

        LocalDate today = LocalDate.now();

        if (hasActionToday(studentId, "TIME_IN", today)) {
            throw new IllegalArgumentException("Already timed in today.");
        }

        return insertLog(studentId, "TIME_IN", LocalDateTime.now());
    }

    // ── TIME OUT ────────────────────────────────────────────
    public long logTimeOut(String studentId) throws SQLException {
        requireStudentExists(studentId);

        LocalDate today = LocalDate.now();

        if (!hasActionToday(studentId, "TIME_IN", today)) {
            throw new IllegalArgumentException("No TIME IN found today.");
        }

        if (hasActionToday(studentId, "TIME_OUT", today)) {
            throw new IllegalArgumentException("Already timed out today.");
        }

        return insertLog(studentId, "TIME_OUT", LocalDateTime.now());
    }

    // ── DELETE (✅ FIX FOR YOUR ERROR) ───────────────────────
    public void deleteByStudentId(String studentId) throws SQLException {
        String sql = """
            DELETE FROM attendance
            WHERE student_id = ?
            AND DATE(timestamp) = CURRENT_DATE
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            ps.executeUpdate();
        }
    }

    // ── FIND TODAY ──────────────────────────────────────────
    public List<AttendanceRecord> findAll() throws SQLException {
        String sql = """
            SELECT id, student_id, action, timestamp
            FROM attendance
            ORDER BY timestamp DESC
        """;

        return queryAll(sql);
    }

    // ── FIND BY STUDENT ─────────────────────────────────────
    public List<AttendanceRecord> findByStudent(String studentId) throws SQLException {
        String sql = """
            SELECT id, student_id, action, timestamp
            FROM attendance
            WHERE student_id = ?
            ORDER BY timestamp DESC
        """;

        return query(sql, studentId);
    }

    // ── INSERT LOG ──────────────────────────────────────────
    private long insertLog(String studentId, String action, LocalDateTime ts) throws SQLException {

        String sql = """
            INSERT INTO attendance (student_id, action, timestamp)
            VALUES (?, ?, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, studentId);
            ps.setString(2, action);
            ps.setTimestamp(3, Timestamp.valueOf(ts));

            ps.executeUpdate(); // ✅ FIXED (was wrong before)

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return -1;
    }

    // ── CHECK ACTION ────────────────────────────────────────
    private boolean hasActionToday(String studentId, String action, LocalDate date) throws SQLException {

        String sql = """
            SELECT 1 FROM attendance
            WHERE student_id = ?
            AND action = ?
            AND DATE(timestamp) = ?
            LIMIT 1
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);
            ps.setString(2, action);
            ps.setDate(3, Date.valueOf(date));

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ── QUERY HELPERS ──────────────────────────────────────
    private List<AttendanceRecord> query(String sql, String studentId) throws SQLException {

        List<AttendanceRecord> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }

        return list;
    }

    private List<AttendanceRecord> queryAll(String sql) throws SQLException {

        List<AttendanceRecord> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(map(rs));
            }
        }

        return list;
    }

    private AttendanceRecord map(ResultSet rs) throws SQLException {
        return new AttendanceRecord(
                rs.getLong("id"),
                rs.getString("student_id"),
                rs.getString("action"),
                rs.getTimestamp("timestamp").toLocalDateTime()
        );
    }

    // ── VALIDATION ──────────────────────────────────────────
    private void requireStudentExists(String studentId) throws SQLException {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Student ID required.");
        }

        if (!studentDAO.exists(studentId)) {
            throw new IllegalStateException("Student not registered: " + studentId);
        }
    }
}
