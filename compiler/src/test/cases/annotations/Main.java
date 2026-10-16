import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

public class Main {
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
    @interface Marker {
        String value() default "default";

        int priority() default 0;
    }

    @FunctionalInterface
    interface Op {
        int apply(int a, int b);
    }

    @Marker(value = "annotated", priority = 1)
    static class Annotated {
        @Marker
        int field = 5;

        @Marker("method")
        @Override
        public String toString() {
            return "Annotated(" + field + ")";
        }

        @Deprecated
        int old() {
            return 42;
        }
    }

    @SafeVarargs
    static <T> List<T> listOf(T... items) {
        var result = new ArrayList<T>();
        for (var item : items) {
            result.add(item);
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        Op add = (a, b) -> a + b;
        System.out.println(add.apply(2, 3));

        var annotated = new Annotated();
        System.out.println(annotated);
        System.out.println(annotated.old());

        System.out.println(listOf("a", "b", "c"));

        Class<? extends Annotation> type = Marker.class;
        System.out.println(type.getSimpleName());
    }
}
