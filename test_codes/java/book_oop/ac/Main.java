import java.nio.charset.StandardCharsets;
import java.io.PrintWriter;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
             PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            if (!scanner.hasNextLine()) return;
            int n = Integer.parseInt(scanner.nextLine().trim());
            for (int i = 0; i < n; i++) {
                String title = scanner.nextLine();
                double price = Double.parseDouble(scanner.nextLine().trim());
                Book book = new Book(title, price);
                output.println(book.toString());
            }
        }
    }
}
