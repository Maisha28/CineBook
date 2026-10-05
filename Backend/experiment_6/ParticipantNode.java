import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ParticipantNode {
    private final String nodeId;
    private final String serviceName;
    private boolean alive = true;
    private boolean forceAbort = false;
    private boolean simulateTimeout = false;

    // Prepared state storage: txId -> record
    private final Map<String, TransactionRecord> localStore = new ConcurrentHashMap<>();

    public ParticipantNode(String nodeId, String serviceName) {
        this.nodeId = nodeId;
        this.serviceName = serviceName;
    }

    public String nodeId() { return nodeId; }
    public String serviceName() { return serviceName; }
    public boolean alive() { return alive; }
    public boolean forceAbort() { return forceAbort; }
    public boolean simulateTimeout() { return simulateTimeout; }

    public void setAlive(boolean alive) { this.alive = alive; }
    public void setForceAbort(boolean force) { this.forceAbort = force; }
    public void setSimulateTimeout(boolean timeout) { this.simulateTimeout = timeout; }

    /** Phase 1: Prepare request from Coordinator */
    public synchronized String prepare(String txId, String seatId, String userName, double amount) {
        if (!alive) {
            Exp6Log.log(nodeId, "PREPARE request for " + txId + " REJECTED -- node is DOWN");
            return "VOTE_ABORT: Node DOWN";
        }
        if (simulateTimeout) {
            Exp6Log.log(nodeId, "PREPARE request for " + txId + " TIMED OUT (injected timeout)");
            return "TIMEOUT";
        }
        if (forceAbort) {
            Exp6Log.log(nodeId, "PREPARE request for " + txId + " VOTED ABORT (injected fault on " + serviceName + ")");
            return "VOTE_ABORT: Simulated Service Failure";
        }

        TransactionRecord record = new TransactionRecord(
            txId, seatId, userName, amount, TransactionRecord.State.PREPARED, "Prepared local lock on " + serviceName
        );
        localStore.put(txId, record);
        Exp6Log.log(nodeId, "PREPARE request for " + txId + " VOTED COMMIT -- local locks acquired on " + serviceName);
        return "VOTE_COMMIT";
    }

    /** Phase 2: Commit request from Coordinator */
    public synchronized boolean commit(String txId) {
        TransactionRecord record = localStore.get(txId);
        if (record != null) {
            record.setState(TransactionRecord.State.COMMITTED);
            record.setDetail("Committed permanently on " + serviceName);
            Exp6Log.log(nodeId, "GLOBAL_COMMIT received for " + txId + " -- transaction finalized on " + serviceName);
            return true;
        }
        Exp6Log.log(nodeId, "GLOBAL_COMMIT for " + txId + " failed -- no prepared record found!");
        return false;
    }

    /** Phase 2: Abort request from Coordinator */
    public synchronized boolean abort(String txId) {
        TransactionRecord record = localStore.get(txId);
        if (record != null) {
            record.setState(TransactionRecord.State.ABORTED);
            record.setDetail("Aborted and local locks released on " + serviceName);
            Exp6Log.log(nodeId, "GLOBAL_ABORT received for " + txId + " -- compensating rollback executed on " + serviceName);
            return true;
        }
        Exp6Log.log(nodeId, "GLOBAL_ABORT received for " + txId + " -- record was not prepared, no action needed");
        return true;
    }

    public List<TransactionRecord> getLocalRecords() {
        return new ArrayList<>(localStore.values());
    }

    public void clear() {
        localStore.clear();
        forceAbort = false;
        simulateTimeout = false;
        alive = true;
    }
}
