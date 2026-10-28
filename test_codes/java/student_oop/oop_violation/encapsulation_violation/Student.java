public class Student {
    // Vi phạm tính đóng gói: để public thay vì private theo yêu cầu của oop_spec
    public String name;
    public double gpa;

    public Student(String name, double gpa) {
        this.name = name;
        this.gpa = gpa;
    }

    public void display() {
        System.out.println("Student: " + name + ", GPA: " + gpa);
    }
}
