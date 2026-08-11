import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.*;

public class BookingServiceImpl extends UnicastRemoteObject implements BookingService {

// ---- fill these in from Supabase: Project Settings -> Database ----
private static final String DB_URL =
"jdbc:postgresql://db.YOUR_PROJECT_REF.supabase.co:5432/postgres";
private static final String DB_USER = "postgres";
private static final String DB_PASSWORD = "YOUR_DB_PASSWORD";
// ---------------------------------------------------------------

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
// available seat and double-book it. Remove this keyword in Experiment 2
// to demonstrate the race condition, then add it back to fix it.
@Override
public synchronized String bookSeat(String seatId, String userName) throws RemoteException {
String checkSql = "SELECT status FROM seats WHERE id = ?::uuid";
String updateSql = "UPDATE seats SET status = 'booked' WHERE id = ?::uuid";
String insertSql = "INSERT INTO bookings (seat_id, user_name, server_node) VALUES (?::uuid, ?, ?)";

try (Connection conn = getConnection()) {
conn.setAutoCommit(false);

try (PreparedStatement check = conn.prepareStatement(checkSql)) {
check.setString(1, seatId);
try (ResultSet rs = check.executeQuery()) {
if (!rs.next() || !"available".equals(rs.getString("status"))) {
conn.rollback();
return "FAILED: seat not available";
}
}
}

try (PreparedStatement update = conn.prepareStatement(updateSql)) {
update.setString(1, seatId);
update.executeUpdate();
}

try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
insert.setString(1, seatId);
insert.setString(2, userName);
insert.setString(3, "node1");
insert.executeUpdate();
}

conn.commit();
return "SUCCESS: seat booked for " + userName;
} catch (SQLException e) {
throw new RemoteException("DB error while booking seat: " + e.getMessage(), e);
}
}
}
