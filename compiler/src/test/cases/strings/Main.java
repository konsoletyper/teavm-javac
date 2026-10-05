public class Main {
    public static void main(String[] args) {
        int i = 42;
        long l = 1234567890123L;
        double d = 3.25;
        char c = 'x';
        boolean b = true;
        Object nil = null;
        System.out.println("concat: " + i + " " + l + " " + d + " " + c + " " + b + " " + nil);
        System.out.println(String.format("%5d|%-5s|%x|%c", 42, "ab", 255, 'z'));
        System.out.println(String.join("-", "a", "b", "c"));
        System.out.println("Hello".repeat(2) + " " + "  trim me  ".strip() + " " + "MiXeD".toLowerCase());
        System.out.println("a,b,,c".split(",").length + " " + "hello world".indexOf("world"));
        System.out.println(new StringBuilder("abc").reverse().insert(0, '>').append(123));
        System.out.println(Integer.parseInt("-123") + Integer.valueOf(23) + " " + Double.parseDouble("1.5e2"));
        System.out.println(Integer.toBinaryString(10) + " " + Integer.toHexString(-1) + " " + Long.MAX_VALUE);
        System.out.println(Character.isDigit('5') + " " + Character.toUpperCase('q') + " " + (int) 'A');
        System.out.println("compare: " + "apple".compareTo("banana") + " " + "x".equals("x"));
        System.out.println(String.valueOf(new char[] { 'o', 'k' }) + " " + "chars".chars().sum());
        System.out.println(0.5 + " " + 100.0 + " " + 1e20 + " " + Double.NaN + " " + (float) 2.5);
    }
}
