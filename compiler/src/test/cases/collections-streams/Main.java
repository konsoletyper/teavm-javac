import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    public static void main(String[] args) {
        var words = List.of("banana", "apple", "cherry", "date", "fig", "apple", "grape");

        var sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparing(String::length).thenComparing(Comparator.reverseOrder()));
        System.out.println(sorted);

        System.out.println(new TreeSet<>(words));

        Map<Integer, List<String>> byLength = words.stream()
                .distinct()
                .collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.toList()));
        System.out.println(byLength);

        var counts = new LinkedHashMap<String, Integer>();
        for (var word : words) {
            counts.merge(word, 1, Integer::sum);
        }
        System.out.println(counts);

        System.out.println(IntStream.rangeClosed(1, 10).filter(i -> i % 2 == 0).map(i -> i * i).sum());
        System.out.println(words.stream().map(String::toUpperCase).collect(Collectors.joining(", ", "[", "]")));
        System.out.println(words.stream().anyMatch(w -> w.startsWith("c")));
        var stats = words.stream().mapToInt(String::length).summaryStatistics();
        System.out.println(stats.getMin() + " " + stats.getMax() + " " + stats.getSum() + " " + stats.getCount());

        var deque = new ArrayDeque<Integer>();
        deque.push(1);
        deque.addLast(2);
        deque.addFirst(0);
        System.out.println(deque + " " + deque.pollLast() + " " + deque.peekFirst());
    }
}
