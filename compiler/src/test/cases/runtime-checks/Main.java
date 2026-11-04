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
    }
}
