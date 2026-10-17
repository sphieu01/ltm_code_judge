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

## Do's and Don'ts

- **Do:** Dùng đa luồng (`javafx.concurrent.Task` hoặc `Service`) khi chấm code để tránh đơ giao diện UI (freeze thread).
- **Do:** Dùng `SplitPane` để giảng viên/người chấm tự co giãn không gian code theo kích thước màn hình.
- **Do:** Sử dụng font Monospace cho cả vùng log console và diff output.
- **Don't:** Không dùng hiệu ứng chuyển cảnh chậm (fade-in, slide-in) cản trở tốc độ duyệt bài.
- **Don't:** Không dùng font chữ uốn lượn hoặc bo tròn góc quá lớn (> 8px) làm mất tính chuyên nghiệp của công cụ kỹ thuật.
- **Don't:** Không gán cứng kích thước cố định (`setPrefWidth`/`setPrefHeight`) khiến giao diện vỡ khi resize cửa sổ.