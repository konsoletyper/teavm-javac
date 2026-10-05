import java.util.ArrayList;
import java.util.List;

// Methods declared in TeaVM classlib under a different name, with @Rename annotation
public class Main {
    static class MyException extends RuntimeException {
        MyException(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String getMessage() {
            return "custom:" + super.getMessage();
        }
    }

    public static void main(String[] args) {
        Throwable t = new MyException("boom", new IllegalStateException("inner"));
        System.out.println(t.getMessage());
        System.out.println(t.getLocalizedMessage());
        System.out.println(t.getCause().getMessage());
        System.out.println(t.getSuppressed().length);
        System.out.println(t.getStackTrace() != null);
        System.out.println(new RuntimeException("plain"));

        Object o = new Object();
        System.out.println(o.equals(o) + " " + o.equals("x"));
        System.out.println(List.of(1, 2).equals(new ArrayList<>(List.of(1, 2))));

        var list = new ArrayList<>(List.of(1, 2, 3));
        Object copyObject = list.clone();
        var copy = (ArrayList<?>) copyObject;
        System.out.println(list + " " + copy + " " + (list != copy));

        synchronized (o) {
            o.notify();
            o.notifyAll();
        }
        System.out.println("done");
    }
}
