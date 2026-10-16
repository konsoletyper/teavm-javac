import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Main {
    public static void main(String[] args) {
        Map<String, Integer> map = new TreeMap<>();
        map.put("b", 2);
        map.put("a", 1);
        map.put("c", 3);
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Map.Entry.comparingByValue((x, y) -> y - x));
        System.out.println(entries);

        Map.Entry<String, Integer> simple = new AbstractMap.SimpleEntry<>("x", 10);
        simple.setValue(20);
        System.out.println(simple);

        var immutable = new AbstractMap.SimpleImmutableEntry<>("y", 30);
        System.out.println(immutable.getKey() + " " + immutable.getValue());

        System.out.println(Map.entry("z", 40));

        var linked = new LinkedHashMap<String, Integer>();
        linked.put("q", 1);
        linked.put("p", 2);
        linked.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.println(e.getKey()));
    }
}
