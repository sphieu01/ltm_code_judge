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

// VI PHẠM: Dồn class Student vào cùng 1 file Main.java (không tách ra Student.java)
class Student {
    private String name;
    private double gpa;

    public Student(String name, double gpa) {
        this.name = name;
        this.gpa = gpa;
    }

    public void display() {
        System.out.println("Student: " + name + ", GPA: " + gpa);
    }
}