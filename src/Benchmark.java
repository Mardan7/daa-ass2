import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Fixed-seed, single-threaded benchmarks; no dependencies beyond the JDK. */
public final class Benchmark {
    private static final int[] SIZES = {100, 1000, 10000, 100000};
    private static final int REPETITIONS = 5;
    private static volatile long sink;

    private static final class Result {
        String workload, structure, complexity;
        int n, m, run;
        long nanos, accesses, comparisons, movements, checksum;

        String key() { return workload + "," + structure + "," + n; }
    }

    private static Result sequence(String workload, boolean array, int[] data,
                                   int[] indices, int[] queries, int[] values) {
        IntSequence sequence = array ? new DynamicArray() : new LinkedList();
        for (int value : data) sequence.add(value);
        int n = data.length;
        int index = workload.endsWith("middle") ? n / 2 : 0;
        int m = workload.equals("access") ? indices.length
            : workload.equals("search") ? queries.length
            : workload.startsWith("remove") ? Math.min(1000, n - index) : 1000;
        sequence.metrics().reset();
        long checksum = 0;
        long start = System.nanoTime();
        if (workload.equals("access")) {
            for (int i : indices) checksum += sequence.get(i);
        } else if (workload.equals("search")) {
            for (int value : queries) if (sequence.contains(value)) checksum++;
        } else if (workload.startsWith("insert")) {
            for (int i = 0; i < m; i++) sequence.add(index, values[i]);
        } else {
            for (int i = 0; i < m; i++) checksum += sequence.remove(index);
        }
        long nanos = System.nanoTime() - start;
        // Observe structure contents after timing, preventing dead-work elimination.
        Metrics metric = sequence.metrics();
        Result result = result(workload, array ? "DynamicArray" : "LinkedList", n, m,
            nanos, metric, checksum);
        if (workload.startsWith("insert")) {
            int expectedSize = n + m;
            if (sequence.size() != expectedSize || sequence.get(index) != values[m - 1])
                throw new AssertionError("Insertion validation");
        } else if (workload.startsWith("remove") && sequence.size() != n - m) {
            throw new AssertionError("Removal validation");
        }
        sink ^= checksum + sequence.size();
        if (array) result.complexity = workload.equals("access") ? "Theta(m)"
            : workload.startsWith("insert") ? "Theta(m*(n+m))" : "Theta(m*n)";
        else result.complexity = workload.endsWith("front") ? "Theta(m)" : "Theta(m*n)";
        return result;
    }

    private static Result[] heap(int[] data) {
        MinHeap heap = new MinHeap();
        int[] extracted = new int[data.length];
        long start = System.nanoTime();
        for (int value : data) heap.insert(value);
        long nanos = System.nanoTime() - start;
        Result insertion = result("heap_insert", "MinHeap", data.length, data.length,
            nanos, heap.metrics(), heap.peekMin());
        insertion.complexity = "expected Theta(n); worst Theta(n log n)";
        if (!heap.isValidHeap()) throw new AssertionError("Heap property after build");
        heap.metrics().reset();
        start = System.nanoTime();
        for (int i = 0; i < extracted.length; i++) extracted[i] = heap.extractMin();
        nanos = System.nanoTime() - start;
        Result extraction = result("heap_extract", "MinHeap", data.length, data.length,
            nanos, heap.metrics(), 0);
        extraction.complexity = "expected/worst Theta(n log n)";
        for (int i = 0; i < extracted.length; i++) {
            if (i > 0 && extracted[i] < extracted[i - 1]) throw new AssertionError("Not sorted");
            extraction.checksum += extracted[i];
        }
        if (heap.size() != 0) throw new AssertionError("Heap not empty");
        sink ^= extraction.checksum;
        return new Result[] {insertion, extraction};
    }

    private static Result result(String workload, String structure, int n, int m,
                                 long nanos, Metrics metrics, long checksum) {
        Result result = new Result();
        result.workload = workload; result.structure = structure;
        result.n = n; result.m = m; result.nanos = nanos;
        result.accesses = metrics.accesses; result.comparisons = metrics.comparisons;
        result.movements = metrics.movements; result.checksum = checksum;
        return result;
    }

    private static List<Result> experiment(int n, int run) {
        // Pre-generated fixtures, identical for both structures and all repetitions.
        Random random = new Random(42);
        int[] data = new int[n];
        for (int i = 0; i < n; i++) data[i] = 2 * random.nextInt(1000000000);
        int[] indices = new int[10000];
        for (int i = 0; i < indices.length; i++) indices[i] = random.nextInt(n);
        int[] queries = new int[1000];
        for (int i = 0; i < queries.length; i++)
            queries[i] = i % 2 == 0 ? data[random.nextInt(n)] : 2 * random.nextInt(1000000000) + 1;
        int[] values = new int[1000];
        for (int i = 0; i < values.length; i++) values[i] = random.nextInt();
        String[] workloads = {"access", "search", "insert_front", "remove_front", "insert_middle", "remove_middle"};
        List<Result> results = new ArrayList<>();
        for (String workload : workloads) {
            // Alternate order between repeats to reduce consistent order bias.
            Result first = sequence(workload, run % 2 == 0, data, indices, queries, values);
            Result second = sequence(workload, run % 2 != 0, data, indices, queries, values);
            if (first.checksum != second.checksum) throw new AssertionError("Workload mismatch");
            results.add(first); results.add(second);
        }
        for (Result result : heap(data)) results.add(result);
        for (Result result : results) result.run = run;
        return results;
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        Path output = Paths.get("results", "tables");
        Files.createDirectories(output);
        System.out.println("Warming up all workload paths (3 unreported rounds at n=10000)...");
        for (int run = 0; run < 3; run++) experiment(10000, run);
        List<Result> all = new ArrayList<>();
        for (int n : SIZES) {
            for (int run = 1; run <= REPETITIONS; run++) all.addAll(experiment(n, run));
            System.out.println("Completed n=" + n + ", five repetitions");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("raw.csv"), StandardCharsets.UTF_8)) {
            writer.write("workload,structure,n,m,run,time_ns,accesses,comparisons,movements,checksum\n");
            for (Result r : all) writer.write(String.format("%s,%s,%d,%d,%d,%d,%d,%d,%d,%d%n",
                r.workload,r.structure,r.n,r.m,r.run,r.nanos,r.accesses,r.comparisons,r.movements,r.checksum));
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("summary.csv"), StandardCharsets.UTF_8)) {
            writer.write("workload,structure,n,m,repetitions,mean_ms,stddev_ms,min_ms,max_ms,accesses,comparisons,movements,theory\n");
            for (Result first : all) {
                if (first.run != 1) continue;
                double sum = 0, squared = 0;
                long min = Long.MAX_VALUE, max = 0;
                for (Result r : all) if (r.key().equals(first.key())) {
                    if (r.accesses != first.accesses || r.comparisons != first.comparisons
                        || r.movements != first.movements || r.checksum != first.checksum)
                        throw new AssertionError("Non-reproducible operation counts");
                    sum += r.nanos; min = Math.min(min, r.nanos); max = Math.max(max, r.nanos);
                }
                double mean = sum / REPETITIONS;
                for (Result r : all) if (r.key().equals(first.key())) squared += Math.pow(r.nanos - mean, 2);
                writer.write(String.format("%s,%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%s%n",
                    first.workload,first.structure,first.n,first.m,REPETITIONS,mean/1e6,
                    Math.sqrt(squared/(REPETITIONS-1))/1e6,min/1e6,max/1e6,
                    first.accesses,first.comparisons,first.movements,first.complexity));
            }
        }
        String environment = "Run time (UTC): " + java.time.Instant.now() + "\n"
            + "Java: " + System.getProperty("java.version") + "\n"
            + "VM: " + System.getProperty("java.vm.name") + "\n"
            + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n"
            + "Architecture: " + System.getProperty("os.arch") + "\n"
            + "Available logical processors: " + Runtime.getRuntime().availableProcessors() + "\n"
            + "CPU: " + System.getenv("PROCESSOR_IDENTIFIER") + "\n"
            + "Max JVM heap bytes: " + Runtime.getRuntime().maxMemory() + "\n"
            + "JVM arguments: " + ManagementFactory.getRuntimeMXBean().getInputArguments() + "\n"
            + "Seed: 42; measured repeats: 5; warmups: 3 at n=10000\n"
            + "Counters are enabled inside timed methods; validation and setup are outside timing.\n";
        Files.write(Paths.get("results", "environment.txt"), environment.getBytes(StandardCharsets.UTF_8));
        System.out.println("Saved raw.csv, summary.csv and environment.txt; sink=" + sink);
    }
}
