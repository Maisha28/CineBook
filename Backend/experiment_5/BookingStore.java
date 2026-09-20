import java.util.List;

// The database both servers share. It is an interface only so the demo can
// also run without Supabase (InMemoryBookingStore); the servers don't care.
public interface BookingStore {

    // "A1 (id=<seat uuid>)", same format as the earlier experiments.
    List<String> availableSeats(String showId) throws Exception;

    // Atomically claims the seat and records the booking. Returns false if
    // the seat was not available (already booked, or no such seat).
    boolean bookSeat(String seatId, String userName, String serverNode) throws Exception;

    // Is there a booking for this seat made by this user?
    boolean isBookedBy(String seatId, String userName) throws Exception;

    // How many booking rows exist for this seat (used by the demo to prove
    // a retried operation did not create a second one).
    int countBookings(String seatId) throws Exception;
}
