public class Main {
    static class AppException extends Exception {
        final int code;

        AppException(String message, int code) {
            super(message);
            this.code = code;
        }
    }

    static int depth(int n) throws AppException {
        if (n == 0) {
            throw new AppException("bottom reached", 42);
        }
        try {
            return depth(n - 1);
        } finally {
            System.out.println("unwinding " + n);
        }
    }

    public static void main(String[] args) {
        try {
            depth(3);
        } catch (AppException e) {
            System.out.println("caught " + e.getMessage() + " code=" + e.code);
        }

        for (int i = 0; i < 3; ++i) {
            try {
                switch (i) {
                    case 0 -> throw new IllegalArgumentException("bad argument");
                    case 1 -> throw new UnsupportedOperationException("unsupported");
                    default -> System.out.println("no exception");
                }
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        try {
            Object s = "string";
            Integer n = (Integer) s;
            System.out.println(n);
        } catch (ClassCastException e) {
            System.out.println("ClassCastException");
        }

        try {
            String s = null;
            s.length();
        } catch (NullPointerException e) {
            System.out.println("NullPointerException");
        }

        try (var resource = new AutoCloseable() {
            @Override
            public void close() {
                System.out.println("resource closed");
            }
        }) {
            System.out.println("using resource");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
