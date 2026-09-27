/** A growable primitive-int array with doubling and no automatic shrinking. */
public final class DynamicArray implements IntSequence {
    private int[] elements = new int[8];
    private int size;
    private final Metrics metrics = new Metrics();

    public int size() { return size; }
    public Metrics metrics() { return metrics; }

    public void add(int value) { add(size, value); }

    public void add(int index, int value) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException();
        ensureCapacity();
        for (int j = size; j > index; j--) {
            elements[j] = elements[j - 1];
            metrics.accesses++;
            metrics.movements++;
        }
        elements[index] = value;
        size++;
    }

    private void ensureCapacity() {
        if (size < elements.length) return;
        int[] grown = new int[Math.multiplyExact(elements.length, 2)];
        for (int i = 0; i < size; i++) {
            grown[i] = elements[i];
            metrics.accesses++;
            metrics.movements++;
        }
        elements = grown;
    }

    public int remove(int index) {
        checkIndex(index);
        int result = elements[index];
        metrics.accesses++;
        for (int j = index; j < size - 1; j++) {
            elements[j] = elements[j + 1];
            metrics.accesses++;
            metrics.movements++;
        }
        elements[--size] = 0;
        return result;
    }

    public int get(int index) {
        checkIndex(index);
        metrics.accesses++;
        return elements[index];
    }

    public boolean contains(int value) {
        for (int i = 0; i < size; i++) {
            metrics.accesses++;
            metrics.comparisons++;
            if (elements[i] == value) return true;
        }
        return false;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
    }
}
