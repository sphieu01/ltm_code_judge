---

name: "JavaFX Code Grader System"
description: "Design system & UI tokens for JavaFX automated code judging and grading desktop application."
tokens:
  colors:
    background-base: "#1E1E1E"
    background-surface: "#252526"
    background-editor: "#181818"
    border-subtle: "#333333"
    text-primary: "#CCCCCC"
    text-secondary: "#858585"
    
    # Verdict Status Colors
    status-ac: "#2ECC71"
    status-wa: "#E74C3C"
    status-tle: "#E67E22"
    status-ce: "#9B59B6"
    status-running: "#3498DB"
    
    accent: "#007ACC"
  typography:
    ui-font: "Segoe UI, Inter, sans-serif"
    code-font: "JetBrains Mono, Consolas, monospace"
    font-size-sm: "11px"
    font-size-md: "13px"
    font-size-lg: "15px"
    font-size-header: "18px"
  metrics:
    corner-radius: "4px"
    padding-base: "8px"
    item-gap: "6px"
---

## Overview

Hệ thống giao diện desktop tối ưu hóa cho công cụ chấm code tự động (Automated Code Grader) viết bằng JavaFX. Phong cách kỹ thuật cao, tập trung vào khả năng đọc mã, hiển thị kết quả testcase trực quan và tối ưu diện tích thao tác.

- **Information Density:** 7/10 — Compact & Functional
- **Theme:** Dark Theme (IDE Standard)
- **Target Platform:** Desktop (JavaFX 17+, Windows/macOS/Linux)

## Layout Architecture

Bố cục được xây dựng dựa trên các cấu trúc container của JavaFX:

- **Root:** `BorderPane`
  - **Top:** MenuBar chuẩn hệ điều hành + Toolbar thao tác nhanh (Mở thư mục bài làm, Nút Chấm tất cả, Xuất Excel).
  - **Center:** `SplitPane` (Hướng ngang, tỉ lệ 25% - 50% - 25%):
    - **Panel Trái (VBox/TreeView):** Cây thư mục danh sách thí sinh và bài tập (.java, .cpp, .py).
    - **Panel Giữa (VBox):** `CodeArea` xem mã nguồn + số dòng + highlight lỗi biên dịch.
    - **Panel Phải (TabPane):** Bảng tổng hợp điểm, danh sách testcase (Pass/Fail) và chi tiết Diff.
  - **Bottom:** `HBox` Status bar (Hiển thị `ProgressBar` tiến độ chấm, tổng số bài, trạng thái máy chấm).

## Color Palette & JavaFX CSS Mapping

| Mục đích | Mã màu HEX | Thuộc tính JavaFX CSS tương ứng |
|---|---|---|
| Nền ứng dụng chính | `#1E1E1E` | `-fx-background-color: #1E1E1E;` |
| Nền bảng & Card | `#252526` | `-fx-background-color: #252526;` |
| Khung viền phân cách | `#333333` | `-fx-border-color: #333333;` |
| Text chính | `#CCCCCC` | `-fx-text-fill: #CCCCCC;` |
| Text mờ / Chú thích | `#858585` | `-fx-text-fill: #858585;` |
| Accepted (Đúng) | `#2ECC71` | `-fx-text-fill: #2ECC71;` |
| Wrong Answer (Sai) | `#E74C3C` | `-fx-text-fill: #E74C3C;` |
| TLE / MLE (Quá giới hạn) | `#E67E22` | `-fx-text-fill: #E67E22;` |
| Compilation Error (Lỗi dịch) | `#9B59B6` | `-fx-text-fill: #9B59B6;` |

## Typography

- **Code Editor & Log Output:** `JetBrains Mono`, `Consolas` — 13px. Tối ưu cho việc so khớp từng ký tự whitespace, tabs, và dấu câu.
- **UI & Table Headers:** `Segoe UI`, `Inter` — 13px (cỡ chữ chuẩn), 11px (thẻ trạng thái).

## Components Specification

### 1. Primary Action Button (Chấm bài / Re-grade)
- Background: `#007ACC`
- Text color: `#FFFFFF`, `-fx-font-weight: bold;`
- Radius: `4px`
- Hover state: Chuyển sang `#1A8AD4` (thời gian phản hồi < 100ms).

### 2. Testcase Result Pill (Nhãn kết quả)
- Bo góc: `4px`, padding `2px 6px`.
- Nếu Passed: Nền `rgba(46, 204, 113, 0.15)`, chữ `#2ECC71`, viền `1px solid #2ECC71`.
- Nếu Failed: Nền `rgba(231, 76, 60, 0.15)`, chữ `#E74C3C`, viền `1px solid #E74C3C`.

### 3. Diff Viewer (So sánh Output thực tế vs Output mẫu)
- Chia đôi màn hình ngang (`HBox` chứa 2 `TextArea` không cho sửa `editable="false"`).
- Dòng lệch được đánh dấu nền đỏ nhạt (`-fx-background-color: #3C1F1F;`).

### 4. Progress Tracking
- Sử dụng JavaFX `ProgressBar` liên kết với `Task<Void>` trong Java đa luồng.
- Chiều cao: `16px`, bo góc nhẹ. Khi đang chạy có chỉ số test hiện tại: `[==========>     ] 32/50 Testcases`.

### 5. Header Avatar & Profile Popup (Quy chuẩn định danh cá nhân)
- **Avatar Button**:
  - Kích thước: `32x32px` cố định, bo góc tròn 50% (`-fx-background-radius: 50%; -fx-border-radius: 50%;`).
  - Nền: `-accent` (`#007ACC`) với chữ viết tắt màu trắng (`#FFFFFF`), `-fx-font-weight: bold; -fx-font-size: 13px;`.
  - Hiệu ứng tương tác: Hover chuyển nền sang `-accent-hover` (`#1A8AD4`), con trỏ dạng bàn tay (`hand`).
- **Profile Popup / Modal Panel**:
  - Kích thước chuẩn: `280px` - `320px`, nền `-bg-surface` (`#252526`), viền `1px solid -border-subtle` (`#333333`), bo góc `6px`.
  - Padding: `16px`, spacing giữa các khối `10px`.
  - Khối nội dung bao gồm:
    1. Avatar cỡ lớn (`48x48px`) kèm Họ và tên in đậm (`15px`, `#FFFFFF`).
    2. Thông tin chi tiết dạng Meta Label (`12px`, `#858585`): Tên đăng nhập (username), Vai trò (badge role), Lớp tín chỉ (nếu là Học sinh).
    3. Đường kẻ phân cách mỏng (`Separator`).
    4. Nút Đăng xuất (`.btn-secondary` hoặc `.btn-danger-outline` với chữ `#F85149`, hover nền `#3C1F1F`).

### 6. Course & Section Selector Dropdown (Bộ chọn Môn học & Lớp tín chỉ)
- **ComboBox Lớp học của Sinh viên**:
  - Chiều cao: `32px`, font `13px`, padding `4px 8px`.
  - Nền `-bg-surface` (`#252526`), viền `1px solid -border-subtle` (`#333333`), bo góc `4px`.
  - Luôn đi kèm nhãn chú thích "Lớp tín chỉ: " rõ ràng ở bên cạnh.
  - Khi người dùng thay đổi lựa chọn lớp, hệ thống tự động làm mới bảng bài tập tương ứng của lớp đó.

### 7. Admin Management Portal (Quy chuẩn trang Quản trị Giáo viên)
- **Cấu trúc Tab / Sidebar Điều hướng**:
  - Phong cách Flat IDE: Sử dụng `TabPane` hoặc Button Bar phẳng, không bo góc dạng bong bóng.
  - Tab đang chọn (Active): Viền dưới `2px solid #007ACC`, chữ sáng `-text-bright` (`#FFFFFF`), chữ in đậm.
  - Tab không chọn: Chữ `-text-secondary` (`#858585`), hover chuyển `-bg-hover` (`#2A2D2E`).
- **4 Phân hệ Bắt buộc**:
  1. *Môn học (Courses)*: Quản lý danh mục ngôn ngữ và môn giảng dạy (C++, Python, Java,...).
  2. *Lớp tín chỉ (Classes)*: Quản lý các lớp học phần, liên kết môn học và giáo viên.
  3. *Sinh viên (Students)*: Xem danh sách sinh viên theo lớp tín chỉ; công cụ cấp tài khoản qua file data.
  4. *Bài tập theo lớp (Problems)*: Quản lý bài tập riêng biệt theo từng lớp tín chỉ.
- **Component Nạp Dữ liệu Sinh viên từ File (Data Import Component)**:
  - Hỗ trợ định dạng file: `.csv`, `.txt` dạng bảng dữ liệu (`username,password,fullName`).
  - Hộp dropzone/nút chọn file: Viền nét đứt (`dashed 1px #444444`), nền `#1E1E1E`.
  - Bảng xem trước dữ liệu (Preview Table) trước khi gửi xác nhận import lên Server.
  - Hiển thị nhãn cảnh báo rõ ràng nếu có dòng dữ liệu sai định dạng.

### 8. Dynamic Testcase Manager (Quản lý Testcase Động trong Hộp thoại Bài tập)
- **Cấu trúc danh sách Testcase linh hoạt**:
  - Hộp thoại tạo bài tập của Giáo viên cung cấp nút bấm `+ Thêm Testcase` cho phép bổ sung không giới hạn số lượng testcase chấm.
  - Mỗi thẻ Testcase (Testcase Card) bao gồm:
    1. Tiêu đề chỉ mục rõ ràng (`Testcase #1`, `Testcase #2`...).
    2. Checkbox chỉ định: `Là Testcase ví dụ (Sample - Hiển thị trong đề bài)`.
    3. Cặp ô nhập `Input` và `Expected Output` sử dụng font Monospace (`JetBrains Mono` / `Consolas`), hỗ trợ nhiều dòng dữ liệu.
    4. Nút bấm `Xóa testcase` dạng icon hoặc text phụ (`.btn-danger-outline` hoặc text đỏ mờ) để loại bỏ testcase thừa.
  - Tối thiểu luôn có 1 testcase trước khi gửi lên Server.

### 9. Student Unified Header Bar & Submission History Modal
- **Thanh Header Tích Hợp Đa Năng của Sinh viên (Student Unified Header)**:
  - Toàn bộ các công cụ điều hướng, tìm kiếm và lọc dữ liệu được gom trực tiếp lên Header thanh trên cùng:
    1. *Logo & Tiêu đề hệ thống*: Nhãn thương hiệu ngắn gọn.
    2. *Bộ chọn Lớp tín chỉ (Class Selector)*: ComboBox phẳng chọn lớp học phần.
    3. *Bộ lọc Độ khó (Difficulty Filter)*: ComboBox lọc EASY, MEDIUM, HARD.
    4. *Ô Tìm kiếm bài tập (Problem Search Field)*: TextField tìm nhanh theo tên hoặc ID bài tập.
    5. *Nút Lịch sử nộp bài (Submission History Button)*: Nút bấm trực quan (`📜 Lịch sử nộp bài`), click mở Modal xem toàn bộ các lần nộp trong quá khứ.
    6. *Nút Chuyển đổi giao diện (Theme Toggle)* và *Avatar Profile Button*.
  - Loại bỏ hoàn toàn nút "Làm mới" dư thừa trên giao diện (hệ thống tự động đồng bộ khi chuyển lớp hoặc lọc).
- **Hộp thoại / Modal Lịch sử Nộp bài (Submission History Modal)**:
  - Kích thước: `850px x 550px`, nền `-bg-surface` (`#252526`), viền `-border-subtle` (`#333333`).
  - Hiển thị toàn bộ lịch sử nộp bài từ trước đến nay của sinh viên dưới dạng `TableView`:
    - Cột: Mã nộp (#ID), Bài tập, Ngôn ngữ, Kết quả (dùng các pill badge màu chuẩn: `AC`, `WA`, `TLE`, `CE`), Điểm / Số test pass, Thời gian chạy, Dung lượng bộ nhớ, Ngày nộp.
    - Cho phép nhấp chọn hàng để xem mã nguồn (source code) đã nộp trong quá khứ.

### 10. Student Change Password Feature (Chức năng Đổi Mật Khẩu Sinh Viên)
- Tích hợp trực tiếp nút `🔒 Đổi mật khẩu` bên trong Profile Popup khi click vào Avatar.
- **Change Password Dialog**:
  - Giao diện modal phẳng chuẩn: Ô nhập Mật khẩu hiện tại, Mật khẩu mới, Xác nhận mật khẩu mới.
  - Kiểm tra điều kiện bảo mật: Mật khẩu mới tối thiểu 6 ký tự, trùng khớp xác nhận.
  - Phản hồi trạng thái ngay lập tức trên hộp thoại (hiển thị lỗi đỏ nếu mật khẩu cũ sai, thông báo xanh khi đổi thành công).

### 11. Problem Visibility Checkbox Column (Cột Checkbox Quản lý Trạng Thái Hiển Thị Từng Bài Tập)
- **Vị trí**: Đặt trực tiếp làm một cột trong bảng `problemTable` tại Tab "Bài tập theo lớp" của Giáo viên (Admin Problems Tab).
- **Quy chuẩn Cột & Cell Component**:
  - Tiêu đề cột: `Trạng thái` — Chiều rộng cố định `130px`, căn giữa (`Pos.CENTER`).
  - Mỗi ô trong cột chứa một `CheckBox`:
    - Khi Check (Checked): Nhãn chữ `[x] Đang mở`, màu xanh `-status-ac` (`#2ECC71`), in đậm, con trỏ dạng `hand`. Bài tập này được hiển thị công khai cho sinh viên làm bài.
    - Khi Uncheck (Unchecked): Nhãn chữ `[ ] Đã ẩn`, màu xám `-text-secondary` (`#858585`), con trỏ dạng `hand`. Bài tập này bị ẩn hoàn toàn đối với tài khoản sinh viên (sinh viên không nhìn thấy trên Dashboard).
- **Cơ chế hoạt động**:
  - Giáo viên click trực tiếp vào ô vuông checkbox của bài tập bất kỳ:
    - Ứng dụng lập tức gửi message socket `TOGGLE_PROBLEM_ACTIVE` với `problemId` và `active` (true/false) lên Server để cập nhật tức thời vào Database PostgreSQL.
    - Không cần bấm thêm nút Lưu hay reload lại trang.
  - Phía sinh viên (Student View): Khi gọi `GET_PROBLEMS`, Server tự động lọc điều kiện `is_active = TRUE`, chỉ trả về các bài tập được giáo viên tick mở. Giáo viên vẫn nhìn thấy toàn bộ mọi bài tập để thuận tiện quản lý.

### 12. Student Management Action Column & Edit Dialog (Cột Thao Tác & Hộp Thoại Sửa/Xóa Sinh Viên)
- **Cột Thao tác trong Bảng Sinh viên (`studentTable`)**:
  - Tiêu đề: `Thao tác` — Chiều rộng `130px - 160px`, căn giữa.
  - Chứa cặp nút bấm văn bản tinh gọn (không emoji):
    - `Sửa`: Style `.btn-secondary`, padding `3px 12px`, font `11px`. Click mở hộp thoại `EditStudentDialog`.
    - `Xóa`: Style `.btn-danger-outline` (`-fx-text-fill: #F85149; -fx-border-color: #F85149;`), padding `3px 12px`, font `11px`. Click hiển thị xác nhận `Confirmation Alert` trước khi thực hiện xóa.
- **Hộp thoại Chỉnh Sửa Sinh Viên (`EditStudentDialog`)**:
  - Kích thước: `440px`, nền `-bg-surface` (`#252526`), viền `-border-subtle` (`#333333`).
  - Trường chỉnh sửa:
    1. Tên đăng nhập (Username): Chỉ đọc (disabled/opacity `0.7`) để định danh.
    2. Họ và tên: Ô nhập text cho phép cập nhật.
    3. Lớp tín chỉ: `ComboBox` chọn lớp muốn chuyển đến.
    4. Mật khẩu mới: Ô nhập dạng mật khẩu (bỏ trống nếu giữ nguyên, nhập nếu giáo viên muốn đặt lại mật khẩu cho sinh viên).

### 13. Minimalist Table Pagination (Thanh Phân Trang Bảng 15 Đối Tượng / Trang)
- **Quy chuẩn Phân trang**:
  - Định mức phân trang: Cố định `15 đối tượng / 1 trang` cho cả Bảng Sinh viên và Bảng Bài tập.
  - Vị trí: Đặt ngay phía dưới bảng tương ứng, căn lề phải (`Pos.CENTER_RIGHT`), padding top `6px`.
  - Thành phần thanh điều hướng:
    1. Nhãn trạng thái: `Trang X / Y (Tổng số: N đối tượng)` — Màu `-text-secondary` (`#858585`), font `12px`.
    2. Nút bấm `◀ Trang trước` và `Trang sau ▶`: Nút phẳng `.btn-secondary`, font `11px`, padding `4px 10px`.
    3. Trạng thái vô hiệu hóa (Disabled state): Mờ 50% khi đang ở trang đầu tiên hoặc trang cuối cùng.

### 14. Credit Class Action Column & Edit Dialog (Cột Thao Tác & Hộp Thoại Sửa/Xóa Lớp Tín Chỉ)
- **Cột Thao tác trong Bảng Lớp tín chỉ (`classTable`)**:
  - Tiêu đề: `Thao tác` — Chiều rộng `130px - 160px`, căn giữa.
  - Chứa cặp nút bấm văn bản tinh gọn (không emoji):
    - `Sửa`: Style `.btn-secondary`, padding `3px 12px`, font `11px`. Click mở `TeacherClassDialog` ở chế độ chỉnh sửa (điền sẵn thông tin mã lớp, tên lớp, chọn môn học tương ứng).
    - `Xóa`: Style `.btn-danger-outline` (`-fx-text-fill: #F85149; -fx-border-color: #F85149;`), padding `3px 12px`, font `11px`. Click hiển thị xác nhận `Confirmation Alert` (cảnh báo sinh viên trong lớp sẽ trở về trạng thái Chưa gán lớp và bài tập của lớp sẽ chuyển thành bài tập chung).
- **Cơ chế an toàn phía Database**:
  - Sử dụng Transaction để cập nhật: chuyển `users.class_id` thành `NULL`, chuyển `problems.class_id` thành `NULL`, sau đó mới xóa bản ghi trong bảng `classes` để bảo toàn dữ liệu tài khoản và bài tập.

### 15. Responsive Window & Table Fitting (Bố Cục Co Giãn Vừa Khít Cửa Sổ)
- **TableView Constrained Resize Policy**:
  - Mọi bảng dữ liệu (`courseTable`, `classTable`, `studentTable`, `problemTable`, `DashboardView.table`) đều áp dụng `TableView.CONSTRAINED_RESIZE_POLICY`.
  - Cột ID và các cột Thao tác, Trạng thái được gán giới hạn `minWidth` và `maxWidth` vừa vặn; các cột nội dung (Tên môn, Tên lớp, Họ tên sinh viên, Tiêu đề bài tập) tự động co giãn lấp đầy 100% không gian cửa sổ, loại bỏ hoàn toàn khoảng trống đen/xám thừa ở cạnh phải.
  - Kích thước cửa sổ tự động thích ứng với độ rộng tối ưu (1200x750) và căn giữa màn hình.

## Do's and Don'ts

- **Do:** Dùng đa luồng (`javafx.concurrent.Task` hoặc `Service`) khi chấm code để tránh đơ giao diện UI (freeze thread).
- **Do:** Tự động đồng bộ và nạp lại dữ liệu sau các thao tác Sửa, Xóa, Tạo mới thay vì bắt người dùng bấm nút Làm mới.
- **Do:** Phân trang danh sách lớn với giới hạn 15 đối tượng/trang để bảng gọn gàng, tăng tốc độ render.
- **Do:** Dùng `SplitPane` để giảng viên/người chấm tự co giãn không gian code theo kích thước màn hình.
- **Do:** Sử dụng font Monospace cho cả vùng log console và diff output.
- **Do:** Tuyệt đối tuân thủ bảng màu Tokens (Zero gradients, Zero drop shadows, Zero neon glow).
- **Don't:** Không đặt nút "Làm mới" thủ công dư thừa trên thanh công cụ của Giáo viên.
- **Don't:** Không dùng hiệu ứng chuyển cảnh chậm (fade-in, slide-in) cản trở tốc độ duyệt bài.
- **Don't:** Không dùng font chữ uốn lượn hoặc bo tròn góc quá lớn (> 8px) làm mất tính chuyên nghiệp của công cụ kỹ thuật.
- **Don't:** Không gán cứng kích thước cố định (`setPrefWidth`/`setPrefHeight`) khiến giao diện vỡ khi resize cửa sổ.
- **Don't:** Không để sót tính năng tài khoản dùng thử/khách offline trong sản phẩm chính thức.