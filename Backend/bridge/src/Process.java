import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI stub for Experiment 4 Process interface.
 * Matches Backend/experiment_4/Process.java exactly.
 */
public interface Process extends Remote {
    boolean election(int candidateId) throws RemoteException;
    void announceCoordinator(int newLeaderId) throws RemoteException;
    boolean ping() throws RemoteException;
}
