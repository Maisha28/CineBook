import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * RMI stub interface for Experiments 1 & 2.
 * Matches Backend/experiment_1/BookingService.java exactly.
 */
public interface BookingService extends Remote {
    List<String> getAvailableSeats(String showId) throws RemoteException;
    String bookSeat(String seatId, String userName) throws RemoteException;
}
