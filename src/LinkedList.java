/** Singly linked list with head, tail and size; indexed access starts at head. */
public final class LinkedList implements IntSequence {
    private static final class Node {
        final int value;
        Node next;
        Node(int value) { this.value = value; }
    }
    private Node head;
    private Node tail;
    private int size;
    private final Metrics metrics = new Metrics();

    public int size() { return size; }
    public Metrics metrics() { return metrics; }

    public void add(int value) {
        Node node = new Node(value);
        if (tail == null) head = node;
        else { metrics.accesses++; tail.next = node; }
        tail = node;
        size++;
    }

    public void add(int index, int value) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException();
        if (index == size) { add(value); return; }
        Node node = new Node(value);
        if (index == 0) { node.next = head; head = node; }
        else {
            Node previous = nodeAt(index - 1);
            node.next = previous.next;
            previous.next = node;
        }
        size++;
    }

    public int remove(int index) {
        checkIndex(index);
        Node removed;
        if (index == 0) {
            removed = head;
            head = head.next;
        } else {
            Node previous = nodeAt(index - 1);
            removed = previous.next;
            previous.next = removed.next;
            if (removed == tail) tail = previous;
        }
        metrics.accesses++; // Visit the removed node, in addition to its predecessors.
        size--;
        if (size == 0) tail = null;
        return removed.value;
    }

    public int get(int index) {
        checkIndex(index);
        return nodeAt(index).value;
    }

    private Node nodeAt(int index) {
        Node current = head;
        metrics.accesses++;
        for (int i = 0; i < index; i++) {
            current = current.next;
            metrics.accesses++;
        }
        return current;
    }

    public boolean contains(int value) {
        for (Node current = head; current != null; current = current.next) {
            metrics.accesses++;
            metrics.comparisons++;
            if (current.value == value) return true;
        }
        return false;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
    }
}
