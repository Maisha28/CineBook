import java.util.*;

/**
 * Standalone Terminal Execution for Experiment 7:
 * Load Balancing and Fault Tolerance in Distributed Systems.
 *
 * Demonstrates:
 *  1. Round Robin sequential distribution
 *  2. Weighted Round Robin (3:2:1 ratio) distribution
 *  3. Fault Detection & dynamic exclusion of failed servers
 *  4. Server recovery & re-integration into the active pool
 *  5. Automatic failover redirection (zero dropped requests)
 */
public class LoadBalancerDemo {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println(" CineBook Experiment 7: Load Balancing and Fault Tolerance in Distributed Systems ");
        System.out.println("==========================================================================");

        LoadBalancer lb = new LoadBalancer();

        // ─────────────────────────────────────────────────────────────────
        // Scenario 1: Round Robin Algorithm
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 1: Round Robin Sequential Distribution (S1 -> S2 -> S3 -> S1...)");
        System.out.println("--------------------------------------------------------------------------");
        lb.setAlgorithm(LoadBalancingAlgorithm.ROUND_ROBIN);

        for (int i = 1; i <= 6; i++) {
            LoadBalancer.DispatchResult res = lb.routeRequest("A" + i, "User" + i);
            System.out.printf("  Req #%d -> Routed to: %-10s | Algorithm: %-15s | Conf: %s%n",
                    i, res.serverId, res.algorithm.getDisplayName(), res.confirmationCode);
        }

        // ─────────────────────────────────────────────────────────────────
        // Scenario 2: Weighted Round Robin Algorithm (3:2:1 Ratio)
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 2: Weighted Round Robin (Capacities: S1=3, S2=2, S3=1 -> Ratio 3:2:1)");
        System.out.println("--------------------------------------------------------------------------");
        lb.reset();
        lb.setAlgorithm(LoadBalancingAlgorithm.WEIGHTED_ROUND_ROBIN);

        int batchSize = 12; // 2 complete 6-request cycles (expected: S1=6, S2=4, S3=2)
        System.out.println("Dispatching a batch of " + batchSize + " requests...");
        LoadBalancer.BatchSummary summary = lb.routeBatch(batchSize, "B", "Client");

        System.out.println("\nSequence: " + String.join(" -> ", summary.sequence));
        System.out.println("\nDistribution Results:");
        for (BackendServerNode s : lb.getServers()) {
            int count = summary.serverCounts.getOrDefault(s.getNodeId(), 0);
            double pct = summary.serverPercentages.getOrDefault(s.getNodeId(), 0.0);
            System.out.printf("  %-10s (Weight: %d) : %2d requests (%5.1f%%) | Expected ratio share: %d/6%n",
                    s.getNodeId(), s.getWeight(), count, pct, s.getWeight());
        }

        // ─────────────────────────────────────────────────────────────────
        // Scenario 3: Server Failure & Fault Handling
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 3: Fault Handling & Health Monitoring (Server-2 Crashes)");
        System.out.println("--------------------------------------------------------------------------");
        BackendServerNode s2 = lb.findServer("Server-2");
        System.out.println("Triggering failure on Server-2...");
        s2.crash();

        System.out.println("\nRunning Health Check...");
        Map<String, Boolean> health = lb.checkHealth();
        for (Map.Entry<String, Boolean> e : health.entrySet()) {
            System.out.printf("  %-10s : %s%n", e.getKey(), e.getValue() ? "ONLINE [HEALTHY]" : "DOWN [UNAVAILABLE]");
        }

        System.out.println("\nRouting 6 requests while Server-2 is DOWN (Expect only S1 and S3 to receive traffic):");
        for (int i = 1; i <= 6; i++) {
            LoadBalancer.DispatchResult res = lb.routeRequest("C" + i, "FaultUser" + i);
            System.out.printf("  Req #%d -> Routed to: %-10s | Status: %s%n",
                    i, res.serverId, res.success ? "SUCCESS" : "FAILED");
            if (res.serverId.equals("Server-2")) {
                System.err.println("ERROR: Server-2 received a request while crashed!");
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // Scenario 4: Server Recovery & Re-Integration
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 4: Server Recovery & Re-integration (Server-2 Restored)");
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("Restoring Server-2 to online status...");
        s2.recover();

        System.out.println("\nRunning Health Check after recovery...");
        health = lb.checkHealth();
        for (Map.Entry<String, Boolean> e : health.entrySet()) {
            System.out.printf("  %-10s : %s%n", e.getKey(), e.getValue() ? "ONLINE [HEALTHY]" : "DOWN [UNAVAILABLE]");
        }

        System.out.println("\nRouting 6 requests after recovery (Server-2 should now receive its share):");
        for (int i = 1; i <= 6; i++) {
            LoadBalancer.DispatchResult res = lb.routeRequest("D" + i, "RecoverUser" + i);
            System.out.printf("  Req #%d -> Routed to: %-10s | Status: %s%n",
                    i, res.serverId, res.success ? "SUCCESS" : "FAILED");
        }

        // ─────────────────────────────────────────────────────────────────
        // Scenario 5: Mid-Processing Crash & Automatic Failover Redirection
        // ─────────────────────────────────────────────────────────────────
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println(" SCENARIO 5: Automatic Failover Redirection (Zero Dropped Requests)");
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("Simulating sudden failure during request execution...");
        // Arm Server-1 to fail mid-request by crashing it right before dispatch
        BackendServerNode s1 = lb.findServer("Server-1");
        s1.crash();

        LoadBalancer.DispatchResult failoverRes = lb.routeRequest("E1", "FailoverTestUser");
        System.out.printf("  Dispatched request -> Was Failover: %b | Handled by: %s | Conf: %s%n",
                failoverRes.wasFailover, failoverRes.serverId, failoverRes.confirmationCode);
        System.out.println("  Detail message: " + failoverRes.message);

        System.out.println("\n==========================================================================");
        System.out.println(" Experiment 7 Demo Completed Successfully!");
        System.out.println("==========================================================================");
    }
}
