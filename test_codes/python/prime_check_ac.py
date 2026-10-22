import sys

"""
Bài 3: Kiểm tra số nguyên tố
Nhập vào số nguyên dương N, in ra "YES" hoặc "NO"
Kết quả mong đợi: ACCEPTED (AC)
"""
def is_prime(n: int) -> bool:
    if n < 2:
        return False
    if n in (2, 3):
        return True
    if n % 2 == 0 or n % 3 == 0:
        return False
    i = 5
    while i * i <= n:
        if n % i == 0 or n % (i + 2) == 0:
            return False
        i += 6
    return True

def main():
    raw = sys.stdin.read().strip()
    if raw:
        n = int(raw)
        print("YES" if is_prime(n) else "NO")

if __name__ == '__main__':
    main()
