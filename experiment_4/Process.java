import java.rmi.Remote;
import java.rmi.RemoteException;

// What one client process exposes to its peers. There is no separate
// server node in this experiment -- every P1..P5 is both caller and
// callee of this interface.
public interface Process extends Remote {

    // Called by a lower-ID candidate on a higher-ID peer during an
    // election. A true reply means "I'm alive and outrank you, stand
    // down" -- per the Bully rule, receiving this also makes the callee
    // start its own election if it hasn't already.
    boolean election(int candidateId) throws RemoteException;

    // One-way announcement from the winning process to everyone else.
    void announceCoordinator(int newLeaderId) throws RemoteException;

    // Cheap liveness check, used before forwarding a "real" request to
    // whoever this process currently believes is the leader.
    boolean ping() throws RemoteException;
}
