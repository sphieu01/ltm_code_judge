-- ============================================================
-- Seed Data — Dữ liệu mẫu cho hệ thống
-- Chạy SAU schema.sql
-- ============================================================

-- ──────────────────────────────────────────────────────────────
-- Users mẫu
-- Password: "password123" — hash format: base64(salt):base64(sha256)
-- Để có hash thật, đăng ký qua ứng dụng hoặc dùng AuthService.register()
-- Các user dưới đây dùng password placeholder — THAY BẰNG HASH THẬT
-- ──────────────────────────────────────────────────────────────

-- Teacher account
INSERT INTO users (username, password_hash, full_name, role) VALUES
('teacher01', 'PLACEHOLDER_HASH', 'Giảng Viên LTM', 'TEACHER');

-- Student accounts
INSERT INTO users (username, password_hash, full_name, role) VALUES
('student01', 'PLACEHOLDER_HASH', 'Nguyễn Văn A', 'STUDENT'),
('student02', 'PLACEHOLDER_HASH', 'Trần Thị B', 'STUDENT'),
('student03', 'PLACEHOLDER_HASH', 'Lê Văn C', 'STUDENT');

-- ──────────────────────────────────────────────────────────────
-- Bài toán mẫu
-- ──────────────────────────────────────────────────────────────

-- Bài 1: Hello World (EASY)
INSERT INTO problems (title, description, time_limit_ms, memory_limit_kb, difficulty, created_by)
VALUES (
    'Hello World',
    'In ra "Hello, World!" (không có dấu ngoặc kép).\n\n**Input:** Không có input.\n\n**Output:** Một dòng duy nhất: `Hello, World!`',
    2000, 262144, 'EASY',
    (SELECT id FROM users WHERE username = 'teacher01')
);

-- Test cases cho Bài 1
INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, order_num)
VALUES
((SELECT id FROM problems WHERE title = 'Hello World'), '', 'Hello, World!', TRUE, 1),
((SELECT id FROM problems WHERE title = 'Hello World'), '', 'Hello, World!', FALSE, 2);

-- Bài 2: Tổng hai số (EASY)
INSERT INTO problems (title, description, time_limit_ms, memory_limit_kb, difficulty, created_by)
VALUES (
    'Tổng Hai Số',
    'Cho hai số nguyên a và b, hãy in ra tổng của chúng.\n\n**Input:** Một dòng chứa hai số nguyên a và b (−10^9 ≤ a, b ≤ 10^9).\n\n**Output:** In ra một số nguyên duy nhất là a + b.',
    2000, 262144, 'EASY',
    (SELECT id FROM users WHERE username = 'teacher01')
);

INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, order_num)
VALUES
((SELECT id FROM problems WHERE title = 'Tổng Hai Số'), '3 5', '8', TRUE, 1),
((SELECT id FROM problems WHERE title = 'Tổng Hai Số'), '0 0', '0', TRUE, 2),
((SELECT id FROM problems WHERE title = 'Tổng Hai Số'), '-1 1', '0', FALSE, 3),
((SELECT id FROM problems WHERE title = 'Tổng Hai Số'), '1000000000 1000000000', '2000000000', FALSE, 4),
((SELECT id FROM problems WHERE title = 'Tổng Hai Số'), '-999999999 999999999', '0', FALSE, 5);

-- Bài 3: Kiểm tra số nguyên tố (MEDIUM)
INSERT INTO problems (title, description, time_limit_ms, memory_limit_kb, difficulty, created_by)
VALUES (
    'Kiểm Tra Số Nguyên Tố',
    'Cho một số nguyên dương n, hãy kiểm tra xem n có phải số nguyên tố hay không.\n\n**Input:** Một dòng chứa số nguyên dương n (2 ≤ n ≤ 10^6).\n\n**Output:** In ra "YES" nếu n là số nguyên tố, "NO" nếu không phải.',
    2000, 262144, 'MEDIUM',
    (SELECT id FROM users WHERE username = 'teacher01')
);

INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, order_num)
VALUES
((SELECT id FROM problems WHERE title = 'Kiểm Tra Số Nguyên Tố'), '7', 'YES', TRUE, 1),
((SELECT id FROM problems WHERE title = 'Kiểm Tra Số Nguyên Tố'), '4', 'NO', TRUE, 2),
((SELECT id FROM problems WHERE title = 'Kiểm Tra Số Nguyên Tố'), '2', 'YES', FALSE, 3),
((SELECT id FROM problems WHERE title = 'Kiểm Tra Số Nguyên Tố'), '1000000', 'NO', FALSE, 4),
((SELECT id FROM problems WHERE title = 'Kiểm Tra Số Nguyên Tố'), '999983', 'YES', FALSE, 5);

-- Bài 4: Sắp xếp mảng (MEDIUM)
INSERT INTO problems (title, description, time_limit_ms, memory_limit_kb, difficulty, created_by)
VALUES (
    'Sắp Xếp Mảng',
    'Cho một mảng gồm n số nguyên, hãy sắp xếp mảng theo thứ tự tăng dần.\n\n**Input:**\n- Dòng 1: Số nguyên n (1 ≤ n ≤ 10^5)\n- Dòng 2: n số nguyên cách nhau bởi dấu cách\n\n**Output:** n số nguyên đã được sắp xếp, cách nhau bởi dấu cách.',
    2000, 262144, 'MEDIUM',
    (SELECT id FROM users WHERE username = 'teacher01')
);

INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, order_num)
VALUES
((SELECT id FROM problems WHERE title = 'Sắp Xếp Mảng'), '5
3 1 4 1 5', '1 1 3 4 5', TRUE, 1),
((SELECT id FROM problems WHERE title = 'Sắp Xếp Mảng'), '1
42', '42', FALSE, 2),
((SELECT id FROM problems WHERE title = 'Sắp Xếp Mảng'), '3
-5 0 3', '-5 0 3', FALSE, 3);

-- Bài 5: Fibonacci (HARD)
INSERT INTO problems (title, description, time_limit_ms, memory_limit_kb, difficulty, created_by)
VALUES (
    'Fibonacci Mod',
    'Tính số Fibonacci thứ n, kết quả lấy modulo 10^9+7.\n\nDãy Fibonacci: F(1) = 1, F(2) = 1, F(n) = F(n-1) + F(n-2).\n\n**Input:** Một dòng chứa số nguyên n (1 ≤ n ≤ 10^18).\n\n**Output:** F(n) mod (10^9+7).',
    3000, 262144, 'HARD',
    (SELECT id FROM users WHERE username = 'teacher01')
);

INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, order_num)
VALUES
((SELECT id FROM problems WHERE title = 'Fibonacci Mod'), '1', '1', TRUE, 1),
((SELECT id FROM problems WHERE title = 'Fibonacci Mod'), '10', '55', TRUE, 2),
((SELECT id FROM problems WHERE title = 'Fibonacci Mod'), '1000000000000000000', '209783453', FALSE, 3);

-- ──────────────────────────────────────────────────────────────
-- Xác nhận
-- ──────────────────────────────────────────────────────────────
SELECT 'Users:' AS info, COUNT(*) AS count FROM users
UNION ALL
SELECT 'Problems:', COUNT(*) FROM problems
UNION ALL
SELECT 'Test cases:', COUNT(*) FROM test_cases;
