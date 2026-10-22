#include <iostream>

using namespace std;

/**
 * Bài 2: Tổng hai số (A + B Problem)
 * Nhập vào hai số nguyên a và b, in ra a + b
 * Kết quả mong đợi: ACCEPTED (AC)
 */
int main() {
    ios_base::sync_with_stdio(false);
    cin.tie(NULL);

    long long a, b;
    if (cin >> a >> b) {
        cout << a + b << "\n";
    }
    return 0;
}
