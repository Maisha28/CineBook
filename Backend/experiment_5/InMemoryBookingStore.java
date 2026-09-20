import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Stand-in for Supabase so FailoverDemo can run offline. One instance is
// shared by both servers, exactly like the one real database is. It only
// works inside a single JVM, so it is for FailoverDemo only, not for
// running Server in separate terminals.
public class InMemoryBookingStore implements BookingStore {

    private final Map<String, Boolean> booked = new LinkedHashMap<>();   // seatId -> is booked
    private final List<String[]> bookings = new ArrayList<>();           // {seatId, user, node}

    public InMemoryBookingStore(int seatCount) {
        for (int i = 1; i <= seatCount; i++) booked.put("mem-seat-" + i, false);
    }

    @Override
    public synchronized List<String> availableSeats(String showId) {
        List<String> seats = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, Boolean> e : booked.entrySet()) {
            n++;
            if (!e.getValue()) seats.add("A" + n + " (id=" + e.getKey() + ")");
        }
        return seats;
    }

    @Override
    public synchronized boolean bookSeat(String seatId, String userName, String serverNode) {
        Boolean isBooked = booked.get(seatId);
        if (isBooked == null || isBooked) return false;
        booked.put(seatId, true);
        bookings.add(new String[] {seatId, userName, serverNode});
        return true;
    }

    @Override
    public synchronized boolean isBookedBy(String seatId, String userName) {
        return bookings.stream().anyMatch(b -> b[0].equals(seatId) && b[1].equals(userName));
    }

    @Override
    public synchronized int countBookings(String seatId) {
        return (int) bookings.stream().filter(b -> b[0].equals(seatId)).count();
    }
}
