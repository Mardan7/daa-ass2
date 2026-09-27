import java.util.NoSuchElementException;

/** Array-backed binary min-heap; duplicates and all int values are supported. */
public final class MinHeap {
    private int[] elements = new int[8];
    private int size;
    private final Metrics metrics = new Metrics();

    public int size() { return size; }
    public Metrics metrics() { return metrics; }

    public void insert(int value) {
        if (size == elements.length) {
            int[] grown = new int[Math.multiplyExact(elements.length, 2)];
            for (int j = 0; j < size; j++) {
                grown[j] = elements[j];
                metrics.movements++;
            }
            elements = grown;
        }
        int i = size++;
        elements[i] = value;
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (!less(elements[i], elements[parent])) break;
            swap(i, parent);
            i = parent;
        }
    }

    public int peekMin() {
        if (size == 0) throw new NoSuchElementException("Empty heap");
        return elements[0];
    }

    public int extractMin() {
        int minimum = peekMin();
        elements[0] = elements[--size];
        elements[size] = 0;
        int i = 0;
        while (i < size / 2) {
            int left = 2 * i + 1;
            int right = left + 1;
            int child = left;
            if (right < size && less(elements[right], elements[left])) child = right;
            if (!less(elements[child], elements[i])) break;
            swap(i, child);
            i = child;
        }
        return minimum;
    }

    private boolean less(int a, int b) { metrics.comparisons++; return a < b; }
    private void swap(int a, int b) {
        int temporary = elements[a];
        elements[a] = elements[b];
        elements[b] = temporary;
        metrics.movements += 2;
    }

    /** Validation only: deliberately does not change benchmark counters. */
    public boolean isValidHeap() {
        for (int i = 1; i < size; i++) {
            if (elements[(i - 1) / 2] > elements[i]) return false;
        }
        return true;
    }
}
