#include <iostream>

using namespace std;

/**
 * Bài 1 (Offline): GẤP ĐÔI
 * Nhập vào số tự nhiên N, in ra N * 2
 * Kết quả mong đợi: ACCEPTED (AC)
 */
int main() {
    ios_base::sync_with_stdio(false);
    cin.tie(NULL);

    long long n;
    if (cin >> n) {
        cout << n * 2 << "\n";
    }
    return 0;
}
