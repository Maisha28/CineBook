import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * CineBook Supabase Catalog & Show Handler
 * Connects directly to Supabase PostgreSQL database to serve real movies,
 * theatres, shows, live seat statuses, and persist real bookings.
 */
public class CatalogHandler {

    private static final String DB_URL = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres";
    private static final String DB_USER = "postgres.xjhdsoiaiqsgujwjocnx";
    private static final String DB_PASSWORD = "MovieTicketBooker123";

    private static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ignored) {}
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static class SeatInfo {
        public String id;
        public String seatNumber;
        public String tier;
        public int price;
        public String status;
        public String bookedBy;

        public SeatInfo(String id, String seatNumber, String tier, int price, String status) {
            this.id = id;
            this.seatNumber = seatNumber;
            this.tier = tier;
            this.price = price;
            this.status = status;
        }
    }

    /** GET /api/movies */
    public static void getMovies(HttpExchange ex) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, title, duration_minutes, genre FROM movies ORDER BY title")) {

            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                String id = rs.getString("id");
                String title = rs.getString("title");
                m.put("id", id);
                m.put("title", title);
                m.put("duration", rs.getInt("duration_minutes") + " min");
                m.put("genre", rs.getString("genre"));
                m.put("rating", title.contains("Signal") ? 8.9 : 8.6);
                m.put("certificate", "UA");

                if (title.contains("Signal")) {
                    m.put("poster", "/posters/the-signal.jpg");
                    m.put("synopsis", "A deep-space observatory detects an anomalous interstellar pulse with rhythmic patterns. A specialized crew embarks on an expedition beyond the solar boundary to decipher the signal.");
                    m.put("cast", Arrays.asList("Aura Volkov", "Kairo Jensen", "Seraphina Reed"));
                    m.put("formats", Arrays.asList("IMAX 3D", "Dolby Atmos 2D"));
                } else {
                    m.put("poster", "/posters/late-monsoon.jpg");
                    m.put("synopsis", "Set against the reflective rain-swept avenues of an old metropolis, two estranged souls cross paths under late-monsoon downpours, uncovering forgotten secrets.");
                    m.put("cast", Arrays.asList("Liu Wei", "Anna Chen", "David Lee"));
                    m.put("formats", Arrays.asList("Dolby Cinema", "2D"));
                }
                list.add(m);
            }
        } catch (Exception e) {
            System.err.println("[CatalogHandler] Supabase movies fetch error: " + e.getMessage());
        }

        // Output JSON
        StringBuilder sb = new StringBuilder("{\"movies\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> m = list.get(i);
            sb.append("{")
              .append("\"id\":").append(BridgeServer.jsonStr((String) m.get("id"))).append(",")
              .append("\"title\":").append(BridgeServer.jsonStr((String) m.get("title"))).append(",")
              .append("\"genre\":").append(BridgeServer.jsonStr((String) m.get("genre"))).append(",")
              .append("\"duration\":").append(BridgeServer.jsonStr((String) m.get("duration"))).append(",")
              .append("\"rating\":").append(m.get("rating")).append(",")
              .append("\"certificate\":").append(BridgeServer.jsonStr((String) m.get("certificate"))).append(",")
              .append("\"poster\":").append(BridgeServer.jsonStr((String) m.get("poster"))).append(",")
              .append("\"synopsis\":").append(BridgeServer.jsonStr((String) m.get("synopsis"))).append(",")
              .append("\"cast\":[");
            List<String> cast = (List<String>) m.get("cast");
            for (int c = 0; c < cast.size(); c++) {
                if (c > 0) sb.append(",");
                sb.append(BridgeServer.jsonStr(cast.get(c)));
            }
            sb.append("],\"formats\":[");
            List<String> formats = (List<String>) m.get("formats");
            for (int f = 0; f < formats.size(); f++) {
                if (f > 0) sb.append(",");
                sb.append(BridgeServer.jsonStr(formats.get(f)));
            }
            sb.append("]}");
        }
        sb.append("]}");
        BridgeServer.json(ex, 200, sb.toString());
    }

    /** GET /api/movies/:id */
    public static void getMovieById(HttpExchange ex, String id) throws IOException {
        try (Connection conn = getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT id, title, duration_minutes, genre FROM movies WHERE id = ?::uuid");
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("title");
                    String poster = title.contains("Signal") ? "/posters/the-signal.jpg" : "/posters/late-monsoon.jpg";
                    String synopsis = title.contains("Signal")
                        ? "A deep-space observatory detects an anomalous interstellar pulse with rhythmic patterns. A specialized crew embarks on an expedition beyond the solar boundary to decipher the signal."
                        : "Set against the reflective rain-swept avenues of an old metropolis, two estranged souls cross paths under late-monsoon downpours.";

                    // Fetch shows for this movie
                    StringBuilder showsSb = new StringBuilder("[");
                    int sCount = 0;
                    String showSql = "SELECT s.id, s.show_time, s.total_seats, t.id AS theatre_id, t.name AS theatre_name, t.location "
                                   + "FROM shows s JOIN theatres t ON s.theatre_id = t.id WHERE s.movie_id = ?::uuid ORDER BY s.show_time";
                    try (PreparedStatement psShows = conn.prepareStatement(showSql)) {
                        psShows.setString(1, id);
                        try (ResultSet rsShows = psShows.executeQuery()) {
                            while (rsShows.next()) {
                                if (sCount++ > 0) showsSb.append(",");
                                String format = title.contains("Signal") ? "IMAX 3D" : "Dolby 2D";
                                int price = title.contains("Signal") ? 380 : 280;
                                showsSb.append("{")
                                       .append("\"id\":").append(BridgeServer.jsonStr(rsShows.getString("id"))).append(",")
                                       .append("\"cinemaId\":").append(BridgeServer.jsonStr(rsShows.getString("theatre_id"))).append(",")
                                       .append("\"cinemaName\":").append(BridgeServer.jsonStr(rsShows.getString("theatre_name"))).append(",")
                                       .append("\"location\":").append(BridgeServer.jsonStr(rsShows.getString("location"))).append(",")
                                       .append("\"time\":").append(BridgeServer.jsonStr(rsShows.getString("show_time"))).append(",")
                                       .append("\"format\":").append(BridgeServer.jsonStr(format)).append(",")
                                       .append("\"price\":").append(price)
                                       .append("}");
                            }
                        }
                    }
                    showsSb.append("]");

                    String json = "{"
                        + "\"id\":" + BridgeServer.jsonStr(rs.getString("id")) + ","
                        + "\"title\":" + BridgeServer.jsonStr(title) + ","
                        + "\"genre\":" + BridgeServer.jsonStr(rs.getString("genre")) + ","
                        + "\"duration\":" + BridgeServer.jsonStr(rs.getInt("duration_minutes") + " min") + ","
                        + "\"rating\":" + (title.contains("Signal") ? 8.9 : 8.6) + ","
                        + "\"certificate\":\"UA\","
                        + "\"poster\":" + BridgeServer.jsonStr(poster) + ","
                        + "\"synopsis\":" + BridgeServer.jsonStr(synopsis) + ","
                        + "\"shows\":" + showsSb.toString()
                        + "}";
                    BridgeServer.json(ex, 200, json);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[CatalogHandler] Supabase getMovieById error: " + e.getMessage());
        }
        BridgeServer.json(ex, 404, "{\"error\":\"Movie not found\"}");
    }

    /** GET /api/supabase/table?name=movies|theatres|shows|seats|bookings */
    public static void getTableData(HttpExchange ex) throws IOException {
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String tableName = q.get("name");
        if (tableName == null || tableName.isBlank()) tableName = "movies";

        Set<String> allowed = new HashSet<>(Arrays.asList("movies", "theatres", "shows", "seats", "bookings"));
        if (!allowed.contains(tableName.toLowerCase())) {
            BridgeServer.json(ex, 400, "{\"error\":\"Invalid table name\"}");
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " ORDER BY 1 LIMIT 50")) {

            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();

            StringBuilder colsSb = new StringBuilder("[");
            for (int i = 1; i <= cols; i++) {
                if (i > 1) colsSb.append(",");
                colsSb.append("{")
                      .append("\"name\":").append(BridgeServer.jsonStr(md.getColumnName(i))).append(",")
                      .append("\"type\":").append(BridgeServer.jsonStr(md.getColumnTypeName(i)))
                      .append("}");
            }
            colsSb.append("]");

            StringBuilder rowsSb = new StringBuilder("[");
            int rCount = 0;
            while (rs.next()) {
                if (rCount++ > 0) rowsSb.append(",");
                rowsSb.append("{");
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) rowsSb.append(",");
                    String val = rs.getString(i);
                    rowsSb.append(BridgeServer.jsonStr(md.getColumnName(i))).append(":").append(BridgeServer.jsonStr(val));
                }
                rowsSb.append("}");
            }
            rowsSb.append("]");

            String json = "{"
                + "\"table\":" + BridgeServer.jsonStr(tableName) + ","
                + "\"columns\":" + colsSb + ","
                + "\"rows\":" + rowsSb + ","
                + "\"total\":" + rCount
                + "}";
            BridgeServer.json(ex, 200, json);
        } catch (Exception e) {
            BridgeServer.json(ex, 500, "{\"error\":" + BridgeServer.jsonStr(e.getMessage()) + "}");
        }
    }

    /** GET /api/cinemas */
    public static void getCinemas(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, location, server_node FROM theatres ORDER BY name")) {

            while (rs.next()) {
                if (count++ > 0) sb.append(",");
                sb.append("{")
                  .append("\"id\":").append(BridgeServer.jsonStr(rs.getString("id"))).append(",")
                  .append("\"name\":").append(BridgeServer.jsonStr(rs.getString("name"))).append(",")
                  .append("\"location\":").append(BridgeServer.jsonStr(rs.getString("location"))).append(",")
                  .append("\"serverNode\":").append(BridgeServer.jsonStr(rs.getString("server_node")))
                  .append("}");
            }
        } catch (Exception e) {
            System.err.println("[CatalogHandler] Supabase theatres error: " + e.getMessage());
        }

        sb.append("]");
        BridgeServer.json(ex, 200, "{\"cinemas\":" + sb + "}");
    }

    /** GET /api/shows */
    public static void getShows(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        String sql = "SELECT s.id, s.movie_id, m.title AS movie_title, s.theatre_id, t.name AS theatre_name, "
                   + "s.show_time, s.total_seats "
                   + "FROM shows s "
                   + "JOIN movies m ON s.movie_id = m.id "
                   + "JOIN theatres t ON s.theatre_id = t.id "
                   + "ORDER BY s.show_time";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                if (count++ > 0) sb.append(",");
                String title = rs.getString("movie_title");
                int price = title.contains("Signal") ? 380 : 280;
                String format = title.contains("Signal") ? "IMAX 3D" : "Dolby 2D";

                sb.append("{")
                  .append("\"id\":").append(BridgeServer.jsonStr(rs.getString("id"))).append(",")
                  .append("\"movieId\":").append(BridgeServer.jsonStr(rs.getString("movie_id"))).append(",")
                  .append("\"movieTitle\":").append(BridgeServer.jsonStr(title)).append(",")
                  .append("\"cinemaId\":").append(BridgeServer.jsonStr(rs.getString("theatre_id"))).append(",")
                  .append("\"cinemaName\":").append(BridgeServer.jsonStr(rs.getString("theatre_name"))).append(",")
                  .append("\"time\":").append(BridgeServer.jsonStr(rs.getString("show_time"))).append(",")
                  .append("\"totalSeats\":").append(rs.getInt("total_seats")).append(",")
                  .append("\"format\":").append(BridgeServer.jsonStr(format)).append(",")
                  .append("\"price\":").append(price)
                  .append("}");
            }
        } catch (Exception e) {
            System.err.println("[CatalogHandler] Supabase shows error: " + e.getMessage());
        }

        sb.append("]");
        BridgeServer.json(ex, 200, "{\"shows\":" + sb + "}");
    }

    /** GET /api/seats?showId=<uuid> */
    public static void getSeats(HttpExchange ex) throws IOException {
        Map<String, String> q = BridgeServer.parseQuery(ex.getRequestURI().getQuery());
        String showId = q.get("showId");
        if (showId == null || showId.isEmpty()) {
            // Default to first show if not specified
            showId = "8fd0925d-364b-485e-837c-b2aaea27fa56";
        }

        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        String sql = "SELECT id, seat_number, status FROM seats WHERE show_id = ?::uuid ORDER BY seat_number, id";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (count++ > 0) sb.append(",");
                    String seatNum = rs.getString("seat_number");
                    String tier = seatNum.startsWith("A") ? "Recliner" : "Prime";
                    int price = seatNum.startsWith("A") ? 420 : 320;

                    sb.append("{")
                      .append("\"id\":").append(BridgeServer.jsonStr(rs.getString("id"))).append(",")
                      .append("\"seatNumber\":").append(BridgeServer.jsonStr(seatNum)).append(",")
                      .append("\"tier\":").append(BridgeServer.jsonStr(tier)).append(",")
                      .append("\"price\":").append(price).append(",")
                      .append("\"status\":").append(BridgeServer.jsonStr(rs.getString("status")))
                      .append("}");
                }
            }
        } catch (Exception e) {
            System.err.println("[CatalogHandler] Supabase seats query error: " + e.getMessage());
        }

        sb.append("]");
        BridgeServer.json(ex, 200, "{\"showId\":" + BridgeServer.jsonStr(showId) + ",\"seats\":" + sb + ",\"source\":\"supabase\"}");
    }

    /** Atomically reserve a seat in Supabase and create a booking */
    public static boolean bookSeatAtomic(String seatId, String userName, String serverNode, Long lamportTs) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String updateSql = "UPDATE seats SET status = 'booked' WHERE id = ?::uuid AND status = 'available'";
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setString(1, seatId);
                int updated = psUpdate.executeUpdate();
                if (updated == 0) {
                    conn.rollback();
                    return false;
                }
            }

            String bookingId = UUID.randomUUID().toString();
            String insertSql = "INSERT INTO bookings (id, seat_id, user_name, booked_at, server_node, lamport_timestamp) "
                             + "VALUES (?::uuid, ?::uuid, ?, NOW(), ?, ?)";
            try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                psInsert.setString(1, bookingId);
                psInsert.setString(2, seatId);
                psInsert.setString(3, userName);
                psInsert.setString(4, (serverNode != null && !serverNode.isEmpty()) ? serverNode : "node1");
                if (lamportTs != null) {
                    psInsert.setLong(5, lamportTs);
                } else {
                    psInsert.setNull(5, Types.INTEGER);
                }
                psInsert.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            System.err.println("[CatalogHandler] Supabase atomic booking error: " + e.getMessage());
            return false;
        }
    }

    /** POST /api/book */
    public static void bookSeatDirect(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { BridgeServer.json(ex, 405, "{\"error\":\"POST only\"}"); return; }
        String body = BridgeServer.bodyString(ex);
        String seatId = jsonField(body, "seatId");
        String userName = jsonField(body, "userName");
        String serverNode = jsonField(body, "serverNode");
        if (serverNode == null || serverNode.isEmpty()) serverNode = "node1";

        if (seatId == null || userName == null) {
            BridgeServer.json(ex, 400, "{\"error\":\"seatId and userName required\"}");
            return;
        }

        boolean ok = bookSeatAtomic(seatId, userName, serverNode, null);
        if (ok) {
            BridgeServer.json(ex, 200, "{\"success\":true,\"seatId\":" + BridgeServer.jsonStr(seatId)
                + ",\"userName\":" + BridgeServer.jsonStr(userName)
                + ",\"serverNode\":" + BridgeServer.jsonStr(serverNode)
                + ",\"message\":\"Booking permanently confirmed in Supabase!\"}");
        } else {
            BridgeServer.json(ex, 200, "{\"success\":false,\"message\":\"Seat is no longer available (already booked)\"}");
        }
    }

    /** GET /api/bookings */
    public static void getBookings(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;

        String sql = "SELECT b.id, b.seat_id, b.user_name, b.booked_at, b.server_node, b.lamport_timestamp, "
                   + "s.seat_number, sh.id AS show_id, m.title AS movie_title "
                   + "FROM bookings b "
                   + "LEFT JOIN seats s ON b.seat_id = s.id "
                   + "LEFT JOIN shows sh ON s.show_id = sh.id "
                   + "LEFT JOIN movies m ON sh.movie_id = m.id "
                   + "ORDER BY b.booked_at DESC LIMIT 50";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                if (count++ > 0) sb.append(",");
                sb.append("{")
                  .append("\"id\":").append(BridgeServer.jsonStr(rs.getString("id"))).append(",")
                  .append("\"seatId\":").append(BridgeServer.jsonStr(rs.getString("seat_id"))).append(",")
                  .append("\"userName\":").append(BridgeServer.jsonStr(rs.getString("user_name"))).append(",")
                  .append("\"bookedAt\":").append(BridgeServer.jsonStr(rs.getString("booked_at"))).append(",")
                  .append("\"serverNode\":").append(BridgeServer.jsonStr(rs.getString("server_node"))).append(",")
                  .append("\"seatNumber\":").append(BridgeServer.jsonStr(rs.getString("seat_number"))).append(",")
                  .append("\"movieTitle\":").append(BridgeServer.jsonStr(rs.getString("movie_title") != null ? rs.getString("movie_title") : "The Signal"))
                  .append("}");
            }
        } catch (Exception e) {
            System.err.println("[CatalogHandler] Supabase bookings query error: " + e.getMessage());
        }

        sb.append("]");
        BridgeServer.json(ex, 200, "{\"bookings\":" + sb + ",\"total\":" + count + "}");
    }

    /** GET /api/supabase/inspect */
    public static void inspectSupabase(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("{");

        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            sb.append("\"database\":").append(BridgeServer.jsonStr(meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion())).append(",");

            String[] tables = {"movies", "theatres", "shows", "seats", "bookings"};
            sb.append("\"tables\":{");

            for (int i = 0; i < tables.length; i++) {
                String t = tables[i];
                if (i > 0) sb.append(",");
                int rowCount = 0;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + t)) {
                    if (rs.next()) rowCount = rs.getInt(1);
                }
                sb.append(BridgeServer.jsonStr(t)).append(":").append(rowCount);
            }
            sb.append("}}");
        } catch (Exception e) {
            sb.append("\"error\":").append(BridgeServer.jsonStr(e.getMessage())).append("}");
        }

        BridgeServer.json(ex, 200, sb.toString());
    }

    /** GET /api/system/status */
    public static void getSystemStatus(HttpExchange ex) throws IOException {
        boolean dbLive = false;
        try (Connection conn = getConnection()) {
            dbLive = conn.isValid(2);
        } catch (Exception ignored) {}

        String json = "{"
            + "\"bridge\":\"online\","
            + "\"port\":8080,"
            + "\"rmiPort\":1099,"
            + "\"database\":\"PostgreSQL (Supabase Live Active)\","
            + "\"supabaseConnected\":" + dbLive + ","
            + "\"timestamp\":" + System.currentTimeMillis()
            + "}";
        BridgeServer.json(ex, 200, json);
    }

    /** Helper for Exp12Handler fallback compatibility */
    public static Map<String, SeatInfo> getShowSeats(String showId) {
        Map<String, SeatInfo> map = new LinkedHashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, seat_number, status FROM seats WHERE show_id = ?::uuid ORDER BY seat_number")) {
            ps.setString(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String seatId = rs.getString("id");
                    String seatNum = rs.getString("seat_number");
                    String status = rs.getString("status");
                    map.put(seatId, new SeatInfo(seatId, seatNum, "Prime", 320, status));
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    public static String bookFallbackSeat(String seatId, String userName) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE seats SET status = 'booked' WHERE id = ?::uuid AND status = 'available'")) {
            ps.setString(1, seatId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (PreparedStatement ps2 = conn.prepareStatement("INSERT INTO bookings (id, seat_id, user_name, booked_at, server_node) VALUES (?::uuid, ?::uuid, ?, NOW(), 'fallback')")) {
                    ps2.setString(1, UUID.randomUUID().toString());
                    ps2.setString(2, seatId);
                    ps2.setString(3, userName);
                    ps2.executeUpdate();
                }
                return "SUCCESS: seat booked for " + userName;
            }
        } catch (Exception ignored) {}
        return "FAILED: seat not available";
    }

    private static String jsonField(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx);
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }
}
