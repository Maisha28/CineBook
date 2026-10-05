import java.util.*;

public class QuorumManager {

    public static class QuorumResult {
        public final String operation; // "WRITE" or "READ"
        public final int totalNodes; // N
        public final int writeQuorum; // W
        public final int readQuorum; // R
        public final boolean strongConsistency; // W + R > N
        public final boolean success;
        public final int acksReceived;
        public final String details;
        public final Map<String, Integer> nodeVersions;

        public QuorumResult(String operation, int totalNodes, int writeQuorum, int readQuorum, boolean strongConsistency, boolean success, int acksReceived, String details, Map<String, Integer> nodeVersions) {
            this.operation = operation;
            this.totalNodes = totalNodes;
            this.writeQuorum = writeQuorum;
            this.readQuorum = readQuorum;
            this.strongConsistency = strongConsistency;
            this.success = success;
            this.acksReceived = acksReceived;
            this.details = details;
            this.nodeVersions = nodeVersions;
        }
    }

    private int N = 5; // Total nodes
    private int W = 3; // Write quorum
    private int R = 3; // Read quorum

    // Node version numbers: Node1..Node5 -> current data version
    private final Map<String, Integer> nodeVersions = new LinkedHashMap<>();
    private final Map<String, Boolean> nodeHealth = new LinkedHashMap<>();

    public QuorumManager() {
        for (int i = 1; i <= N; i++) {
            String id = "Node-" + i;
            nodeVersions.put(id, 1); // Version 1 initial state
            nodeHealth.put(id, true);
        }
    }

    public synchronized void setParameters(int N, int W, int R) {
        this.N = N;
        this.W = W;
        this.R = R;
        // Adjust nodes
        nodeVersions.clear();
        nodeHealth.clear();
        for (int i = 1; i <= N; i++) {
            String id = "Node-" + i;
            nodeVersions.put(id, 1);
            nodeHealth.put(id, true);
        }
        Exp6Log.log("Quorum", "Quorum parameters set to N=" + N + ", W=" + W + ", R=" + R + " (Strong Consistency: " + (W + R > N) + ")");
    }

    public int N() { return N; }
    public int W() { return W; }
    public int R() { return R; }
    public boolean isStrongConsistency() { return (W + R > N); }

    public synchronized QuorumResult executeWrite(String dataKey, int newVersion) {
        Exp6Log.log("Quorum", "EXECUTE WRITE (Version " + newVersion + ") -- Target Write Quorum W=" + W + "/" + N);
        int acks = 0;
        Map<String, Integer> state = new LinkedHashMap<>();

        for (Map.Entry<String, Boolean> entry : nodeHealth.entrySet()) {
            String nodeId = entry.getKey();
            boolean alive = entry.getValue();

            if (alive) {
                acks++;
                nodeVersions.put(nodeId, newVersion);
                state.put(nodeId, newVersion);
                Exp6Log.log("Quorum", "  " + nodeId + ": Write ACK (Updated to v" + newVersion + ")");
                if (acks >= W) break; // Reached write quorum
            } else {
                state.put(nodeId, nodeVersions.get(nodeId));
                Exp6Log.log("Quorum", "  " + nodeId + ": UNREACHABLE (Missed write v" + newVersion + ")");
            }
        }

        boolean success = acks >= W;
        String details = success
            ? "Write Quorum Met (" + acks + "/" + W + " ACKs received). Data updated to v" + newVersion + "."
            : "Write Quorum Failed (" + acks + "/" + W + " ACKs received). Not enough healthy nodes!";

        Exp6Log.log("Quorum", details);
        return new QuorumResult("WRITE", N, W, R, isStrongConsistency(), success, acks, details, new LinkedHashMap<>(nodeVersions));
    }

    public synchronized QuorumResult executeRead() {
        Exp6Log.log("Quorum", "EXECUTE READ -- Target Read Quorum R=" + R + "/" + N);
        int responses = 0;
        int maxVersionFound = 0;

        for (Map.Entry<String, Boolean> entry : nodeHealth.entrySet()) {
            String nodeId = entry.getKey();
            boolean alive = entry.getValue();

            if (alive) {
                responses++;
                int v = nodeVersions.get(nodeId);
                if (v > maxVersionFound) maxVersionFound = v;
                Exp6Log.log("Quorum", "  " + nodeId + ": Read response v" + v);
                if (responses >= R) break; // Reached read quorum
            }
        }

        boolean success = responses >= R;
        boolean isLatest = maxVersionFound == Collections.max(nodeVersions.values());

        String details = success
            ? "Read Quorum Met (" + responses + "/" + R + " nodes read). Highest version returned: v" + maxVersionFound + (isLatest ? " (Consistent Latest)" : " (STALE Read! W+R <= N)")
            : "Read Quorum Failed (" + responses + "/" + R + " nodes).";

        Exp6Log.log("Quorum", details);
        return new QuorumResult("READ", N, W, R, isStrongConsistency(), success, responses, details, new LinkedHashMap<>(nodeVersions));
    }

    public synchronized void setNodeHealth(String nodeId, boolean alive) {
        if (nodeHealth.containsKey(nodeId)) {
            nodeHealth.put(nodeId, alive);
            Exp6Log.log("Quorum", nodeId + " status updated: " + (alive ? "ONLINE" : "OFFLINE"));
        }
    }

    public Map<String, Integer> getNodeVersions() { return new LinkedHashMap<>(nodeVersions); }
    public Map<String, Boolean> getNodeHealth() { return new LinkedHashMap<>(nodeHealth); }
}
