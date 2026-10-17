import java.util.Scanner;

/**
 * Bài 2: Tổng hai số (A + B Problem)
 * Nhập vào 2 số nguyên a và b, in ra a + b
 * Kết quả mong đợi: ACCEPTED (AC)
 */
public class APlusB_AC {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        if (sc.hasNextLong()) {
            long a = sc.nextLong();
            long b = sc.nextLong();
            System.out.println(a + b);
        }
        sc.close();
    }
}
