import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    static class Foo {
    }

    static class Bar extends Foo {
    }

    interface I {
    }

    static class Baz implements I {
    }

    public static void main(String[] args) {
        Object[] objects = { new Object(), "str", 42, new Foo(), new Bar(), new Baz(), new ArrayList<String>(),
                new int[3], new String[2], new Foo[1][1] };
        for (Object o : objects) {
            Class<?> cls = o.getClass();
            System.out.println(cls.getName() + " simple=" + cls.getSimpleName()
                    + " super=" + (cls.getSuperclass() != null ? cls.getSuperclass().getName() : "null")
                    + " array=" + cls.isArray()
                    + (cls.isArray() ? " component=" + cls.getComponentType().getName() : ""));
        }
        Object bar = new Bar();
        System.out.println(bar.getClass() == Bar.class);
        System.out.println(Foo.class.isInstance(bar));
        System.out.println(Foo.class.isAssignableFrom(bar.getClass()));
        System.out.println(new Foo().getClass() == new Foo().getClass());
        System.out.println(Arrays.toString(new Baz().getClass().getInterfaces()));
        System.out.println(bar.getClass());
        Runnable lambda = () -> { };
        System.out.println(lambda.getClass().getInterfaces()[0].getName());
    }
}
