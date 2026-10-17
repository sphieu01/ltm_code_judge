import java.util.Scanner;

/**
 * Bài 3: Kiểm tra số nguyên tố
 * Nhập vào số nguyên dương N, in ra "YES" hoặc "NO"
 * Kết quả mong đợi: ACCEPTED (AC)
 */
public class PrimeCheck_AC {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        if (sc.hasNextLong()) {
            long n = sc.nextLong();
            System.out.println(isPrime(n) ? "YES" : "NO");
        }
        sc.close();
    }

    private static boolean isPrime(long n) {
        if (n < 2) return false;
        if (n == 2 || n == 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (long i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }
}
