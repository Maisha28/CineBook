import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

// The "contract" the client and server both agree on.
// Any method here can be called by a remote client as if it were local.
public interface BookingService extends Remote {
List<String> getAvailableSeats(String showId) throws RemoteException;
String bookSeat(String seatId, String userName) throws RemoteException;
}
