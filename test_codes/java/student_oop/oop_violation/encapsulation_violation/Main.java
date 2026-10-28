import java.util.Locale;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            scanner.useLocale(Locale.US);
            if (!scanner.hasNext()) {
                return;
            }

            if (scanner.hasNextInt()) {
                int n = scanner.nextInt();
                for (int i = 0; i < n; i++) {
                    Student student = new Student(scanner.next(), scanner.nextDouble());
                    student.display();
                }
            } else {
                while (scanner.hasNext()) {
                    Student student = new Student(scanner.next(), scanner.nextDouble());
                    student.display();
                }
            }
        }
    }
}
