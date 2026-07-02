package com.campus;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AttendanceDAO — entry-log operations for attendance table.
 *
 * Business rules:
 *  • Time-In / Time-Out only allowed 06:00–22:00.
 *  • If a student is still timed-in at 22:00, they are auto-timed-out and
 *    blocked_next_day is set; the next day they cannot time-in without
 *    admin manual approval (clearing the flag).
 *  • A student with blocked_next_day = true is told to visit admin.
 */
public class AttendanceDAO {

    private static final LocalTime OPEN_TIME  = LocalTime.of(6, 0);   // 06:00
    private static final LocalTime CLOSE_TIME = LocalTime.of(22, 0);  // 22:00

    private final StudentDAO studentDAO = new StudentDAO();

    // ── TIME IN ─────────────────────────────────────────────────────────────

    public long logTimeIn(String studentId) throws SQLException {
        requireStudentExists(studentId);
        checkOperatingHours();

        // Check if student is blocked from yesterday's auto-timeout
        Student student = studentDAO.findById(studentId)
                .orElseThrow(() -> new IllegalStateException("Student not found."));

        if (student.isBlockedNextDay()) {
            throw new IllegalStateException(
                "ACCESS BLOCKED\n\nYou were automatically timed-out at 10:00 PM yesterday " +
                "because you did not time out before closing.\n\n" +
                "Please go to the Admin Office for manual time-in approval.");
        }

        LocalDate today = LocalDate.now();

        if (hasActionToday(studentId, "TIME_IN", today)) {
            throw new IllegalArgumentException("Already timed in today.");
        }

        return insertLog(studentId, "TIME_IN", LocalDateTime.now());
    }

    // ── TIME OUT ────────────────────────────────────────────────────────────

    public long logTimeOut(String studentId) throws SQLException {
        requireStudentExists(studentId);
        checkOperatingHours();

        LocalDate today = LocalDate.now();

        if (!hasActionToday(studentId, "TIME_IN", today)) {
            throw new IllegalArgumentException("No TIME IN found today. Please time in first.");
        }

        if (hasActionToday(studentId, "TIME_OUT", today)) {
            throw new IllegalArgumentException("Already timed out today.");
        }

        return insertLog(studentId, "TIME_OUT", LocalDateTime.now());
    }

    // ── AUTO TIMEOUT (called by scheduler at 22:00) ──────────────────────────

    /**
     * For every student still timed-in today without a time-out,
     * inserts a TIME_OUT at 22:00 and sets their blocked_next_day flag.
     */
    public int autoTimeOutAll() throws SQLException {
        // Find all students timed-in today without timeout
        String sql = """
            SELECT DISTINCT a.student_id
            FROM attendance a
            WHERE DATE(a.timestamp) = CURRENT_DATE
              AND a.action = 'TIME_IN'
              AND NOT EXISTS (
                  SELECT 1 FROM attendance b
                  WHERE b.student_id = a.student_id
                    AND DATE(b.timestamp) = CURRENT_DATE
                    AND b.action = 'TIME_OUT'
              )
            """;

        List<String> toTimeout = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                toTimeout.add(rs.getString("student_id"));
            }
        }

        LocalDateTime closeTs = LocalDate.now().atTime(CLOSE_TIME);

        for (String sid : toTimeout) {
            insertLog(sid, "TIME_OUT", closeTs);
            studentDAO.setBlocked(sid, true);
        }

        return toTimeout.size();
    }

    /** Admin approves manual time-in: clears the blocked flag. */
    public void approveManualTimeIn(String studentId) throws SQLException {
        requireStudentExists(studentId);
        studentDAO.setBlocked(studentId, false);
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

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

    // ── FIND ─────────────────────────────────────────────────────────────────

    public List<AttendanceRecord> findAll() throws SQLException {
        String sql = """
            SELECT id, student_id, action, timestamp
            FROM attendance
            ORDER BY timestamp DESC
        """;

        return queryAll(sql);
    }

    public List<AttendanceRecord> findByStudent(String studentId) throws SQLException {
        String sql = """
            SELECT id, student_id, action, timestamp
            FROM attendance
            WHERE student_id = ?
            ORDER BY timestamp DESC
        """;

        return query(sql, studentId);
    }

    // ── GENDER STATS ─────────────────────────────────────────────────────────

    /**
     * Returns count of students currently timed-in (no timeout today),
     * broken down by sex.
     */
    public int[] countCurrentlyInBySex() throws SQLException {
        // [0] = male, [1] = female
        String sql = """
            SELECT s.sex, COUNT(*) AS cnt
            FROM attendance a
            JOIN students s ON s.student_id = a.student_id
            WHERE DATE(a.timestamp) = CURRENT_DATE
              AND a.action = 'TIME_IN'
              AND NOT EXISTS (
                  SELECT 1 FROM attendance b
                  WHERE b.student_id = a.student_id
                    AND DATE(b.timestamp) = CURRENT_DATE
                    AND b.action = 'TIME_OUT'
              )
            GROUP BY s.sex
            """;

        int male = 0, female = 0;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String sex = rs.getString("sex");
                int cnt    = rs.getInt("cnt");
                if ("M".equalsIgnoreCase(sex)) male = cnt;
                else if ("F".equalsIgnoreCase(sex)) female = cnt;
            }
        }

        return new int[]{male, female};
    }

    // ── INSERT LOG ───────────────────────────────────────────────────────────

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
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }

        return -1;
    }

    // ── OPERATING HOURS CHECK ────────────────────────────────────────────────

    private void checkOperatingHours() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(OPEN_TIME)) {
            throw new IllegalStateException(
                "Time-in is not yet open.\n\nThe campus entry system opens at 6:00 AM.\nPlease come back later.");
        }
        if (!now.isBefore(CLOSE_TIME)) {
            throw new IllegalStateException(
                "The campus entry system is already closed.\n\nEntry/exit recording ends at 10:00 PM.");
        }
    }

    // ── CHECK ACTION ─────────────────────────────────────────────────────────

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

    // ── QUERY HELPERS ────────────────────────────────────────────────────────

    private List<AttendanceRecord> query(String sql, String studentId) throws SQLException {
        List<AttendanceRecord> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }

        return list;
    }

    private List<AttendanceRecord> queryAll(String sql) throws SQLException {
        List<AttendanceRecord> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(map(rs));
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

    // ── VALIDATION ───────────────────────────────────────────────────────────

    private void requireStudentExists(String studentId) throws SQLException {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Student ID required.");
        }

        if (!studentDAO.exists(studentId)) {
            throw new IllegalStateException("Student not registered: " + studentId);
        }
    }
}
