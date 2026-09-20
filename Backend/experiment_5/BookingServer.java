import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

// What every CineBook server exposes over RMI. Both the Primary node and the
// Backup node implement this same interface; which of the three groups
// below actually gets used depends on the caller and on the node's Role.
public interface BookingServer extends Remote {

    // ---- 1. Client-facing ----

    // Reads are safe on any node (both share one database), so this is not
    // restricted to the ACTIVE_PRIMARY.
    List<String> getAvailableSeats(String showId) throws RemoteException;

    // operationId is generated once by the client and reused on every retry.
    // Returns "SUCCESS: ...", "FAILED: ...", "ERROR: ..." or, if this node is
    // not the ACTIVE_PRIMARY, "NOT_PRIMARY: ...".
    String bookSeat(String operationId, String seatId, String userName) throws RemoteException;

    // Liveness probe. The client calls this once a second on the node it
    // believes is primary; the reply also says what role the node is playing.
    NodeStatus heartbeat() throws RemoteException;

    // Client-driven failover: STANDBY -> ACTIVE_PRIMARY. Idempotent.
    NodeStatus activate() throws RemoteException;

    // ---- 2. Synchronous replication (active primary -> its peer) ----

    // Phase 1: log the operation as PREPARED and acknowledge (true = ACK).
    boolean prepare(OperationRecord op) throws RemoteException;

    // Phase 2: the primary has committed the booking; make the replica
    // record COMMITTED with the final result.
    void commit(OperationRecord op) throws RemoteException;

    // The primary could not complete the booking; drop the PREPARED record.
    void abort(String operationId) throws RemoteException;

    // ---- 3. Recovery (restarted node -> currently active peer) ----

    // Every operation this node has committed.
    List<OperationRecord> getCommittedOperations() throws RemoteException;

    // Called by a recovering primary that wants its role back: this node
    // stops serving (ACTIVE_PRIMARY -> STANDBY) and returns its final list of
    // committed operations, so nothing booked in the meantime is lost.
    List<OperationRecord> stepDownToStandby() throws RemoteException;
}
