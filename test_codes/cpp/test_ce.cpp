#include <iostream>

using namespace std;

/**
 * File test phán quyết Compile Error (CE)
 * Cố ý chứa lỗi cú pháp không thể biên dịch.
 */
int main() {
    this_is_an_intentional_compile_error :::;
    return 0;
}
