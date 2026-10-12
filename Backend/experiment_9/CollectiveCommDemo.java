import java.util.*;

/**
 * Standalone Terminal Execution for Experiment 9:
 * MPI Collective Communication — Broadcast, Scatter, Gather (+ Reduce).
 *
 * Mirrors the classic mpi4py reference program (mpi_collective.py) using an
 * in-process simulation of an MPI communicator (COMM_WORLD) built around the
 * CineBook Booking Cluster: Rank 0 is the HQ Coordinator and ranks 1..N-1
 * are regional cinema branch nodes, each driven by its own Java thread to
 * genuinely emulate concurrent MPI processes (a real lab would instead run
 * this under `mpirun -n <N> python mpi_collective.py` with mpi4py).
 *
 * Run with:
 *   javac *.java
 *   java CollectiveCommDemo
 */
public class CollectiveCommDemo {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println(" CineBook Experiment 9: MPI Collective Communication (Broadcast / Scatter / Gather) ");
        System.out.println("==========================================================================");

        int numRanks = 4;
        MpiCommunicator comm = new MpiCommunicator(numRanks);

        // ─────────────────────────────────────────────────────────────────
        // Scenario 1: MPI_Bcast
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 1: MPI_Bcast — HQ Coordinator broadcasts today's promo config");
        System.out.println("--------------------------------------------------------------------------");
        comm.broadcast("PROMO50 | Lineup: Dune Part Two, Interstellar, Inception");
        for (MpiProcess p : comm.getProcesses()) {
            System.out.printf("  [Rank %d] %-18s Received broadcast data: %s%n",
                    p.getRank(), p.getName(), p.getReceivedBroadcast());
        }

        // ─────────────────────────────────────────────────────────────────
        // Scenario 2: MPI_Scatter
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 2: MPI_Scatter — distributing ticket-booking workload across branches");
        System.out.println("--------------------------------------------------------------------------");
        List<Integer> workload = new ArrayList<>();
        for (int i = 0; i < numRanks; i++) workload.add(i * 10);
        comm.scatter(workload);
        for (MpiProcess p : comm.getProcesses()) {
            System.out.printf("  [Rank %d] %-18s Received scatter value: %d%n",
                    p.getRank(), p.getName(), p.getScatterValue());
        }

        // ─────────────────────────────────────────────────────────────────
        // Scenario 3: MPI_Gather
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 3: MPI_Gather — collecting processed results back at HQ");
        System.out.println("--------------------------------------------------------------------------");
        List<Integer> gathered = comm.gather();
        System.out.println("  [Rank 0] Gathered data from all processes: " + gathered);

        // ─────────────────────────────────────────────────────────────────
        // Scenario 4 (Extension / Problem Statement #1): MPI_Reduce(SUM)
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 4 (Extension): MPI_Reduce(SUM) — aggregate without a full gather");
        System.out.println("--------------------------------------------------------------------------");
        int total = comm.reduceSum();
        System.out.println("  [Rank 0] MPI_Reduce(SUM) total booking workload processed: " + total);

        // ─────────────────────────────────────────────────────────────────
        // Scenario 5: Fault tolerance — a branch node drops mid-run
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 5: Fault Tolerance — Bengaluru Branch (rank 3) goes offline mid-run");
        System.out.println("--------------------------------------------------------------------------");
        if (numRanks > 3) {
            comm.crash(3);
            comm.broadcast("URGENT: Scheduled server maintenance notice");
            List<Integer> gatheredAfterCrash = comm.gather();
            System.out.println("  [Rank 0] Gathered data with rank 3 offline: " + gatheredAfterCrash);
            comm.recover(3);
            System.out.println("  Rank 3 recovered and rejoined the communicator.");
        }

        System.out.println("\n==========================================================================");
        System.out.println(" Experiment 9 Demo Completed Successfully!");
        System.out.println("==========================================================================");
    }
}
