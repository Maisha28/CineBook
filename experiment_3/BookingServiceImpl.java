import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.*;

public class BookingServiceImpl extends UnicastRemoteObject implements BookingService {

    // ---- fill these in from Supabase: Project Settings -> Database ----
    private static final String DB_URL =
        "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres";
    private static final String DB_USER = "postgres.xjhdsoiaiqsgujwjocnx";
    private static final String DB_PASSWORD = "MovieTicketBooker123";
    // ---------------------------------------------------------------

    // ONE shared Lamport clock for the whole server. Every incoming request
    // (from any client thread) advances this same clock using the receive
    // rule. This is what gives us a single logical ordering of events as
    // observed by the server.
    private final LamportClock serverLamportClock = new LamportClock();

    protected BookingServiceImpl() throws RemoteException {
        super();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    @Override
    public List<String> getAvailableSeats(String showId) throws RemoteException {
        List<String> seats = new ArrayList<>();
        String sql = "SELECT id, seat_number FROM seats WHERE show_id = ?::uuid AND status = 'available' ORDER BY seat_number";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seats.add(rs.getString("seat_number") + " (id=" + rs.getString("id") + ")");
                }
            }
        } catch (SQLException e) {
            throw new RemoteException("DB error while fetching seats: " + e.getMessage(), e);
        }
        return seats;
    }

    // synchronized so two simultaneous calls can't both "see" the same
    // available seat and double-book it. This is the ONLY thing that
    // decides who actually gets the seat -- Lamport timestamps below are
    // for logging/ordering only and never influence this decision.
    @Override
    public synchronized String bookSeat(String seatId, String userName, long lamportTimestamp) throws RemoteException {

        // Lamport receive rule: fold the caller's logical timestamp into
        // our own clock. This gives this booking *event* a logical position
        // in the server's timeline, but says nothing about which network
        // packet physically arrived first if there was contention -- that's
        // still decided below by whichever thread wins the synchronized lock.
        long serverLogicalTime = serverLamportClock.update(lamportTimestamp);

        System.out.println("[" + Thread.currentThread().getName() + "] "
            + userName + " request received (clientLamport=" + lamportTimestamp
            + ") -> serverLamport=" + serverLogicalTime);

        String checkSql = "SELECT status FROM seats WHERE id = ?::uuid";
        String updateSql = "UPDATE seats SET status = 'booked' WHERE id = ?::uuid";
        String insertSql = "INSERT INTO bookings (seat_id, user_name, server_node, lamport_timestamp) VALUES (?::uuid, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            String currentStatus = null;
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setString(1, seatId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) currentStatus = rs.getString("status");
                }
            }

            if (!"available".equals(currentStatus)) {
                conn.rollback();
                return "FAILED: seat not available [serverLamport=" + serverLogicalTime + "]";
            }

            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setString(1, seatId);
                update.executeUpdate();
            }

            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                insert.setString(1, seatId);
                insert.setString(2, userName);
                insert.setString(3, "node1");
                insert.setLong(4, serverLogicalTime);
                insert.executeUpdate();
            }

            conn.commit();
            System.out.println("[" + Thread.currentThread().getName() + "] "
                + userName + " -> SUCCESS (serverLamport=" + serverLogicalTime + ")");
            return "SUCCESS: seat booked for " + userName + " [serverLamport=" + serverLogicalTime + "]";
        } catch (SQLException e) {
            throw new RemoteException("DB error while booking seat: " + e.getMessage(), e);
        }
    }

    // --- Berkeley coordinator logic ---
    @Override
    public Map<String, Long> synchronizeClocks(Map<String, Long> clientTimes) throws RemoteException {
        long myTime = System.currentTimeMillis();

        // Include the server itself as a participant, same as classic Berkeley.
        Map<String, Long> allTimes = new LinkedHashMap<>();
        allTimes.put("SERVER", myTime);
        allTimes.putAll(clientTimes);

        long sum = 0;
        for (long t : allTimes.values()) sum += t;
        long average = sum / allTimes.size();

        System.out.println("[Berkeley] Server time = " + myTime + ", average across "
            + allTimes.size() + " participants = " + average);

        Map<String, Long> corrections = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : allTimes.entrySet()) {
            long correction = average - entry.getValue();
            corrections.put(entry.getKey(), correction);
            System.out.println("[Berkeley]   " + entry.getKey()
                + " offset=" + entry.getValue() + " -> correction=" + correction + "ms");
        }
        return corrections;
    }

    @Override
    public long getServerTime() throws RemoteException {
        return System.currentTimeMillis();
    }
}