/**
 * File test phán quyết Compile Error (CE)
 * Cố ý chứa lỗi cú pháp không hợp lệ để kiểm tra hệ thống trả về CE kèm log lỗi biên dịch.
 */
public class Test_CE {
    public static void main(String[] args) {
        // Cố tình thiếu dấu ngoặc và cú pháp lỗi
        int x = 100
        System.out.println(this_is_an_undefined_variable)
    }
}
