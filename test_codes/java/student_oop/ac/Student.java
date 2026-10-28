public class Student {
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
