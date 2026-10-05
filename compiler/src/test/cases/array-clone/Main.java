import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) {
        int[] array = { 1, 2, 3 };
        int[] copy = array.clone();
        copy[0] = 10;
        System.out.println(Arrays.toString(array) + " " + Arrays.toString(copy));

        var map = new HashMap<String, Integer>();
        map.put("a", 1);
        System.out.println(map.clone());

        var bits = new BitSet();
        bits.set(3);
        System.out.println(bits.clone());
    }
}
