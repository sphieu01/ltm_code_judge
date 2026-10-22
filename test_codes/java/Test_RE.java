/**
 * File test phán quyết Runtime Error (RE)
 * Cố ý gây ArithmeticException (chia cho 0) khi đang chạy.
 */
public class Test_RE {
    public static void main(String[] args) {
        int a = 100;
        int b = 0;
        int result = a / b;
        System.out.println("Result: " + result);
    }
}
