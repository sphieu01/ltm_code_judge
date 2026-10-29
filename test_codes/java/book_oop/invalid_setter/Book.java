import java.util.Locale;

public class Book {
    private String title;
    private double price;

    public Book() {
        this("Untitled", 0.0);
    }

    public Book(String title, double price) {
        setTitle(title);
        setPrice(price);
    }

    public String getTitle() {
        return title;
    }

    public double getPrice() {
        return price;
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 60) {
            throw new IllegalArgumentException("Invalid title");
        }
        this.title = title;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Book: %s, Price: %.2f", title, price);
    }
}
