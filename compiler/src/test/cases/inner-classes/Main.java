import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class Main {
    private int counter;

    class Inner {
        void increment() {
            counter++;
        }
    }

    interface Greeter {
        String name();

        default String greet() {
            return "Hello, " + name();
        }

        static Greeter of(String name) {
            return () -> name;
        }
    }

    abstract static class Animal {
        abstract String sound();

        @Override
        public String toString() {
            return getClass().getSimpleName() + " says " + sound();
        }
    }

    static class Dog extends Animal {
        @Override
        String sound() {
            return "woof";
        }
    }

    static class Box<T extends Comparable<T>> {
        private final List<T> items = new ArrayList<>();

        void add(T item) {
            items.add(item);
        }

        T max() {
            T result = items.get(0);
            for (T item : items) {
                if (item.compareTo(result) > 0) {
                    result = item;
                }
            }
            return result;
        }
    }

    public static void main(String[] args) {
        var main = new Main();
        var inner = main.new Inner();
        inner.increment();
        inner.increment();
        System.out.println(main.counter);

        System.out.println(Greeter.of("world").greet());
        System.out.println(new Dog());
        System.out.println(new Animal() {
            @Override
            String sound() {
                return "...";
            }
        }.sound());

        var box = new Box<String>();
        box.add("pear");
        box.add("zucchini");
        box.add("apple");
        System.out.println(box.max());

        Function<Integer, Integer> twice = x -> x * 2;
        Function<Integer, Integer> plusOne = x -> x + 1;
        System.out.println(twice.andThen(plusOne).apply(5) + " " + twice.compose(plusOne).apply(5));

        int base = 10;
        Supplier<Integer> captured = () -> base + main.counter;
        System.out.println(captured.get());
    }
}
