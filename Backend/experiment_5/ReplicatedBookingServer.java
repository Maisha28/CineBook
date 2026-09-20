import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// One CineBook server. The same class runs as the Primary node or the Backup
// node; what differs is the Role it plays at the moment:
//
//   ACTIVE_PRIMARY -- serves bookings, replicates each one to its peer
//   STANDBY        -- keeps a replica log, serves nothing
//   RECOVERING     -- a restarted Primary catching up before it takes over
//
// Replication is synchronous: for every booking the active primary
//   1. sends PREPARE to the peer and waits for the ACK,
//   2. commits the booking to the (shared) database,
//   3. sends COMMIT so the peer's replica record becomes final,
//   4. only then replies to the client.
public class ReplicatedBookingServer extends UnicastRemoteObject implements BookingServer {

    private final String node;
    private final String peerName;
    private final BookingStore store;
    // true  -> crash() kills the whole JVM (real process death, used by Server)
    // false -> crash() only takes this node off the network (used by FailoverDemo,
    //          where both servers live in one JVM)
    private final boolean haltJvmOnCrash;

    private volatile Role role;
    private volatile boolean crashAfterDbCommit;
    private Registry registry;

    // The replica log: operationId -> record. Guarded by its own monitor.
    private final Map<String, OperationRecord> opLog = new LinkedHashMap<>();

    // Serializes bookSeat / activate / stepDownToStandby, so a role change can
    // never land in the middle of a booking. heartbeat() and the replication
    // callbacks deliberately do NOT take it: a node that is busy booking must
    // still answer heartbeats, and two nodes replicating to each other while
    // holding it would deadlock.
    private final Object roleLock = new Object();

    // The RMI object is exported on the same port as this node's registry
    // (see Cluster.portOf), so taking a node down closes exactly one port.
    public ReplicatedBookingServer(String node, BookingStore store, boolean haltJvmOnCrash) throws RemoteException {
        super(Cluster.portOf(node));
        this.node = node;
        this.peerName = Cluster.peerOf(node);
        this.store = store;
        this.haltJvmOnCrash = haltJvmOnCrash;
    }

    // ------------------------------------------------------------------
    // Startup / recovery
    // ------------------------------------------------------------------

    public void start() throws Exception {
        boolean homePrimary = Cluster.PRIMARY.equals(node);
        // The Primary node never goes straight to ACTIVE: it first checks
        // whether the Backup has been running things while it was away.
        role = homePrimary ? Role.RECOVERING : Role.STANDBY;

        registry = LocateRegistry.createRegistry(Cluster.portOf(node));
        registry.rebind(Cluster.BINDING, this);
        log("online on port " + Cluster.portOf(node) + ", role = " + role);

        if (homePrimary) recoverAsPrimary();
        else resyncAsBackup();
    }

    // RECOVERING -> fetch committed ops from the peer -> verify against the
    // database -> peer steps down to STANDBY -> ACTIVE_PRIMARY.
    private void recoverAsPrimary() {
        NodeStatus peerStatus = peerStatus();
        if (peerStatus == null) {
            log("Backup is not reachable -- nothing to recover from, starting as ACTIVE PRIMARY");
            role = Role.ACTIVE_PRIMARY;
            return;
        }

        log("RECOVERING: Backup is up (role = " + peerStatus.role() + "), fetching its committed operations");
        try {
            BookingServer peer = Cluster.lookup(peerName);

            List<OperationRecord> ops = peer.getCommittedOperations();
            int added = merge(ops);
            log("received " + ops.size() + " committed operation(s) from " + peerName + " (" + added + " new to me)");

            verifyAgainstDatabase();

            if (peerStatus.role() == Role.ACTIVE_PRIMARY) {
                log("asking " + peerName + " to step down to STANDBY");
                // The peer may have committed more bookings while we were
                // busy verifying; stepping down returns its final list.
                added = merge(peer.stepDownToStandby());
                log("final sync: " + added + " more operation(s) committed during recovery");
            }
        } catch (Exception e) {
            log("recovery could not finish talking to " + peerName + " (" + e.getClass().getSimpleName()
                + ") -- continuing with what I have; the database is the source of truth");
        }

        synchronized (roleLock) {
            role = Role.ACTIVE_PRIMARY;
        }
        log(">>> recovery complete: I am the ACTIVE PRIMARY again <<<");
    }

    // A (re)started Backup just pulls whatever the primary already knows.
    private void resyncAsBackup() {
        NodeStatus peerStatus = peerStatus();
        if (peerStatus == null) {
            log("Primary is not reachable -- staying STANDBY until a client activates me");
            return;
        }
        try {
            List<OperationRecord> ops = Cluster.lookup(peerName).getCommittedOperations();
            int added = merge(ops);
            log("resync: received " + ops.size() + " committed operation(s) from " + peerName
                + " (" + added + " new to me), staying STANDBY");
        } catch (Exception e) {
            log("resync failed (" + e.getClass().getSimpleName() + ") -- staying STANDBY");
        }
    }

    // "Verifies/synchronizes booking state": every SUCCESS in the replica log
    // must really be in the database.
    private void verifyAgainstDatabase() {
        int verified = 0;
        int problems = 0;
        for (OperationRecord op : committedSnapshot()) {
            if (!op.result().startsWith("SUCCESS")) continue;
            try {
                if (store.isBookedBy(op.seatId(), op.userName())) {
                    verified++;
                } else {
                    problems++;
                    log("MISMATCH: op " + op.operationId() + " is SUCCESS in the log but the database has no booking of seat "
                        + op.seatId() + " for " + op.userName());
                }
            } catch (Exception e) {
                problems++;
                log("could not verify op " + op.operationId() + " (" + e.getMessage() + ")");
            }
        }
        log("verified " + verified + " successful booking(s) against the database"
            + (problems > 0 ? ", " + problems + " problem(s)" : ", no mismatches"));
    }

    // ------------------------------------------------------------------
    // Client-facing
    // ------------------------------------------------------------------

    @Override
    public List<String> getAvailableSeats(String showId) throws RemoteException {
        try {
            return store.availableSeats(showId);
        } catch (Exception e) {
            throw new RemoteException("DB error while fetching seats: " + e.getMessage(), e);
        }
    }

    @Override
    public String bookSeat(String operationId, String seatId, String userName) throws RemoteException {
        synchronized (roleLock) {
            if (role != Role.ACTIVE_PRIMARY) {
                log("rejecting booking request " + operationId + " -- I am " + role + ", not the ACTIVE PRIMARY");
                return "NOT_PRIMARY: " + node + " is " + role;
            }
            log("booking request opId=" + operationId + " user=" + userName + " seat=" + seatId);

            // Idempotency: this exact operation was already finished.
            OperationRecord known = lookupOp(operationId);
            if (known != null && known.state() == OperationRecord.State.COMMITTED) {
                log("DUPLICATE operationId " + operationId + " -- already committed, returning the recorded result, NO new booking");
                return known.result() + " (duplicate operationId -- original result returned)";
            }

            OperationRecord pending = new OperationRecord(operationId, seatId, userName,
                OperationRecord.State.PREPARED, null, node);
            remember(pending);

            // Step 1: PREPARE on the peer, wait for its ACK.
            boolean replicated = sendPrepare(pending);

            // Step 2: commit the booking to the database.
            String result;
            try {
                result = store.bookSeat(seatId, userName, node)
                    ? successMessage(userName)
                    : "FAILED: seat not available";
            } catch (Exception e) {
                // Nothing reached the database, so undo the replica's PREPARE too.
                forget(operationId);
                if (replicated) sendAbort(operationId);
                log("database error, booking not made: " + e.getMessage());
                return "ERROR: database error while booking (" + e.getMessage() + ")";
            }
            log("database commit done -> " + result);

            // Fault injection for the demo: die after the booking is in the
            // database but before the peer's COMMIT and before the reply.
            if (crashAfterDbCommit) {
                crashAfterDbCommit = false;
                log("!!! injected fault: dying right after the database commit -- no COMMIT sent to " + peerName
                    + ", no reply sent to the client");
                crash();
                throw new RemoteException(node + " crashed before replying");
            }

            // Step 3: make the operation final locally and on the peer.
            OperationRecord done = pending.committed(result);
            remember(done);
            if (replicated) sendCommit(done);

            return result;
        }
    }

    @Override
    public NodeStatus heartbeat() throws RemoteException {
        return status();
    }

    // The client noticed the primary is gone and asks this node to take over.
    @Override
    public NodeStatus activate() throws RemoteException {
        synchronized (roleLock) {
            if (role == Role.STANDBY) {
                log("ACTIVATE request from client -- promoting STANDBY -> ACTIVE PRIMARY");
                resolveInDoubtOperations();
                role = Role.ACTIVE_PRIMARY;
                log(">>> I am now the ACTIVE PRIMARY <<<");
            } else {
                log("ACTIVATE request ignored, my role is already " + role);
            }
            return status();
        }
    }

    // Operations still PREPARED at promotion time were in flight when the old
    // primary died. Two cases, told apart by asking the shared database:
    //  - booking IS there:  the old primary committed it but never told us.
    //                       Mark it COMMITTED so the client's retry gets a
    //                       normal answer instead of "seat not available".
    //  - booking NOT there: the old primary died before committing. Drop the
    //                       record; the client's retry will run it from scratch.
    private void resolveInDoubtOperations() {
        List<OperationRecord> inDoubt = new ArrayList<>();
        synchronized (opLog) {
            for (OperationRecord op : opLog.values()) {
                if (op.state() == OperationRecord.State.PREPARED) inDoubt.add(op);
            }
        }
        for (OperationRecord op : inDoubt) {
            try {
                if (store.isBookedBy(op.seatId(), op.userName())) {
                    remember(op.committed(successMessage(op.userName())));
                    log("in-doubt op " + op.operationId() + ": PREPARED here and the booking IS in the database "
                        + "(old primary committed it before dying) -> marked COMMITTED");
                } else {
                    forget(op.operationId());
                    log("in-doubt op " + op.operationId() + ": PREPARED here but NO booking in the database "
                        + "(old primary died before committing) -> discarded, the client's retry will run it fresh");
                }
            } catch (Exception e) {
                log("could not resolve in-doubt op " + op.operationId() + " (" + e.getMessage() + ") -- leaving it PREPARED");
            }
        }
    }

    // ------------------------------------------------------------------
    // Replication: what this node does when its peer is the primary
    // ------------------------------------------------------------------

    @Override
    public boolean prepare(OperationRecord op) throws RemoteException {
        if (role == Role.ACTIVE_PRIMARY) {
            log("refusing PREPARE for " + op.operationId() + " -- I am an ACTIVE PRIMARY myself");
            return false;
        }
        remember(op);
        log("PREPARE received: opId=" + op.operationId() + " user=" + op.userName() + " -> logged as PREPARED, sending ACK");
        return true;
    }

    @Override
    public void commit(OperationRecord op) throws RemoteException {
        remember(op);
        log("COMMIT received: opId=" + op.operationId() + " -> replica record is now COMMITTED (" + op.result() + ")");
    }

    @Override
    public void abort(String operationId) throws RemoteException {
        forget(operationId);
        log("ABORT received: opId=" + operationId + " -> PREPARED record dropped");
    }

    // ------------------------------------------------------------------
    // Recovery: what this node does when its peer is the one recovering
    // ------------------------------------------------------------------

    @Override
    public List<OperationRecord> getCommittedOperations() throws RemoteException {
        List<OperationRecord> ops = committedSnapshot();
        log("peer asked for my committed operations -> sending " + ops.size());
        return ops;
    }

    @Override
    public List<OperationRecord> stepDownToStandby() throws RemoteException {
        synchronized (roleLock) {
            if (role == Role.ACTIVE_PRIMARY) {
                role = Role.STANDBY;
                log("STEP DOWN requested by the recovering Primary: ACTIVE PRIMARY -> STANDBY");
            }
            return committedSnapshot();
        }
    }

    // ------------------------------------------------------------------
    // Failure simulation
    // ------------------------------------------------------------------

    // Arms a one-shot fault: the next booking dies right after its database
    // commit (see bookSeat).
    public void armCrashAfterDbCommit() {
        crashAfterDbCommit = true;
        log("fault injection armed: will crash right after the next database commit");
    }

    public void crash() {
        log("*** CRASHED (process terminated) ***");
        if (haltJvmOnCrash) {
            // Immediate death: no shutdown hooks, no cleanup -- like kill -9.
            Runtime.getRuntime().halt(1);
        }
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception ignored) {
        }
        try {
            UnicastRemoteObject.unexportObject(registry, true);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public NodeStatus status() {
        synchronized (opLog) {
            return new NodeStatus(node, role, (int) opLog.values().stream()
                .filter(op -> op.state() == OperationRecord.State.COMMITTED).count());
        }
    }

    public Role getRole() {
        return role;
    }

    public void printReplicaLog() {
        List<OperationRecord> ops;
        synchronized (opLog) {
            ops = new ArrayList<>(opLog.values());
        }
        log("replica log (" + ops.size() + " entr" + (ops.size() == 1 ? "y" : "ies") + "), role = " + role);
        for (OperationRecord op : ops) {
            log("   " + op.operationId() + "  " + String.format("%-6s", op.userName()) + " "
                + String.format("%-9s", op.state()) + " executedBy=" + op.executedBy()
                + (op.result() == null ? "" : "  " + op.result()));
        }
    }

    private static String successMessage(String userName) {
        return "SUCCESS: seat booked for " + userName;
    }

    private NodeStatus peerStatus() {
        try {
            return Cluster.lookup(peerName).heartbeat();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean sendPrepare(OperationRecord op) {
        try {
            if (Cluster.lookup(peerName).prepare(op)) {
                log("PREPARE -> " + peerName + " : ACK received");
                return true;
            }
            log("PREPARE -> " + peerName + " : refused (it is not a STANDBY) -- continuing UNREPLICATED");
        } catch (Exception e) {
            log("PREPARE -> " + peerName + " : unreachable -- continuing UNREPLICATED (degraded mode)");
        }
        return false;
    }

    private void sendCommit(OperationRecord op) {
        try {
            Cluster.lookup(peerName).commit(op);
            log("COMMIT  -> " + peerName + " : replica record committed");
        } catch (Exception e) {
            log("COMMIT  -> " + peerName + " : unreachable (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void sendAbort(String operationId) {
        try {
            Cluster.lookup(peerName).abort(operationId);
        } catch (Exception ignored) {
        }
    }

    private OperationRecord lookupOp(String operationId) {
        synchronized (opLog) {
            return opLog.get(operationId);
        }
    }

    // Never downgrades a COMMITTED record back to PREPARED.
    private void remember(OperationRecord op) {
        synchronized (opLog) {
            OperationRecord old = opLog.get(op.operationId());
            if (old == null || old.state() != OperationRecord.State.COMMITTED) {
                opLog.put(op.operationId(), op);
            }
        }
    }

    private void forget(String operationId) {
        synchronized (opLog) {
            opLog.remove(operationId);
        }
    }

    // Adds records this node doesn't have yet; returns how many were new.
    private int merge(List<OperationRecord> ops) {
        int added = 0;
        synchronized (opLog) {
            for (OperationRecord op : ops) {
                OperationRecord old = opLog.get(op.operationId());
                if (old == null || old.state() != OperationRecord.State.COMMITTED) {
                    opLog.put(op.operationId(), op);
                    added++;
                }
            }
        }
        return added;
    }

    private List<OperationRecord> committedSnapshot() {
        List<OperationRecord> ops = new ArrayList<>();
        synchronized (opLog) {
            for (OperationRecord op : opLog.values()) {
                if (op.state() == OperationRecord.State.COMMITTED) ops.add(op);
            }
        }
        return ops;
    }

    private void log(String msg) {
        Log.log(node, msg);
    }
}
