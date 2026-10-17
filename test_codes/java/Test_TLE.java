/**
 * File test phán quyết Time Limit Exceeded (TLE)
 * Chạy vòng lặp vô tận để kiểm tra watchdog ngắt tiến trình đúng giới hạn time limit.
 */
public class Test_TLE {
    public static void main(String[] args) {
        long sum = 0;
        while (true) {
            sum++;
            if (sum % 100000000L == 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {}
            }
        }
    }
}
