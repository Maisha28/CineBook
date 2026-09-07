import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * RMI stub interface for Experiment 3.
 * Matches Backend/experiment_3/BookingService.java exactly —
 * bookSeat takes an extra lamportTimestamp, and adds clock sync methods.
 */
public interface BookingService3 extends Remote {
    List<String> getAvailableSeats(String showId) throws RemoteException;
    String bookSeat(String seatId, String userName, long lamportTimestamp) throws RemoteException;
    Map<String, Long> synchronizeClocks(Map<String, Long> clientTimes) throws RemoteException;
    long getServerTime() throws RemoteException;
}
