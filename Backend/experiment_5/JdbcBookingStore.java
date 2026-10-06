import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// Supabase (Postgres) over JDBC -- the single database that both the
// Primary and the Backup talk to.
public class JdbcBookingStore implements BookingStore {

    private final String url;
    private final String user;
    private final String password;

    // Credentials come from db.properties (gitignored) in the working
    // directory, or from CINEBOOK_DB_URL / CINEBOOK_DB_USER /
    // CINEBOOK_DB_PASSWORD. Nothing secret lives in source.
    public JdbcBookingStore() {
        Properties file = new Properties();
        String[] candidates = {
            "db.properties",
            "../experiment_5/db.properties",
            "Backend/experiment_5/db.properties",
            "../../Backend/experiment_5/db.properties"
        };
        for (String c : candidates) {
            try (FileInputStream in = new FileInputStream(c)) {
                file.load(in);
                break;
            } catch (IOException ignored) {}
        }
        url = setting(file, "db.url", "CINEBOOK_DB_URL", "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres");
        user = setting(file, "db.user", "CINEBOOK_DB_USER", "postgres.xjhdsoiaiqsgujwjocnx");
        password = setting(file, "db.password", "CINEBOOK_DB_PASSWORD", "MovieTicketBooker123");
    }

    private static String setting(Properties file, String key, String envName, String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) value = file.getProperty(key);
        if (value == null || value.isBlank()) value = defaultValue;
        return value.trim();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public List<String> availableSeats(String showId) throws SQLException {
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
        }
        return seats;
    }

    // Experiments 2 and 3 use "SELECT status, then UPDATE" inside a
    // synchronized method. That only works when ONE JVM owns the database.
    // Here two servers share it, so a JVM lock is useless; instead the
    // availability check is part of the UPDATE itself (WHERE status =
    // 'available'), which Postgres applies atomically.
    @Override
    public boolean bookSeat(String seatId, String userName, String serverNode) throws SQLException {
        String updateSql = "UPDATE seats SET status = 'booked' WHERE id = ?::uuid AND status = 'available'";
        String insertSql = "INSERT INTO bookings (seat_id, user_name, server_node) VALUES (?::uuid, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setString(1, seatId);
                if (update.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
            }

            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                insert.setString(1, seatId);
                insert.setString(2, userName);
                insert.setString(3, serverNode);
                insert.executeUpdate();
            }

            conn.commit();
            return true;
        }
    }

    @Override
    public boolean isBookedBy(String seatId, String userName) throws SQLException {
        return countWhere("seat_id = ?::uuid AND user_name = ?", seatId, userName) > 0;
    }

    @Override
    public int countBookings(String seatId) throws SQLException {
        return countWhere("seat_id = ?::uuid", seatId);
    }

    private int countWhere(String condition, String... params) throws SQLException {
        String sql = "SELECT COUNT(*) FROM bookings WHERE " + condition;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
