import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CineBook Experiment 9: MPI-style Collective Communication engine.
 *
 * Simulates an MPI communicator (COMM_WORLD) over the CineBook Booking
 * Cluster. Rank 0 is the HQ Coordinator (root); ranks 1..N-1 are regional
 * cinema Branch nodes. Each collective call spins up one real Java thread
 * per rank so Broadcast / Scatter / Gather genuinely execute concurrently —
 * mirroring how real MPI ranks operate as independent processes rather
 * than a single sequential loop.
 *
 * Supported collectives (see the mpi4py reference, mpi_collective.py):
 *   - broadcast(message) : MPI_Bcast         — root  -> all
 *   - scatter(values)    : MPI_Scatter       — root splits array, 1 chunk/rank
 *   - gather()           : MPI_Gather        — all   -> root (computed values)
 *   - reduceSum()        : MPI_Reduce(SUM)   — all   -> root (aggregated total;
 *                            Problem Statement #1 extension)
 */
public class MpiCommunicator {

    /** Rank 0 is always the HQ Coordinator; ranks 1+ are regional branches. */
    public static final String[] NODE_NAMES = {
        "HQ Coordinator", "Mumbai Branch", "Delhi Branch", "Bengaluru Branch",
        "Chennai Branch", "Hyderabad Branch", "Pune Branch", "Kolkata Branch"
    };

    private final List<MpiProcess> processes = new ArrayList<>();
    private final List<String> memoryLogs = new CopyOnWriteArrayList<>();

    private volatile String lastBroadcastMessage = null;
    private volatile List<Integer> lastScatterInput = null;
    private volatile List<Integer> lastGatherResult = null;
    private volatile Integer lastReduceSum = null;

    private final AtomicInteger broadcastCount = new AtomicInteger(0);
    private final AtomicInteger scatterCount = new AtomicInteger(0);
    private final AtomicInteger gatherCount = new AtomicInteger(0);
    private final AtomicInteger reduceCount = new AtomicInteger(0);

    public MpiCommunicator() { this(4); }

    public MpiCommunicator(int size) {
        resize(size);
    }

    /** Rebuilds COMM_WORLD with a new rank count (2..NODE_NAMES.length). */
    public synchronized void resize(int size) {
        size = Math.max(2, Math.min(size, NODE_NAMES.length));
        processes.clear();
        for (int r = 0; r < size; r++) {
            processes.add(new MpiProcess(r, NODE_NAMES[r]));
        }
        lastBroadcastMessage = null;
        lastScatterInput = null;
        lastGatherResult = null;
        lastReduceSum = null;
        broadcastCount.set(0);
        scatterCount.set(0);
        gatherCount.set(0);
        reduceCount.set(0);
        memoryLogs.clear();
        log("Comm-Init", "COMM_WORLD (re)initialized with " + size + " ranks — rank 0 = " + NODE_NAMES[0]);
    }

    public List<MpiProcess> getProcesses() { return Collections.unmodifiableList(processes); }
    public int size() { return processes.size(); }
    public List<String> getMemoryLogs() { return new ArrayList<>(memoryLogs); }

    public String getLastBroadcastMessage() { return lastBroadcastMessage; }
    public List<Integer> getLastScatterInput() { return lastScatterInput; }
    public List<Integer> getLastGatherResult() { return lastGatherResult; }
    public Integer getLastReduceSum() { return lastReduceSum; }

    public int getBroadcastCount() { return broadcastCount.get(); }
    public int getScatterCount() { return scatterCount.get(); }
    public int getGatherCount() { return gatherCount.get(); }
    public int getReduceCount() { return reduceCount.get(); }

    private void log(String who, String msg) {
        Exp9Log.log(who, msg);
        memoryLogs.add(String.format("[%s] %s", who, msg));
        while (memoryLogs.size() > 250) {
            memoryLogs.remove(0);
        }
    }

    // ── MPI_Bcast ────────────────────────────────────────────────────────
    /**
     * Root (rank 0) broadcasts {@code message} to every other rank. Each
     * rank receives the data concurrently on its own thread, exactly as
     * {@code comm.bcast(data, root=0)} fans data out to every MPI process.
     */
    public synchronized void broadcast(String message) {
        broadcastCount.incrementAndGet();
        lastBroadcastMessage = message;
        log("Rank-0 (Root)", ">>> MPI_Bcast initiated: \"" + message + "\" -> all " + (size() - 1) + " worker ranks <<<");

        List<Thread> workers = new ArrayList<>();
        for (MpiProcess p : processes) {
            Thread t = new Thread(() -> {
                if (!p.isAlive()) {
                    log("Rank-" + p.getRank(), p.getName() + " is OFFLINE — did not receive broadcast");
                    return;
                }
                simulateNetworkDelay();
                p.setReceivedBroadcast(message);
                log("Rank-" + p.getRank(), "[" + p.getName() + "] Received broadcast data: \"" + message + "\"");
            }, "mpi-bcast-rank-" + p.getRank());
            workers.add(t);
            t.start();
        }
        joinAll(workers);
    }

    // ── MPI_Scatter ──────────────────────────────────────────────────────
    /**
     * Root splits {@code values} (must have exactly {@code size()} elements
     * — one chunk per rank, including the root itself) and sends
     * {@code values[r]} to rank r.
     */
    public synchronized List<Integer> scatter(List<Integer> values) {
        if (values == null || values.size() != size()) {
            throw new IllegalArgumentException(
                "Scatter requires exactly " + size() + " values (one per rank), got " +
                (values == null ? 0 : values.size()));
        }
        scatterCount.incrementAndGet();
        lastScatterInput = new ArrayList<>(values);
        log("Rank-0 (Root)", ">>> MPI_Scatter initiated: " + values + " -> " + size() + " ranks <<<");

        List<Thread> workers = new ArrayList<>();
        for (MpiProcess p : processes) {
            final int chunk = values.get(p.getRank());
            Thread t = new Thread(() -> {
                if (!p.isAlive()) {
                    log("Rank-" + p.getRank(), p.getName() + " is OFFLINE — did not receive scatter chunk");
                    return;
                }
                simulateNetworkDelay();
                p.setScatterValue(chunk);
                log("Rank-" + p.getRank(), "[" + p.getName() + "] Received scatter value: " + chunk);
            }, "mpi-scatter-rank-" + p.getRank());
            workers.add(t);
            t.start();
        }
        joinAll(workers);
        return values;
    }

    // ── MPI_Gather ───────────────────────────────────────────────────────
    /**
     * Each rank locally computes {@code computed = scatterValue + rank}
     * (mirrors the reference program) concurrently, then all results are
     * gathered back to the root in rank order — exactly like
     * {@code comm.gather(value, root=0)}.
     */
    public synchronized List<Integer> gather() {
        gatherCount.incrementAndGet();
        log("Rank-0 (Root)", ">>> MPI_Gather initiated: collecting computed values from all ranks <<<");

        List<Thread> workers = new ArrayList<>();
        for (MpiProcess p : processes) {
            Thread t = new Thread(() -> {
                if (!p.isAlive()) {
                    log("Rank-" + p.getRank(), p.getName() + " is OFFLINE — contributed no value to gather");
                    return;
                }
                simulateNetworkDelay();
                int computed = p.compute();
                log("Rank-" + p.getRank(), "[" + p.getName() + "] Computed value (scatter + rank) = " + computed);
            }, "mpi-gather-rank-" + p.getRank());
            workers.add(t);
            t.start();
        }
        joinAll(workers);

        List<Integer> gathered = new ArrayList<>();
        for (MpiProcess p : processes) {
            gathered.add(p.isAlive() ? p.getComputedValue() : null);
        }
        lastGatherResult = gathered;
        log("Rank-0 (Root)", "[Rank 0] Gathered data from all processes: " + gathered);
        return gathered;
    }

    // ── MPI_Reduce(SUM) ──────────────────────────────────────────────────
    /** Problem Statement #1 extension: aggregate with reduce() instead of a full gather(). */
    public synchronized int reduceSum() {
        reduceCount.incrementAndGet();
        log("Rank-0 (Root)", ">>> MPI_Reduce(SUM) initiated across all ranks <<<");

        List<Thread> workers = new ArrayList<>();
        AtomicInteger sum = new AtomicInteger(0);
        for (MpiProcess p : processes) {
            Thread t = new Thread(() -> {
                if (!p.isAlive()) return;
                simulateNetworkDelay();
                int computed = p.compute();
                sum.addAndGet(computed);
                log("Rank-" + p.getRank(), "[" + p.getName() + "] contributed " + computed + " to SUM reduction");
            }, "mpi-reduce-rank-" + p.getRank());
            workers.add(t);
            t.start();
        }
        joinAll(workers);

        lastReduceSum = sum.get();
        log("Rank-0 (Root)", "[Rank 0] MPI_Reduce(SUM) result = " + lastReduceSum);
        return lastReduceSum;
    }

    // ── Fault injection (kept consistent with the rest of the CineBook lab) ──
    public synchronized MpiProcess findRank(int rank) {
        for (MpiProcess p : processes) {
            if (p.getRank() == rank) return p;
        }
        return null;
    }

    public synchronized void crash(int rank) {
        MpiProcess p = findRank(rank);
        if (p == null || rank == 0) return;
        p.setAlive(false);
        log("Rank-" + rank, ">>> FAULT: " + p.getName() + " (rank " + rank + ") went OFFLINE <<<");
    }

    public synchronized void recover(int rank) {
        MpiProcess p = findRank(rank);
        if (p == null) return;
        p.setAlive(true);
        log("Rank-" + rank, ">>> RECOVERY: " + p.getName() + " (rank " + rank + ") back ONLINE <<<");
    }

    public synchronized void reset() {
        for (MpiProcess p : processes) p.reset();
        lastBroadcastMessage = null;
        lastScatterInput = null;
        lastGatherResult = null;
        lastReduceSum = null;
        broadcastCount.set(0);
        scatterCount.set(0);
        gatherCount.set(0);
        reduceCount.set(0);
        memoryLogs.clear();
        log("Comm-Reset", "Communicator state cleared — " + size() + " ranks remain registered");
    }

    private void joinAll(List<Thread> threads) {
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }

    /** A few ms of simulated network/compute latency so concurrency is visible, not instantaneous. */
    private void simulateNetworkDelay() {
        try { Thread.sleep(8 + (int) (Math.random() * 12)); } catch (InterruptedException ignored) {}
    }
}
