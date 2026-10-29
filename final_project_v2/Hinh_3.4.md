// ============================================================
// DBML CODE DÁN TRỰC TIẾP VÀO HTTPS://DBDIAGRAM.IO/D
// Sơ đồ thực thể quan hệ (ERD) - Hệ thống chấm mã nguồn tự động
// Khớp chuẩn xác 100% toàn bộ 13 bảng CSDL PostgreSQL trong mã nguồn
// ============================================================

Enum user_role {
  STUDENT
  TEACHER
}

Enum verdict_type {
  PENDING
  JUDGING
  AC
  WA
  TLE
  RE
  CE
  SECURITY_VIOLATION
}

// 1. Quản lý người dùng
Table users {
  id int [pk, increment]
  username varchar(50) [unique, not null]
  password_hash varchar(255) [not null]
  full_name varchar(100)
  role user_role [default: 'STUDENT']
  created_at timestamp [default: `CURRENT_TIMESTAMP`]
}

// 2. Danh mục môn học
Table courses {
  id int [pk, increment]
  code varchar(50) [unique, not null]
  name varchar(150) [not null]
  language varchar(20) [default: 'cpp']
  description text
  is_active boolean [default: true]
  created_at timestamp [default: `CURRENT_TIMESTAMP`]
}

// 3. Lớp tín chỉ
Table classes {
  id int [pk, increment]
  class_code varchar(50) [unique, not null]
  name varchar(150) [not null]
  course_id int [ref: > courses.id]
  teacher_id int [ref: > users.id]
  allowed_languages varchar(100) [default: 'c,cpp,java,python']
  created_at timestamp [default: `CURRENT_TIMESTAMP`]
}

// 4. Quan hệ Nhiều - Nhiều: Sinh viên tham gia Lớp tín chỉ
Table class_enrollments {
  class_id int [ref: > classes.id]
  student_id int [ref: > users.id]
  enrolled_at timestamp [default: `CURRENT_TIMESTAMP`]

  indexes {
    (class_id, student_id) [pk]
  }
}

// 5. Ngân hàng đề bài
Table problems {
  id int [pk, increment]
  title varchar(200) [not null]
  description text [not null]
  time_limit_ms int [default: 1000]
  memory_limit_kb int [default: 524288]
  difficulty int [default: 1, note: 'Độ khó từ 1 đến 6']
  created_by int [ref: > users.id]
  is_active boolean [default: true]
  problem_type varchar(20) [default: 'IO_MATCHING', note: 'IO_MATCHING hoặc JAVA_OOP']
  oop_spec text [note: 'JSON cấu hình kiểm thử phản xạ OOP']
  created_at timestamp [default: `CURRENT_TIMESTAMP`]
}

// 6. Quan hệ Nhiều - Nhiều: Giao bài tập cho Lớp tín chỉ
Table class_problems {
  id int [pk, increment]
  class_id int [ref: > classes.id]
  problem_id int [ref: > problems.id]
  is_open boolean [default: true]
  open_at timestamp
  close_at timestamp
  created_at timestamp [default: `CURRENT_TIMESTAMP`]

  indexes {
    (class_id, problem_id) [unique]
  }
}

// 7. Bộ kiểm thử thuật toán
Table test_cases {
  id int [pk, increment]
  problem_id int [ref: > problems.id]
  input_data text [not null]
  expected_output text [not null]
  is_sample boolean [default: false]
  order_num int [default: 0]
}

// 8. Đợt thi / Bài kiểm tra thực hành tính giờ
Table assessments {
  id int [pk, increment]
  class_id int [ref: > classes.id]
  title varchar(200) [not null]
  description text
  opens_at timestamp [not null]
  closes_at timestamp [not null]
  duration_minutes int [not null]
  is_published boolean [default: false]
  created_by int [ref: > users.id]
  created_at timestamp [default: `CURRENT_TIMESTAMP`]
}

// 9. Quan hệ Nhiều - Nhiều: Đề bài trong Đợt thi
Table assessment_problems {
  assessment_id int [ref: > assessments.id]
  problem_id int [ref: > problems.id]
  order_num int [default: 0]

  indexes {
    (assessment_id, problem_id) [pk]
  }
}

// 10. Quan hệ Nhiều - Nhiều: Lượt tham gia làm bài thi của Sinh viên
Table assessment_attempts {
  assessment_id int [ref: > assessments.id]
  student_id int [ref: > users.id]
  started_at timestamp [default: `CURRENT_TIMESTAMP`]
  completed_at timestamp

  indexes {
    (assessment_id, student_id) [pk]
  }
}

// 11. Lịch sử và kết quả bài nộp
Table submissions {
  id int [pk, increment]
  user_id int [ref: > users.id]
  problem_id int [ref: > problems.id]
  assessment_id int [ref: > assessments.id]
  class_problem_id int [ref: > class_problems.id]
  source_code text [not null, note: 'Mã nguồn đơn hoặc Base64 khi is_zip=true']
  language varchar(20) [not null]
  is_zip boolean [default: false]
  verdict verdict_type [default: 'PENDING']
  score int [default: 0, note: 'Thang điểm 0-100']
  passed_tests int [default: 0]
  total_tests int [default: 0]
  exec_time_ms int [default: 0]
  memory_used_kb int [default: 0]
  compile_output text
  submitted_at timestamp [default: `CURRENT_TIMESTAMP`]
  judged_at timestamp
}

// 12. Nhật ký giám sát chống gian lận trong phòng thi
Table proctor_logs {
  id int [pk, increment]
  user_id int [ref: > users.id]
  username text
  full_name text
  problem_id int [ref: > problems.id]
  assessment_id int [ref: > assessments.id]
  problem_title text
  event_type varchar(100) [not null]
  detail text
  violation_count int [not null]
  severity varchar(20) [not null]
  client_ip text
  occurred_at_ms bigint [not null]
}

// 13. Bộ đếm vi phạm giám sát theo phạm vi bài thi/bài tập
Table proctor_violation_counters {
  user_id int [ref: > users.id]
  problem_id int [ref: > problems.id]
  assessment_scope int
  violation_count int [not null]

  indexes {
    (user_id, problem_id, assessment_scope) [pk]
  }
}
