import sys

"""
Bài 1 (Offline): GẤP ĐÔI
Nhập vào số tự nhiên N, in ra N * 2
Kết quả mong đợi: ACCEPTED (AC)
"""
def main():
    raw = sys.stdin.read().strip()
    if raw:
        n = int(raw)
        print(n * 2)

if __name__ == '__main__':
    main()
