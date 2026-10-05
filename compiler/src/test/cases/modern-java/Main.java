public class Main {
    sealed interface Shape permits Circle, Rectangle, Square {
    }

    record Circle(double radius) implements Shape {
    }

    record Rectangle(double width, double height) implements Shape {
    }

    record Square(double side) implements Shape {
    }

    enum Color {
        RED, GREEN, BLUE
    }

    static double area(Shape shape) {
        return switch (shape) {
            case Circle c -> 3 * c.radius() * c.radius();
            case Rectangle(double w, double h) -> w * h;
            case Square s when s.side() == 0 -> -1;
            case Square s -> s.side() * s.side();
        };
    }

    static String describe(Object o) {
        return switch (o) {
            case null -> "null";
            case Integer i when i > 10 -> "big integer " + i;
            case Integer i -> "integer " + i;
            case String s -> "string of length " + s.length();
            case Color c -> "color " + c.name().toLowerCase();
            default -> "something else";
        };
    }

    public static void main(String[] args) {
        Shape[] shapes = { new Circle(1), new Rectangle(2, 3), new Square(4), new Square(0) };
        for (var shape : shapes) {
            System.out.println(shape + " area=" + area(shape));
        }
        System.out.println(new Circle(2).equals(new Circle(2)) + " " + new Circle(2).equals(new Circle(3)));
        System.out.println(new Circle(2).hashCode() == new Circle(2).hashCode());

        for (Object o : new Object[] { 5, 15, "hello", Color.GREEN, 2.5, null }) {
            System.out.println(describe(o));
        }

        for (var color : Color.values()) {
            String text = switch (color) {
                case RED -> "warm";
                case GREEN, BLUE -> "cold";
            };
            System.out.println(color + " " + color.ordinal() + " " + text);
        }

        Object value = "pattern";
        if (value instanceof String s && s.length() > 3) {
            System.out.println("instanceof pattern: " + s);
        }

        var textBlock = """
                first line
                  second line
                third "quoted" line\
                """;
        System.out.println(textBlock);
    }
}
