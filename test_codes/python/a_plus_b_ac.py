import sys

"""
Bài 2: Tổng hai số (A + B Problem)
Nhập vào hai số nguyên a và b, in ra a + b
Kết quả mong đợi: ACCEPTED (AC)
"""
def main():
    parts = sys.stdin.read().split()
    if len(parts) >= 2:
        a = int(parts[0])
        b = int(parts[1])
        print(a + b)

if __name__ == '__main__':
    main()
