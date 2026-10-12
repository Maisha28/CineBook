/**
 * Represents a single MPI-style rank (process) participating in the
 * CineBook Booking Cluster communicator for Experiment 9.
 *
 * Rank 0 is always the root process ("HQ Coordinator"); ranks 1..N-1 are
 * worker processes representing regional cinema branch booking nodes.
 * Each rank keeps exactly the local state a real MPI process would hold
 * between collective calls: the value it last received via broadcast,
 * the chunk it received via scatter, and the value it computed locally
 * before contributing it to a gather/reduce.
 */
public class MpiProcess {

    private final int rank;
    private final String name;

    // Local state populated by collective operations (mirrors the local
    // variables `data`, `received_value`, `computed_value` in the mpi4py
    // reference program: mpi_collective.py)
    private volatile String receivedBroadcast;
    private volatile Integer scatterValue;
    private volatile Integer computedValue;
    private volatile boolean alive = true;

    public MpiProcess(int rank, String name) {
        this.rank = rank;
        this.name = name;
    }

    public int getRank() { return rank; }
    public String getName() { return name; }
    public boolean isRoot() { return rank == 0; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    public String getReceivedBroadcast() { return receivedBroadcast; }
    public void setReceivedBroadcast(String data) { this.receivedBroadcast = data; }

    public Integer getScatterValue() { return scatterValue; }
    public void setScatterValue(Integer v) { this.scatterValue = v; }

    public Integer getComputedValue() { return computedValue; }

    /**
     * Local computation performed by this rank once it has its scattered
     * chunk — mirrors the reference program's
     * {@code computed_value = received_value + rank}.
     */
    public int compute() {
        int base = (scatterValue != null) ? scatterValue : 0;
        computedValue = base + rank;
        return computedValue;
    }

    public void reset() {
        receivedBroadcast = null;
        scatterValue = null;
        computedValue = null;
        alive = true;
    }
}
