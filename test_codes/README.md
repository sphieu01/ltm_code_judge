# 🧪 Thư Viện File Test Chấm Mã Nguồn (Java, C++, Python)

Thư mục này chứa đầy đủ các file code mẫu cho 3 ngôn ngữ (**Java**, **C++**, **Python**) phục vụ việc kiểm thử hệ thống chấm tự động (Auto Grader).

---

## 📂 Cấu trúc thư mục

```
test_codes/
├── java/
│   ├── GapDoi_AC.java        # AC Bài 1 (Offline: GẤP ĐÔI)
│   ├── HelloWorld_AC.java    # AC Bài 1 (Online: Hello World)
│   ├── APlusB_AC.java        # AC Bài 2 (Tổng hai số / A + B)
│   ├── PrimeCheck_AC.java    # AC Bài 3 (Kiểm tra số nguyên tố)
│   ├── Test_WA.java          # Test kết quả SAI (Wrong Answer)
│   ├── Test_TLE.java         # Test quá thời gian (Time Limit Exceeded)
│   ├── Test_CE.java          # Test lỗi biên dịch (Compile Error)
│   └── Test_RE.java          # Test lỗi thực thi (Runtime Error / Chia cho 0)
│
├── cpp/
│   ├── gap_doi_ac.cpp        # AC Bài 1 (Offline: GẤP ĐÔI)
│   ├── hello_world_ac.cpp    # AC Bài 1 (Online: Hello World)
│   ├── a_plus_b_ac.cpp       # AC Bài 2 (Tổng hai số / A + B)
│   ├── prime_check_ac.cpp    # AC Bài 3 (Kiểm tra số nguyên tố)
│   ├── test_wa.cpp           # Test kết quả SAI (Wrong Answer)
│   ├── test_tle.cpp          # Test quá thời gian (Time Limit Exceeded)
│   ├── test_ce.cpp           # Test lỗi biên dịch (Compile Error)
│   └── test_re.cpp           # Test lỗi thực thi (Runtime Error / Chia cho 0)
│
└── python/
    ├── gap_doi_ac.py         # AC Bài 1 (Offline: GẤP ĐÔI)
    ├── hello_world_ac.py     # AC Bài 1 (Online: Hello World)
    ├── a_plus_b_ac.py        # AC Bài 2 (Tổng hai số / A + B)
    ├── prime_check_ac.py     # AC Bài 3 (Kiểm tra số nguyên tố)
    ├── test_wa.py            # Test kết quả SAI (Wrong Answer)
    ├── test_tle.py           # Test quá thời gian (Time Limit Exceeded)
    ├── test_ce.py            # Test lỗi biên dịch / Syntax Error (Compile Error)
    └── test_re.py            # Test lỗi thực thi (Runtime Error / ZeroDivisionError)
```

---

## 🎯 Bảng Ánh Xạ Bài Tập & Phán Quyết Kỳ Vọng

| Bài tập | File Java | File C++ | File Python | Kỳ vọng |
| :--- | :--- | :--- | :--- | :---: |
| **Bài 1 (Offline: GẤP ĐÔI)** | `GapDoi_AC.java` | `gap_doi_ac.cpp` | `gap_doi_ac.py` | **AC** |
| **Bài 1 (Online: Hello World)** | `HelloWorld_AC.java` | `hello_world_ac.cpp` | `hello_world_ac.py` | **AC** |
| **Bài 2 (Tổng hai số / A+B)** | `APlusB_AC.java` | `a_plus_b_ac.cpp` | `a_plus_b_ac.py` | **AC** |
| **Bài 3 (Số nguyên tố)** | `PrimeCheck_AC.java` | `prime_check_ac.cpp` | `prime_check_ac.py` | **AC** |
| **Bất kỳ bài nào** | `Test_WA.java` | `test_wa.cpp` | `test_wa.py` | **WA** |
| **Bất kỳ bài nào** | `Test_TLE.java` | `test_tle.cpp` | `test_tle.py` | **TLE** |
| **Bất kỳ bài nào** | `Test_CE.java` | `test_ce.cpp` | `test_ce.py` | **CE** |
| **Bất kỳ bài nào** | `Test_RE.java` | `test_re.cpp` | `test_re.py` | **RE** |

---

## 🚀 Cách Thử Nghiệm Trên Client

1. Vào giao diện **Chọn bài tập** (Dashboard).
2. Nhấn nút **Làm bài ▶** tại bài tương ứng (ví dụ: *GẤP ĐÔI* hoặc *A + B Problem*).
3. Chọn ngôn ngữ (`Java`, `C++`, `Python`) trong combobox.
4. Có 2 cách nộp:
   - **Tải file lên**: Nhấn nút `📂 Tải file lên`, duyệt đến thư mục `test_codes/<ngôn ngữ>/` và chọn file tương ứng $\rightarrow$ Hệ thống tự động nạp code vào và bạn bấm `Nộp bài`.
   - **Dán mã nguồn**: Mở file, copy toàn bộ nội dung và dán vào ô nhập liệu $\rightarrow$ Nhấn nút `Dán và Nộp bài`.
5. Quan sát:
   - Lịch sử nộp bài bên phải sẽ xuất hiện lần nộp mới.
   - Khi quay lại màn hình **Danh sách bài**, cột **Trạng thái** sẽ tự động cập nhật đúng phán quyết (`AC`, `WA`, `TLE`, `CE`,...).
