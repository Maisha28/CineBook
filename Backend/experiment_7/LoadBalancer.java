import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load Balancer for CineBook Experiment 7.
 * Supports Round Robin, Smooth Weighted Round Robin, active health monitoring,
 * and automatic fault detection with failover redirect.
 */
public class LoadBalancer {

    public static class NoHealthyServerException extends RuntimeException {
        public NoHealthyServerException(String message) {
            super(message);
        }
    }

    public static class DispatchResult {
        public final String requestId;
        public final boolean success;
        public final String serverId;
        public final String serverName;
        public final LoadBalancingAlgorithm algorithm;
        public final boolean wasFailover;
        public final String failedServerId;
        public final String confirmationCode;
        public final String message;
        public final long timestamp;

        public DispatchResult(String requestId, boolean success, String serverId, String serverName,
                              LoadBalancingAlgorithm algorithm, boolean wasFailover, String failedServerId,
                              String confirmationCode, String message) {
            this.requestId = requestId;
            this.success = success;
            this.serverId = serverId;
            this.serverName = serverName;
            this.algorithm = algorithm;
            this.wasFailover = wasFailover;
            this.failedServerId = failedServerId;
            this.confirmationCode = confirmationCode;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class BatchSummary {
        public final int totalRequests;
        public final LoadBalancingAlgorithm algorithm;
        public final Map<String, Integer> serverCounts; // serverId -> count
        public final Map<String, Double> serverPercentages; // serverId -> %
        public final List<String> sequence; // list of serverIds in order
        public final int failoversCount;

        public BatchSummary(int totalRequests, LoadBalancingAlgorithm algorithm,
                            Map<String, Integer> serverCounts, Map<String, Double> serverPercentages,
                            List<String> sequence, int failoversCount) {
            this.totalRequests = totalRequests;
            this.algorithm = algorithm;
            this.serverCounts = serverCounts;
            this.serverPercentages = serverPercentages;
            this.sequence = sequence;
            this.failoversCount = failoversCount;
        }
    }

    private final List<BackendServerNode> servers = new ArrayList<>();
    private volatile LoadBalancingAlgorithm algorithm = LoadBalancingAlgorithm.ROUND_ROBIN;
    private final AtomicInteger rrCounter = new AtomicInteger(0);
    private final AtomicInteger totalRequestsReceived = new AtomicInteger(0);
    private final AtomicInteger totalFailoversOccurred = new AtomicInteger(0);
    private final List<DispatchResult> routingHistory = new CopyOnWriteArrayList<>();
    private final List<String> memoryLogs = new CopyOnWriteArrayList<>();

    public LoadBalancer() {
        // Default cluster: 3 servers with weights 3, 2, 1 matching experiment specification
        servers.add(new BackendServerNode("Server-1", "Server 1 (Primary Node)", 3));
        servers.add(new BackendServerNode("Server-2", "Server 2 (Compute Node)", 2));
        servers.add(new BackendServerNode("Server-3", "Server 3 (Edge Node)", 1));
        log("LB-Init", "Load Balancer initialized with 3 backend servers: S1 (wt:3), S2 (wt:2), S3 (wt:1)");
    }

    private void log(String who, String msg) {
        Exp7Log.log(who, msg);
        memoryLogs.add(String.format("[%s] %s", who, msg));
        while (memoryLogs.size() > 200) {
            memoryLogs.remove(0);
        }
    }

    public List<BackendServerNode> getServers() { return Collections.unmodifiableList(servers); }

    public BackendServerNode findServer(String nodeId) {
        for (BackendServerNode s : servers) {
            if (s.getNodeId().equalsIgnoreCase(nodeId)) return s;
        }
        return null;
    }

    public LoadBalancingAlgorithm getAlgorithm() { return algorithm; }

    public synchronized void setAlgorithm(LoadBalancingAlgorithm algorithm) {
        this.algorithm = algorithm;
        // Reset dynamic state for smooth round robin
        for (BackendServerNode s : servers) {
            s.setCurrentWeight(0);
        }
        log("LB-Config", "Scheduling algorithm switched to: " + algorithm.getDisplayName());
    }

    public int getTotalRequestsReceived() { return totalRequestsReceived.get(); }
    public int getTotalFailoversOccurred() { return totalFailoversOccurred.get(); }
    public List<DispatchResult> getRoutingHistory() { return new ArrayList<>(routingHistory); }
    public List<String> getMemoryLogs() { return new ArrayList<>(memoryLogs); }

    /** Returns all currently healthy and responsive servers */
    public List<BackendServerNode> getHealthyServers() {
        List<BackendServerNode> healthy = new ArrayList<>();
        for (BackendServerNode s : servers) {
            if (s.ping()) healthy.add(s);
        }
        return healthy;
    }

    /**
     * Algorithm 1: Standard Round Robin
     * Sequentially steps through the healthy servers.
     */
    private synchronized BackendServerNode selectRoundRobin(List<BackendServerNode> healthy) {
        if (healthy.isEmpty()) {
            throw new NoHealthyServerException("503 Service Unavailable: No healthy backend servers available!");
        }
        int idx = Math.abs(rrCounter.getAndIncrement() % healthy.size());
        return healthy.get(idx);
    }

    /**
     * Algorithm 2: Smooth Weighted Round Robin (Nginx Algorithm)
     * Distributes requests proportionally according to assigned capacity/weights (e.g. 3:2:1)
     * while guaranteeing smooth interleaving without bursty clustering.
     */
    private synchronized BackendServerNode selectWeightedRoundRobin(List<BackendServerNode> healthy) {
        if (healthy.isEmpty()) {
            throw new NoHealthyServerException("503 Service Unavailable: No healthy backend servers available!");
        }

        int totalWeight = 0;
        BackendServerNode best = null;

        for (BackendServerNode s : healthy) {
            int w = s.getWeight();
            totalWeight += w;
            s.setCurrentWeight(s.getCurrentWeight() + w);

            if (best == null || s.getCurrentWeight() > best.getCurrentWeight()) {
                best = s;
            }
        }

        if (best != null) {
            best.setCurrentWeight(best.getCurrentWeight() - totalWeight);
        }

        return best;
    }

    /** Selects the next server based on active algorithm */
    public synchronized BackendServerNode selectServer() {
        List<BackendServerNode> healthy = getHealthyServers();
        if (algorithm == LoadBalancingAlgorithm.WEIGHTED_ROUND_ROBIN) {
            return selectWeightedRoundRobin(healthy);
        } else {
            return selectRoundRobin(healthy);
        }
    }

    /**
     * Routes a single booking request through the Load Balancer.
     * Incorporates Health Monitoring & Automatic Fault Handling with failover redirection.
     */
    public DispatchResult routeRequest(String seatId, String userName) {
        String reqId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        totalRequestsReceived.incrementAndGet();

        log("LB-Route", "Incoming request " + reqId + " [User: " + userName + ", Seat: " + seatId + "] using " + algorithm.getDisplayName());

        BackendServerNode targetServer;
        try {
            targetServer = selectServer();
        } catch (NoHealthyServerException e) {
            log("LB-Error", "Routing failed for " + reqId + ": " + e.getMessage());
            DispatchResult failure = new DispatchResult(reqId, false, "NONE", "None", algorithm, false, null, null, e.getMessage());
            routingHistory.add(failure);
            return failure;
        }

        log("LB-Route", "Load balancer selected target: " + targetServer.getName() + " (" + targetServer.getNodeId() + ")");

        // Attempt execution with fault detection & failover redirect
        try {
            BackendServerNode.BookingResponse resp = targetServer.processRequest(reqId, seatId, userName);
            DispatchResult result = new DispatchResult(reqId, true, targetServer.getNodeId(), targetServer.getName(),
                    algorithm, false, null, resp.confirmationCode, resp.message);
            routingHistory.add(result);
            return result;
        } catch (Exception ex) {
            // Fault handling: Server failed! Exclude server and failover to next healthy server
            totalFailoversOccurred.incrementAndGet();
            String failedId = targetServer.getNodeId();
            log("LB-Failover", ">>> ALERT: " + failedId + " failed to respond! Initiating automatic failover redirect... <<<");

            // Mark node as failed in case it was a mid-transaction crash
            targetServer.setAlive(false);

            // Re-select from remaining healthy servers
            try {
                BackendServerNode backupServer = selectServer();
                log("LB-Failover", "Redirecting request " + reqId + " -> healthy backup: " + backupServer.getName() + " (" + backupServer.getNodeId() + ")");
                BackendServerNode.BookingResponse backupResp = backupServer.processRequest(reqId, seatId, userName);

                DispatchResult failoverResult = new DispatchResult(reqId, true, backupServer.getNodeId(), backupServer.getName(),
                        algorithm, true, failedId, backupResp.confirmationCode,
                        "Handled via failover redirect after " + failedId + " failed");
                routingHistory.add(failoverResult);
                return failoverResult;
            } catch (Exception backupEx) {
                log("LB-Failover", "CRITICAL: Failover also failed! " + backupEx.getMessage());
                DispatchResult failoverFailed = new DispatchResult(reqId, false, failedId, targetServer.getName(),
                        algorithm, true, failedId, null, "All available servers failed during failover");
                routingHistory.add(failoverFailed);
                return failoverFailed;
            }
        }
    }

    /**
     * Executes a batch of requests to empirically demonstrate the distribution ratio
     * (e.g. 1:1:1 for Round Robin vs 3:2:1 for Weighted Round Robin).
     */
    public BatchSummary routeBatch(int count, String baseSeat, String userPrefix) {
        log("LB-Batch", ">>> Executing batch simulation of " + count + " requests (" + algorithm.getDisplayName() + ") <<<");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BackendServerNode s : servers) {
            counts.put(s.getNodeId(), 0);
        }

        List<String> seq = new ArrayList<>();
        int batchFailovers = 0;

        for (int i = 1; i <= count; i++) {
            String seat = baseSeat + "-" + i;
            String user = userPrefix + "-" + i;
            DispatchResult res = routeRequest(seat, user);
            if (res.success) {
                counts.put(res.serverId, counts.getOrDefault(res.serverId, 0) + 1);
                seq.add(res.serverId);
            }
            if (res.wasFailover) {
                batchFailovers++;
            }
        }

        Map<String, Double> percentages = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            double pct = count > 0 ? (entry.getValue() * 100.0) / count : 0.0;
            percentages.put(entry.getKey(), Math.round(pct * 10.0) / 10.0);
        }

        log("LB-Batch", "Batch completed: " + counts + " | Percentages: " + percentages);
        return new BatchSummary(count, algorithm, counts, percentages, seq, batchFailovers);
    }

    /** Health Check Monitor */
    public synchronized Map<String, Boolean> checkHealth() {
        Map<String, Boolean> health = new LinkedHashMap<>();
        for (BackendServerNode s : servers) {
            boolean status = s.ping();
            health.put(s.getNodeId(), status);
            log("LB-HealthCheck", s.getNodeId() + " (" + s.getName() + ") -> " + (status ? "HEALTHY [200 OK]" : "DOWN [UNREACHABLE]"));
        }
        return health;
    }

    /** Updates weight of a server */
    public synchronized void updateServerWeight(String nodeId, int newWeight) {
        BackendServerNode node = findServer(nodeId);
        if (node != null) {
            node.setWeight(newWeight);
            log("LB-Config", "Updated " + nodeId + " weight to " + newWeight);
        }
    }

    /** Resets the entire load balancer and all servers */
    public synchronized void reset() {
        rrCounter.set(0);
        totalRequestsReceived.set(0);
        totalFailoversOccurred.set(0);
        routingHistory.clear();
        memoryLogs.clear();
        for (BackendServerNode s : servers) {
            s.reset();
        }
        log("LB-Reset", "Load Balancer and all backend server states have been fully reset.");
    }
}
