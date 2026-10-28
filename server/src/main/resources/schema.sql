-- ============================================================
-- Schema SQL cho hệ thống chấm code tự động
-- Database: PostgreSQL (Neon.db)
-- Chạy file này lần đầu để tạo toàn bộ cấu trúc DB
-- ============================================================

-- Custom ENUM types
CREATE TYPE user_role AS ENUM ('STUDENT', 'TEACHER');
-- Difficulty levels: 1 (de nhat) den 6 (kho nhat)
CREATE TYPE verdict_type AS ENUM ('PENDING', 'JUDGING', 'AC', 'WA', 'TLE', 'RE', 'CE', 'SECURITY_VIOLATION');

-- ──────────────────────────────────────────────────────────────
-- Bảng users
-- ──────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              SERIAL PRIMARY KEY,
    username        VARCHAR(50)  UNIQUE NOT NULL,
    password_hash   VARCHAR(255)        NOT NULL,  -- "salt:sha256hash"
    full_name       VARCHAR(100),
    role            user_role    DEFAULT 'STUDENT',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  users                IS 'Tài khoản người dùng (student/teacher)';
COMMENT ON COLUMN users.password_hash  IS 'Format: base64(salt):base64(sha256(salt+password))';

-- ──────────────────────────────────────────────────────────────
-- Bảng problems
-- ──────────────────────────────────────────────────────────────
CREATE TABLE problems (
    id              SERIAL PRIMARY KEY,
    title           VARCHAR(200)       NOT NULL,
    description     TEXT               NOT NULL,
    time_limit_ms   INT       DEFAULT 1000,       -- 1 giây
    memory_limit_kb INT       DEFAULT 524288,     -- 512 MB
    difficulty      INT DEFAULT 1 CHECK (difficulty >= 1 AND difficulty <= 6),
    created_by      INT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active       BOOLEAN   DEFAULT TRUE,
    problem_type    VARCHAR(20) DEFAULT 'IO_MATCHING',
    oop_spec        TEXT,

    FOREIGN KEY (created_by) REFERENCES users(id)
);

COMMENT ON TABLE  problems               IS 'Danh sách bài toán';
COMMENT ON COLUMN problems.time_limit_ms IS 'Giới hạn thời gian chạy (milliseconds)';
COMMENT ON COLUMN problems.memory_limit_kb IS 'Giới hạn bộ nhớ (kilobytes)';

-- ──────────────────────────────────────────────────────────────
-- Bảng test_cases
-- ──────────────────────────────────────────────────────────────
CREATE TABLE test_cases (
    id              SERIAL PRIMARY KEY,
    problem_id      INT          NOT NULL,
    input_data      TEXT         NOT NULL,
    expected_output TEXT         NOT NULL,
    is_sample       BOOLEAN      DEFAULT FALSE,  -- TRUE = hiển thị cho student
    order_num       INT          DEFAULT 0,      -- thứ tự chạy

    FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE CASCADE
);

CREATE INDEX idx_test_cases_problem_id ON test_cases(problem_id);

COMMENT ON TABLE  test_cases           IS 'Test cases cho từng bài toán';
COMMENT ON COLUMN test_cases.is_sample IS 'TRUE = test case mẫu hiển thị trong đề bài, FALSE = hidden';

-- ──────────────────────────────────────────────────────────────
-- Bảng submissions
-- ──────────────────────────────────────────────────────────────
CREATE TABLE submissions (
    id              SERIAL PRIMARY KEY,
    user_id         INT          NOT NULL,
    problem_id      INT          NOT NULL,
    source_code     TEXT         NOT NULL,
    language        VARCHAR(20)  NOT NULL,         -- 'java' | 'cpp' | 'python'
    verdict         verdict_type DEFAULT 'PENDING',
    score           INT          DEFAULT 0,         -- 0-100
    passed_tests    INT          DEFAULT 0,
    total_tests     INT          DEFAULT 0,
    exec_time_ms    INT          DEFAULT 0,         -- thời gian chạy tối đa (ms)
    memory_used_kb  INT          DEFAULT 0,         -- bộ nhớ tối đa (KB)
    compile_output  TEXT,                           -- stderr của compiler (khi CE)
    submitted_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    judged_at       TIMESTAMP    NULL,

    FOREIGN KEY (user_id)    REFERENCES users(id),
    FOREIGN KEY (problem_id) REFERENCES problems(id)
);

CREATE INDEX idx_submissions_user_id    ON submissions(user_id);
CREATE INDEX idx_submissions_problem_id ON submissions(problem_id);
CREATE INDEX idx_submissions_verdict    ON submissions(verdict);
CREATE INDEX idx_submissions_submitted  ON submissions(submitted_at DESC);

COMMENT ON TABLE  submissions              IS 'Lịch sử nộp bài của students';
COMMENT ON COLUMN submissions.verdict      IS 'PENDING→JUDGING→AC|WA|TLE|RE|CE';
COMMENT ON COLUMN submissions.exec_time_ms IS 'Thời gian chạy tối đa qua tất cả test cases';

-- Nhật ký giám sát giữ bản chụp thông tin, không xóa theo user/bài tập/bài thi.
CREATE TABLE IF NOT EXISTS proctor_logs (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    username TEXT,
    full_name TEXT,
    problem_id INT NOT NULL,
    assessment_id INT,
    problem_title TEXT,
    event_type VARCHAR(100) NOT NULL,
    detail TEXT,
    violation_count INT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    client_ip TEXT,
    occurred_at_ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS proctor_violation_counters (
    user_id INT NOT NULL,
    problem_id INT NOT NULL,
    assessment_scope INT NOT NULL,
    violation_count INT NOT NULL,
    PRIMARY KEY (user_id, problem_id, assessment_scope)
);

CREATE INDEX IF NOT EXISTS idx_proctor_logs_assessment_time
    ON proctor_logs(assessment_id, occurred_at_ms DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_proctor_logs_time
    ON proctor_logs(occurred_at_ms DESC, id DESC);
