-- =============================================================
-- CampusEntrySystem — schema
-- =============================================================

-- 1. students
-- =============================================================
CREATE TABLE IF NOT EXISTS students (
    student_id       VARCHAR(20)  PRIMARY KEY,
    full_name        VARCHAR(100) NOT NULL,
    sex              VARCHAR(1)   NOT NULL
    CONSTRAINT chk_sex CHECK (sex IN ('M', 'F')),
    blocked_next_day BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
    );

COMMENT ON TABLE  students IS 'Registered students in the campus system.';
COMMENT ON COLUMN students.student_id        IS 'Primary key — e.g. 2024-0001.';
COMMENT ON COLUMN students.sex               IS 'M (Male) or F (Female).';
COMMENT ON COLUMN students.blocked_next_day  IS 'TRUE if auto-timed-out at 10 PM and needs admin approval before next time-in.';

INSERT INTO students (student_id, full_name, sex)
VALUES
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
ON CONFLICT (student_id) DO NOTHING;

-- 2. faculty
-- =============================================================
CREATE TABLE IF NOT EXISTS faculty (
    id         SERIAL       PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,   -- BCrypt hash
    full_name  VARCHAR(100),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
    );

-- 3. attendance
-- =============================================================
CREATE TABLE IF NOT EXISTS attendance (
    id          BIGSERIAL    PRIMARY KEY,
    student_id  VARCHAR(20)  NOT NULL
    REFERENCES students(student_id) ON DELETE CASCADE,
    action      VARCHAR(10)  NOT NULL
    CONSTRAINT chk_action CHECK (action IN ('TIME_IN', 'TIME_OUT')),
    timestamp   TIMESTAMP    NOT NULL DEFAULT NOW(),
    exported    BOOLEAN      NOT NULL DEFAULT FALSE
    );

COMMENT ON TABLE  attendance IS 'Campus entry/exit log. Recording only allowed 6:00 AM – 10:00 PM.';
COMMENT ON COLUMN attendance.action   IS 'TIME_IN or TIME_OUT.';
COMMENT ON COLUMN attendance.exported IS 'Marked TRUE after CSV export.';

CREATE INDEX IF NOT EXISTS idx_attendance_student_date
    ON attendance (student_id, DATE(timestamp));

-- 6. Migration helper for existing databases (adds new columns if missing)
-- =============================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'students' AND column_name = 'sex'
    ) THEN
        ALTER TABLE students ADD COLUMN sex VARCHAR(1);
        UPDATE students SET sex = 'M' WHERE sex IS NULL;
        ALTER TABLE students ALTER COLUMN sex SET NOT NULL;
        ALTER TABLE students ADD CONSTRAINT chk_sex CHECK (sex IN ('M','F'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'students' AND column_name = 'blocked_next_day'
    ) THEN
        ALTER TABLE students ADD COLUMN blocked_next_day BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;
