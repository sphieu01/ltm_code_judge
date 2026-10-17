import java.util.Scanner;

/**
 * File test phán quyết Wrong Answer (WA)
 * Cố tình in ra đáp án sai để kiểm tra hệ thống phát hiện WA.
 */
public class Test_WA {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        if (sc.hasNext()) {
            sc.next();
        }
        System.out.println("WRONG_ANSWER_999999");
        sc.close();
    }
}
