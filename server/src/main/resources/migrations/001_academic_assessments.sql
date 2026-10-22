-- Migration 001: enrollment, class assignments, and timed assessments.
-- Run this ONCE against an existing PostgreSQL database, after taking a backup.
-- It is idempotent and preserves the current users.class_id / problems.class_id data.

BEGIN;

CREATE TABLE IF NOT EXISTS class_enrollments (
    class_id    INT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    student_id  INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (class_id, student_id)
);

-- Preserve the legacy single-class membership. The application now reads
-- memberships from this table and keeps users.class_id only for compatibility.
INSERT INTO class_enrollments (class_id, student_id)
SELECT class_id, id FROM users
WHERE role = 'STUDENT' AND class_id IS NOT NULL
ON CONFLICT (class_id, student_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS class_problems (
    id         SERIAL PRIMARY KEY,
    class_id   INT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    problem_id INT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    is_open    BOOLEAN NOT NULL DEFAULT TRUE,
    open_at    TIMESTAMP NULL,
    close_at   TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (class_id, problem_id),
    CHECK (close_at IS NULL OR open_at IS NULL OR close_at > open_at)
);

-- Keep all existing class-specific problems available after the migration.
INSERT INTO class_problems (class_id, problem_id, is_open)
SELECT class_id, id, COALESCE(is_active, TRUE) FROM problems
WHERE class_id IS NOT NULL
ON CONFLICT (class_id, problem_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS assessments (
    id              SERIAL PRIMARY KEY,
    class_id        INT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    opens_at        TIMESTAMP NOT NULL,
    closes_at       TIMESTAMP NOT NULL,
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      INT REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (closes_at > opens_at)
);

CREATE TABLE IF NOT EXISTS assessment_problems (
    assessment_id INT NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    problem_id    INT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    order_num     INT NOT NULL DEFAULT 0,
    PRIMARY KEY (assessment_id, problem_id)
);

ALTER TABLE submissions ADD COLUMN IF NOT EXISTS class_problem_id INT
    REFERENCES class_problems(id) ON DELETE SET NULL;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS assessment_id INT
    REFERENCES assessments(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_class_enrollments_student ON class_enrollments(student_id);
CREATE INDEX IF NOT EXISTS idx_class_problems_class ON class_problems(class_id);
CREATE INDEX IF NOT EXISTS idx_submissions_class_problem_language
    ON submissions(user_id, class_problem_id, language, verdict);
CREATE INDEX IF NOT EXISTS idx_assessments_class_window
    ON assessments(class_id, opens_at, closes_at);

COMMIT;
