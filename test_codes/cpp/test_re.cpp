#include <iostream>

using namespace std;

/**
 * File test phán quyết Runtime Error (RE)
 * Gây crash tiến trình khi đang thực thi (chia cho 0).
 */
int main() {
    volatile int a = 100;
    volatile int b = 0;
    cout << (a / b) << endl;
    return 0;
}
