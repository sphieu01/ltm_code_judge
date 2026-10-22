import java.util.Scanner;

/**
 * Bài 1 (Offline): GẤP ĐÔI
 * Nhập vào số tự nhiên N, in ra N * 2
 * Kết quả mong đợi: ACCEPTED (AC)
 */
public class GapDoi_AC {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        if (sc.hasNextLong()) {
            long n = sc.nextLong();
            System.out.println(n * 2);
        }
        sc.close();
    }
}
