package com.campus;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * DatabaseInitializer
 *
 * Creates all tables and seeds default data on first run.
 * Call DatabaseInitializer.initialize() from MainApp.start() before showing any scene.
 *
 * Changes:
 *  - students table now stores only student_id, full_name, sex (M/F),
 *    and blocked_next_day (set when auto-timed-out at 10 PM closing).
 *  - Faculty seed password is stored as a BCrypt hash, not plain text.
 *  - Includes a migration step so existing databases (with the old
 *    course/year_level/contact/email columns) are upgraded in place.
 */
public class DatabaseInitializer {

    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // ── 1. students ─────────────────────────────────────────────────────
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS students (
                    student_id       VARCHAR(20)  PRIMARY KEY,
                    full_name        VARCHAR(100) NOT NULL,
                    sex              VARCHAR(1)   NOT NULL DEFAULT 'M' CHECK (sex IN ('M', 'F')),
                    blocked_next_day BOOLEAN      NOT NULL DEFAULT FALSE,
                    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
    )
""");

            // ── 1a. Migration: upgrade older databases that still have the
            //        legacy course/year_level/contact/email columns ──────────
            migrateLegacyStudentsTable(conn);

            // ── 2. faculty ───────────────────────────────────────────────────
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS faculty (
                    id         SERIAL       PRIMARY KEY,
                    username   VARCHAR(50)  NOT NULL UNIQUE,
                    password   VARCHAR(255) NOT NULL,
                    full_name  VARCHAR(100),
                    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
                )
            """);

            // Only hash + insert when the default account doesn't exist yet.
            String checkSql  = "SELECT 1 FROM faculty WHERE username = ?";
            String insertSql = """
                INSERT INTO faculty (username, password, full_name)
                VALUES (?, ?, 'System Administrator')
                """;

            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setString(1, "admin");
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) {                          // ← only runs once, ever
                        String hash = BCrypt.hashpw("admin123", BCrypt.gensalt());
                        try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                            ins.setString(1, "admin");
                            ins.setString(2, hash);
                            ins.executeUpdate();
                        }
                    }
                }
            }

            // ── 3. attendance ────────────────────────────────────────────────
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS attendance (
                    id          BIGSERIAL    PRIMARY KEY,
                    student_id  VARCHAR(20)  NOT NULL
                                REFERENCES students(student_id) ON DELETE CASCADE,
                    action      VARCHAR(10)  NOT NULL
                                CHECK (action IN ('TIME_IN', 'TIME_OUT')),
                    timestamp   TIMESTAMP    NOT NULL DEFAULT NOW(),
                    exported    BOOLEAN      NOT NULL DEFAULT FALSE
                )
            """);

            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_attendance_student_date
                ON attendance (student_id, DATE(timestamp))
            """);

            // Seed default students only on a brand new table
            stmt.executeUpdate("""
                INSERT INTO students (student_id, full_name, sex) VALUES
                ('2024-0001', 'Johnny Bravo', 'M'),
                ('2024-0002', 'Maria Santos', 'F'),
                ('2024-0003', 'Juan Dela Cruz', 'M'),
                ('2024-0004', 'Ana Reyes', 'F'),
                ('2024-0005', 'Mark Lopez', 'M'),
                ('2024-0006', 'Sarah Garcia', 'F'),
                ('2024-0007', 'Kevin Torres', 'M'),
                ('2024-0008', 'Patricia Cruz', 'F'),
                ('2024-0009', 'James Mendoza', 'M'),
                ('2024-0010', 'Angelica Flores', 'F')
                ON CONFLICT (student_id) DO NOTHING
            """);

            System.out.println("[DB] Tables initialized successfully.");

        } catch (SQLException e) {
            System.err.println("[DB] Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * If the students table already exists from a previous version of the
     * app (with course/year_level/contact/email columns), this adapts it:
     *   - adds sex + blocked_next_day if missing
     *   - drops the old columns that are no longer used
     * Safe to run repeatedly; every step is conditional.
     */
    private static void migrateLegacyStudentsTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

            // Add sex column if missing
            stmt.executeUpdate("""
                ALTER TABLE students ADD COLUMN IF NOT EXISTS sex VARCHAR(1)
            """);
            stmt.executeUpdate("""
                UPDATE students SET sex = 'M' WHERE sex IS NULL
            """);
            stmt.executeUpdate("""
                ALTER TABLE students ALTER COLUMN sex SET NOT NULL
            """);
            stmt.executeUpdate("""
                ALTER TABLE students ALTER COLUMN sex SET DEFAULT 'M'
            """);

            // Add blocked_next_day column if missing
            stmt.executeUpdate("""
                ALTER TABLE students ADD COLUMN IF NOT EXISTS blocked_next_day BOOLEAN NOT NULL DEFAULT FALSE
            """);

            // Drop legacy columns if they still exist
            String[] legacyCols = {"course", "year_level", "contact", "email"};
            for (String col : legacyCols) {
                try {
                    stmt.executeUpdate("ALTER TABLE students DROP COLUMN IF EXISTS " + col);
                } catch (SQLException ignored) {
                    // column may have constraints; ignore and move on
                }
            }

        } catch (SQLException e) {
            // Non-fatal: log and continue, fresh installs won't hit this path anyway
            System.err.println("[DB] Migration step skipped/failed: " + e.getMessage());
        }
    }
}
