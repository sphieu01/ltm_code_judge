#include <iostream>

using namespace std;

/**
 * Bài 3: Kiểm tra số nguyên tố
 * Nhập vào số nguyên dương N, in ra "YES" hoặc "NO"
 * Kết quả mong đợi: ACCEPTED (AC)
 */
bool isPrime(long long n) {
    if (n < 2) return false;
    if (n == 2 || n == 3) return true;
    if (n % 2 == 0 || n % 3 == 0) return false;
    for (long long i = 5; i * i <= n; i += 6) {
        if (n % i == 0 || n % (i + 2) == 0) return false;
    }
    return true;
}

int main() {
    ios_base::sync_with_stdio(false);
    cin.tie(NULL);

    long long n;
    if (cin >> n) {
        cout << (isPrime(n) ? "YES" : "NO") << "\n";
    }
    return 0;
}
