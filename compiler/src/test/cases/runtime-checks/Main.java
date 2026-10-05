public class Main {
    public static void main(String[] args) {
        int[] array = new int[2];
        try {
            array[2] = 1;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("write: ArrayIndexOutOfBoundsException");
        }
        try {
            System.out.println(array[-1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("read: ArrayIndexOutOfBoundsException");
        }
        try {
            System.out.println(1 / args.length);
        } catch (ArithmeticException e) {
            System.out.println("int: ArithmeticException");
        }
        try {
            System.out.println(1L % args.length);
        } catch (ArithmeticException e) {
            System.out.println("long: ArithmeticException");
        }
    }
}
