import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

// The "contract" the client and server both agree on.
// Any method here can be called by a remote client as if it were local.
public interface BookingService extends Remote {

    List<String> getAvailableSeats(String showId) throws RemoteException;

    // Added lamportTimestamp: the CALLER's logical clock value at the moment
    // it decided to send this request (after ticking its own LamportClock).
    // This is ONLY used for logical event ordering/logging. It plays no
    // role in deciding who actually gets the seat -- that is still handled
    // entirely by the existing synchronized critical section + DB check.
    String bookSeat(String seatId, String userName, long lamportTimestamp) throws RemoteException;

    // --- Berkeley physical clock synchronization ---
    //
    // The RMI server acts as the Berkeley coordinator. A caller (ClockDemo)
    // reports a set of participant clock readings -- clientId -> that
    // client's current application time in millis (which may be a
    // simulated, deliberately-offset value for demo purposes). The server:
    //   1. adds its own current time as a participant
    //   2. averages all participant times
    //   3. computes each participant's correction = average - participantTime
    // and returns clientId -> correctionOffsetMillis.
    //
    // Callers apply the offset locally as:
    //   correctedTime = clientCurrentTime + correctionOffset
    //
    // The server's own OS clock is never touched -- this is purely an
    // application-level correction value.
    Map<String, Long> synchronizeClocks(Map<String, Long> clientTimes) throws RemoteException;

    // Lets a caller ask the coordinator for its raw current time, mainly so
    // ClockDemo can display "server's actual time" before running Berkeley.
    long getServerTime() throws RemoteException;
}