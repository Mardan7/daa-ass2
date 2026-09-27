import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Random;

/** Dependency-free tests: explicit checks work without the JVM -ea switch. */
public final class Tests {
    private static long checks;

    public static void main(String[] args) {
        sequence(new DynamicArray());
        sequence(new LinkedList());
        heap();
        counters();
        System.out.println("PASS: " + checks + " checks");
    }

    private static void sequence(IntSequence actual) {
        ArrayList<Integer> expected = new ArrayList<>();
        check(actual.size() == 0 && !actual.contains(7), "empty sequence");
        expect(IndexOutOfBoundsException.class, () -> actual.get(0));
        expect(IndexOutOfBoundsException.class, () -> actual.remove(0));
        expect(IndexOutOfBoundsException.class, () -> actual.add(-1, 1));
        expect(IndexOutOfBoundsException.class, () -> actual.add(1, 1));
        actual.add(0, 42);
        check(actual.get(0) == 42 && actual.remove(0) == 42, "singleton");
        actual.add(9); // Exercises tail reset after deleting the only node.
        actual.add(9);
        actual.add(0, Integer.MIN_VALUE);
        actual.add(actual.size(), Integer.MAX_VALUE);
        check(actual.contains(9) && actual.get(3) == Integer.MAX_VALUE, "duplicates/boundaries");
        while (actual.size() > 0) actual.remove(actual.size() - 1);
        Random random = new Random(12345);
        for (int step = 0; step < 12000; step++) {
            int value = random.nextInt(101) - 50;
            int operation = random.nextInt(5);
            if (operation == 0 || expected.isEmpty()) {
                actual.add(value); expected.add(value);
            } else if (operation == 1) {
                int index = random.nextInt(expected.size() + 1);
                actual.add(index, value); expected.add(index, value);
            } else if (operation == 2) {
                int index = random.nextInt(expected.size());
                check(actual.remove(index) == expected.remove(index), "remove differential");
            } else if (operation == 3) {
                int index = random.nextInt(expected.size());
                check(actual.get(index) == expected.get(index), "get differential");
            } else check(actual.contains(value) == expected.contains(value), "search differential");
            check(actual.size() == expected.size(), "size differential");
            if (step % 200 == 0) {
                for (int i = 0; i < expected.size(); i++) check(actual.get(i) == expected.get(i), "contents");
            }
        }
        expect(IndexOutOfBoundsException.class, () -> actual.get(-1));
        expect(IndexOutOfBoundsException.class, () -> actual.get(actual.size()));
        expect(IndexOutOfBoundsException.class, () -> actual.remove(actual.size()));
        expect(IndexOutOfBoundsException.class, () -> actual.remove(-1));
        expect(IndexOutOfBoundsException.class, () -> actual.add(actual.size() + 1, 0));
        while (actual.size() > 0) actual.remove(0);
        for (int i = 0; i < 100000; i++) actual.add(i);
        check(actual.get(0) == 0 && actual.get(50000) == 50000 && actual.get(99999) == 99999, "large input");
        check(!actual.contains(-1) && actual.contains(99999), "large search");
    }

    private static void heap() {
        MinHeap heap = new MinHeap();
        PriorityQueue<Integer> reference = new PriorityQueue<>();
        expect(NoSuchElementException.class, heap::peekMin);
        expect(NoSuchElementException.class, heap::extractMin);
        int[] special = {0, 0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 7, 7};
        for (int value : special) {
            heap.insert(value); reference.add(value);
            check(heap.isValidHeap(), "heap property after insertion");
        }
        while (!reference.isEmpty()) {
            check(heap.peekMin() == reference.peek(), "peek");
            check(heap.extractMin() == reference.remove(), "extract");
            check(heap.isValidHeap(), "heap property after extraction");
        }
        heap.insert(3);
        check(heap.extractMin() == 3 && heap.size() == 0, "heap singleton/reuse");
        Random random = new Random(42);
        for (int i = 0; i < 20000; i++) {
            if (reference.isEmpty() || random.nextBoolean()) {
                int value = random.nextInt(100);
                heap.insert(value); reference.add(value);
            } else check(heap.extractMin() == reference.remove(), "mixed heap differential");
            check(heap.isValidHeap() && heap.size() == reference.size(), "mixed heap invariant");
            if (!reference.isEmpty()) check(heap.peekMin() == reference.peek(), "mixed peek");
        }
        while (!reference.isEmpty()) check(heap.extractMin() == reference.remove(), "drain");
        for (int i = 0; i < 100000; i++) {
            int value = random.nextInt(); heap.insert(value); reference.add(value);
        }
        check(heap.isValidHeap(), "large heap property");
        int previous = Integer.MIN_VALUE;
        while (!reference.isEmpty()) {
            int value = heap.extractMin();
            check(value == reference.remove() && value >= previous, "large sorted extraction");
            previous = value;
        }
        // Monotone and equal-key adversarial inputs, checking every modification.
        for (int mode = 0; mode < 3; mode++) {
            for (int i = 0; i < 1000; i++) {
                heap.insert(mode == 0 ? i : mode == 1 ? -i : 5);
                check(heap.isValidHeap(), "adversarial insertion");
            }
            previous = Integer.MIN_VALUE;
            while (heap.size() > 0) {
                int value = heap.extractMin();
                check(value >= previous && heap.isValidHeap(), "adversarial extraction");
                previous = value;
            }
        }
    }

    private static void counters() {
        for (IntSequence sequence : new IntSequence[] {new DynamicArray(), new LinkedList()}) {
            sequence.add(1); sequence.add(2); sequence.add(3);
            sequence.metrics().reset();
            sequence.get(2);
            check(sequence.metrics().accesses == (sequence instanceof DynamicArray ? 1 : 3), "get metric");
            sequence.metrics().reset();
            sequence.contains(4);
            check(sequence.metrics().comparisons == 3, "search metric");
            sequence.metrics().reset();
            sequence.add(0, 4);
            check(sequence.metrics().movements == (sequence instanceof DynamicArray ? 3 : 0), "shift metric");
            sequence.metrics().reset();
            sequence.remove(0);
            check(sequence.metrics().accesses == (sequence instanceof DynamicArray ? 4 : 1), "remove metric");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        checks++;
        try { action.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
