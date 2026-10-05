import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an individual CineBook backend booking server node managed by the Load Balancer.
 */
public class BackendServerNode {

    private final String nodeId;
    private final String name;
    private volatile int weight;
    private volatile int currentWeight; // For smooth weighted round-robin calculation
    private volatile boolean alive;
    private final AtomicInteger totalRequestsHandled;
    private final AtomicInteger activeConnections;
    private volatile int simulatedLatencyMs;

    public BackendServerNode(String nodeId, String name, int weight) {
        this.nodeId = nodeId;
        this.name = name;
        this.weight = Math.max(1, weight);
        this.currentWeight = 0;
        this.alive = true;
        this.totalRequestsHandled = new AtomicInteger(0);
        this.activeConnections = new AtomicInteger(0);
        this.simulatedLatencyMs = 15;
    }

    public String getNodeId() { return nodeId; }
    public String getName() { return name; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) {
        this.weight = Math.max(1, weight);
        this.currentWeight = 0;
    }

    public int getCurrentWeight() { return currentWeight; }
    public void setCurrentWeight(int currentWeight) { this.currentWeight = currentWeight; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    public int getTotalRequestsHandled() { return totalRequestsHandled.get(); }
    public int getActiveConnections() { return activeConnections.get(); }
    public int getSimulatedLatencyMs() { return simulatedLatencyMs; }
    public void setSimulatedLatencyMs(int latency) { this.simulatedLatencyMs = latency; }

    /** Health check ping */
    public boolean ping() {
        return alive;
    }

    /** Simulates server crash */
    public void crash() {
        this.alive = false;
        Exp7Log.log(nodeId, ">>> FAULT TRIGGERED: " + name + " (" + nodeId + ") CRASHED / WENT OFFLINE <<<");
    }

    /** Simulates server recovery */
    public void recover() {
        this.alive = true;
        this.currentWeight = 0;
        Exp7Log.log(nodeId, ">>> RECOVERY: " + name + " (" + nodeId + ") RECOVERED AND BACK ONLINE <<<");
    }

    /** Resets metrics */
    public void reset() {
        this.totalRequestsHandled.set(0);
        this.activeConnections.set(0);
        this.currentWeight = 0;
        this.alive = true;
    }

    /**
     * Processes a booking request on this server.
     * Throws an IllegalStateException if the server is offline or fails during processing.
     */
    public BookingResponse processRequest(String requestId, String seatId, String userName) {
        if (!alive) {
            Exp7Log.log(nodeId, "Request " + requestId + " REJECTED -- Server is DOWN");
            throw new IllegalStateException("Server " + nodeId + " is offline");
        }

        activeConnections.incrementAndGet();
        try {
            // Optional micro-delay to simulate actual network/database work
            if (simulatedLatencyMs > 0) {
                try {
                    Thread.sleep(Math.min(simulatedLatencyMs, 20));
                } catch (InterruptedException ignored) {}
            }

            // Check if server died during processing (mid-flight failure simulation)
            if (!alive) {
                Exp7Log.log(nodeId, "Request " + requestId + " FAILED mid-processing -- Server died!");
                throw new IllegalStateException("Server " + nodeId + " died during execution");
            }

            int count = totalRequestsHandled.incrementAndGet();
            String confirmationCode = "CB-EXP7-" + nodeId.substring(nodeId.lastIndexOf('-') + 1) + "-" + count;
            Exp7Log.log(nodeId, "Processed request " + requestId + " for user '" + userName + "' (Seat: " + seatId + ") -> Conf: " + confirmationCode);

            return new BookingResponse(
                true,
                requestId,
                nodeId,
                name,
                seatId,
                userName,
                confirmationCode,
                "Seat successfully booked by " + name
            );
        } finally {
            activeConnections.decrementAndGet();
        }
    }

    public static class BookingResponse {
        public final boolean success;
        public final String requestId;
        public final String serverId;
        public final String serverName;
        public final String seatId;
        public final String userName;
        public final String confirmationCode;
        public final String message;

        public BookingResponse(boolean success, String requestId, String serverId, String serverName,
                               String seatId, String userName, String confirmationCode, String message) {
            this.success = success;
            this.requestId = requestId;
            this.serverId = serverId;
            this.serverName = serverName;
            this.seatId = seatId;
            this.userName = userName;
            this.confirmationCode = confirmationCode;
            this.message = message;
        }
    }
}
