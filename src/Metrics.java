/** Logical work counters. Reset after setup and before each workload. */
public final class Metrics {
    public long accesses;
    public long comparisons;
    public long movements;

    public void reset() {
        accesses = comparisons = movements = 0;
    }
}
