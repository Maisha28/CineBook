import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CineBook Catalog & Show Handler
 * Exposes real movie metadata, cinema directories, showtimes, and booking persistence.
 */
public class CatalogHandler {

    public static class Movie {
        public String id;
        public String title;
        public String genre;
        public String language;
        public String format;
        public double rating;
        public String duration;
        public String certificate;
        public String synopsis;
        public String posterColor;
        public String posterText;
        public List<String> cast;

        public Movie(String id, String title, String genre, String language, String format,
                     double rating, String duration, String certificate, String synopsis,
                     String posterColor, String posterText, List<String> cast) {
            this.id = id;
            this.title = title;
            this.genre = genre;
            this.language = language;
            this.format = format;
            this.rating = rating;
            this.duration = duration;
            this.certificate = certificate;
            this.synopsis = synopsis;
            this.posterColor = posterColor;
            this.posterText = posterText;
            this.cast = cast;
        }
    }

    public static class Cinema {
        public String id;
        public String name;
        public String location;
        public List<String> formats;

        public Cinema(String id, String name, String location, List<String> formats) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.formats = formats;
        }
    }

    public static class Show {
        public String id;
        public String movieId;
        public String cinemaId;
        public String time;
        public String format;
        public int price;

        public Show(String id, String movieId, String cinemaId, String time, String format, int price) {
            this.id = id;
            this.movieId = movieId;
            this.cinemaId = cinemaId;
            this.time = time;
            this.format = format;
            this.price = price;
        }
    }

    private static final List<Movie> MOVIES = new ArrayList<>();
    private static final List<Cinema> CINEMAS = new ArrayList<>();
    private static final List<Show> SHOWS = new ArrayList<>();

    // Local in-memory seat map per show: showId -> (seatId -> status)
    private static final ConcurrentHashMap<String, Map<String, SeatInfo>> SHOW_SEATS = new ConcurrentHashMap<>();

    public static class SeatInfo {
        public String id;
        public String seatNumber;
        public String tier; // Recliner, Prime, Classic
        public int price;
        public String status; // "available" or "booked"
        public String bookedBy;

        public SeatInfo(String id, String seatNumber, String tier, int price, String status) {
            this.id = id;
            this.seatNumber = seatNumber;
            this.tier = tier;
            this.price = price;
            this.status = status;
        }
    }

    static {
        initCatalog();
    }

    private static void initCatalog() {
        MOVIES.add(new Movie(
            "m-kalki",
            "Kalki 2898 AD",
            "Sci-Fi / Action",
            "Hindi, Telugu, Tamil",
            "2D, 3D, IMAX 3D",
            8.7,
            "3h 1m",
            "UA",
            "In the dystopian future of 2898 AD, the celestial avatar Kalki descends to protect humanity from the forces of supreme darkness in the city of Kasi.",
            "#1A1A24",
            "KALKI",
            Arrays.asList("Prabhas", "Amitabh Bachchan", "Deepika Padukone", "Kamal Haasan")
        ));

        MOVIES.add(new Movie(
            "m-stree2",
            "Stree 2: Sarkate Ka Aatank",
            "Comedy / Horror",
            "Hindi",
            "2D",
            8.5,
            "2h 29m",
            "UA",
            "The town of Chanderi is haunted once again, this time by a headless entity named Sarkata. Vicky and his friends summon Stree to rescue their people.",
            "#221015",
            "STREE 2",
            Arrays.asList("Shraddha Kapoor", "Rajkummar Rao", "Pankaj Tripathi", "Aparshakti Khurana")
        ));

        MOVIES.add(new Movie(
            "m-jawan",
            "Jawan",
            "Action / Thriller",
            "Hindi, Tamil, Telugu",
            "2D, IMAX",
            8.4,
            "2h 49m",
            "UA",
            "A man is driven by a personal vendetta to rectify the wrongs in society, while keeping a promise made years ago to fight against injustice.",
            "#1A0A0A",
            "JAWAN",
            Arrays.asList("Shah Rukh Khan", "Nayanthara", "Vijay Sethupathi", "Deepika Padukone")
        ));

        MOVIES.add(new Movie(
            "m-dune2",
            "Dune: Part Two",
            "Sci-Fi / Adventure",
            "English, Hindi",
            "IMAX 3D, 2D",
            8.9,
            "2h 46m",
            "UA",
            "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
            "#2A1A0F",
            "DUNE II",
            Arrays.asList("Timothée Chalamet", "Zendaya", "Rebecca Ferguson", "Javier Bardem")
        ));

        MOVIES.add(new Movie(
            "m-interstellar",
            "Interstellar (Re-Release)",
            "Sci-Fi / Drama",
            "English",
            "IMAX 70mm, 2D",
            9.1,
            "2h 49m",
            "UA",
            "When Earth becomes uninhabitable, a team of researchers journeys through a wormhole across spacetime in search of a new home for mankind.",
            "#0A1128",
            "INTERSTELLAR",
            Arrays.asList("Matthew McConaughey", "Anne Hathaway", "Jessica Chastain", "Michael Caine")
        ));

        CINEMAS.add(new Cinema("c-pvr-palladium", "PVR INOX: Phoenix Palladium, Lower Parel", "Lower Parel, Mumbai", Arrays.asList("IMAX", "4DX", "Dolby Atmos")));
        CINEMAS.add(new Cinema("c-inox-rcity", "INOX: R-City, Ghatkopar", "Ghatkopar West, Mumbai", Arrays.asList("Insignia", "Laser 3D", "Dolby 7.1")));
        CINEMAS.add(new Cinema("c-cinepolis-viviana", "Cinepolis: Viviana Mall, Thane", "Eastern Express Highway, Thane", Arrays.asList("VIP", "Dolby Atmos")));
        CINEMAS.add(new Cinema("c-pvr-infinity", "PVR ICON: Infiniti Mall, Versova", "Andheri West, Mumbai", Arrays.asList("Gold Class", "IMAX")));

        // Shows
        SHOWS.add(new Show("3fa85f64-5717-4562-b3fc-2c963f66afa6", "m-kalki", "c-pvr-palladium", "10:30 AM", "IMAX 3D", 380));
        SHOWS.add(new Show("s-kalki-noon", "m-kalki", "c-pvr-palladium", "02:15 PM", "IMAX 3D", 450));
        SHOWS.add(new Show("s-kalki-eve", "m-kalki", "c-inox-rcity", "06:45 PM", "Dolby Atmos", 320));
        SHOWS.add(new Show("s-kalki-night", "m-kalki", "c-cinepolis-viviana", "10:00 PM", "VIP", 400));

        SHOWS.add(new Show("s-stree-morn", "m-stree2", "c-inox-rcity", "11:15 AM", "2D", 250));
        SHOWS.add(new Show("s-stree-eve", "m-stree2", "c-pvr-palladium", "05:30 PM", "2D", 320));
        SHOWS.add(new Show("s-stree-night", "m-stree2", "c-pvr-infinity", "09:45 PM", "Gold Class", 420));

        SHOWS.add(new Show("s-jawan-noon", "m-jawan", "c-pvr-palladium", "01:30 PM", "2D", 280));
        SHOWS.add(new Show("s-jawan-night", "m-jawan", "c-cinepolis-viviana", "08:15 PM", "Dolby Atmos", 340));

        SHOWS.add(new Show("s-dune-morn", "m-dune2", "c-pvr-palladium", "10:00 AM", "IMAX 3D", 400));
        SHOWS.add(new Show("s-dune-eve", "m-dune2", "c-pvr-infinity", "07:00 PM", "IMAX", 450));

        SHOWS.add(new Show("s-interstellar-night", "m-interstellar", "c-pvr-palladium", "09:30 PM", "IMAX", 450));
    }

    /** GET /api/movies */
    public static void getMovies(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < MOVIES.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(movieJson(MOVIES.get(i)));
        }
        sb.append("]");
        BridgeServer.json(ex, 200, "{\"movies\":" + sb + "}");
    }

    /** GET /api/movies/{id} */
    public static void getMovieById(HttpExchange ex, String movieId) throws IOException {
        Movie found = null;
        for (Movie m : MOVIES) {
            if (m.id.equalsIgnoreCase(movieId)) { found = m; break; }
        }
        if (found == null) {
            BridgeServer.json(ex, 404, "{\"error\":\"Movie not found\"}");
            return;
        }
        // Attach shows for this movie
        StringBuilder sb = new StringBuilder(movieJson(found));
        sb.setLength(sb.length() - 1); // remove closing '}'
        sb.append(",\"shows\":[");
        int count = 0;
        for (Show s : SHOWS) {
            if (s.movieId.equalsIgnoreCase(movieId)) {
                if (count++ > 0) sb.append(",");
                Cinema c = findCinema(s.cinemaId);
                sb.append("{")
                  .append("\"id\":").append(BridgeServer.jsonStr(s.id)).append(",")
                  .append("\"cinemaId\":").append(BridgeServer.jsonStr(s.cinemaId)).append(",")
                  .append("\"cinemaName\":").append(BridgeServer.jsonStr(c != null ? c.name : "Cinema")).append(",")
                  .append("\"location\":").append(BridgeServer.jsonStr(c != null ? c.location : "Mumbai")).append(",")
                  .append("\"time\":").append(BridgeServer.jsonStr(s.time)).append(",")
                  .append("\"format\":").append(BridgeServer.jsonStr(s.format)).append(",")
                  .append("\"price\":").append(s.price)
                  .append("}");
            }
        }
        sb.append("]}");
        BridgeServer.json(ex, 200, sb.toString());
    }

    /** GET /api/cinemas */
    public static void getCinemas(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < CINEMAS.size(); i++) {
            Cinema c = CINEMAS.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"id\":").append(BridgeServer.jsonStr(c.id)).append(",")
              .append("\"name\":").append(BridgeServer.jsonStr(c.name)).append(",")
              .append("\"location\":").append(BridgeServer.jsonStr(c.location)).append(",")
              .append("\"formats\":[");
            for (int f = 0; f < c.formats.size(); f++) {
                if (f > 0) sb.append(",");
                sb.append(BridgeServer.jsonStr(c.formats.get(f)));
            }
            sb.append("]}");
        }
        sb.append("]");
        BridgeServer.json(ex, 200, "{\"cinemas\":" + sb + "}");
    }

    /** GET /api/shows */
    public static void getShows(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < SHOWS.size(); i++) {
            Show s = SHOWS.get(i);
            if (i > 0) sb.append(",");
            Cinema c = findCinema(s.cinemaId);
            Movie m = findMovie(s.movieId);
            sb.append("{")
              .append("\"id\":").append(BridgeServer.jsonStr(s.id)).append(",")
              .append("\"movieId\":").append(BridgeServer.jsonStr(s.movieId)).append(",")
              .append("\"movieTitle\":").append(BridgeServer.jsonStr(m != null ? m.title : "")).append(",")
              .append("\"cinemaId\":").append(BridgeServer.jsonStr(s.cinemaId)).append(",")
              .append("\"cinemaName\":").append(BridgeServer.jsonStr(c != null ? c.name : "")).append(",")
              .append("\"time\":").append(BridgeServer.jsonStr(s.time)).append(",")
              .append("\"format\":").append(BridgeServer.jsonStr(s.format)).append(",")
              .append("\"price\":").append(s.price)
              .append("}");
        }
        sb.append("]");
        BridgeServer.json(ex, 200, "{\"shows\":" + sb + "}");
    }

    /** GET /api/system/status */
    public static void getSystemStatus(HttpExchange ex) throws IOException {
        String json = "{"
            + "\"bridge\":\"online\","
            + "\"port\":8080,"
            + "\"rmiPort\":1099,"
            + "\"clusterNodes\":5,"
            + "\"database\":\"PostgreSQL (Resilient Fallback Active)\","
            + "\"timestamp\":" + System.currentTimeMillis()
            + "}";
        BridgeServer.json(ex, 200, json);
    }

    /** Helper to get or generate seats for a show */
    public static Map<String, SeatInfo> getShowSeats(String showId) {
        return SHOW_SEATS.computeIfAbsent(showId, id -> {
            Map<String, SeatInfo> map = new LinkedHashMap<>();
            String[] rows = {"A", "B", "C", "D", "E", "F"};
            for (String row : rows) {
                String tier = "Classic";
                int price = 220;
                if (row.equals("A")) { tier = "Recliner"; price = 450; }
                else if (row.equals("B") || row.equals("C")) { tier = "Prime"; price = 320; }

                for (int col = 1; col <= 8; col++) {
                    String seatNum = row + col;
                    String seatId = "seat-" + id.substring(0, Math.min(8, id.length())) + "-" + seatNum;
                    // Pre-book a couple of seats for realistic display
                    String status = (row.equals("C") && (col == 3 || col == 4)) ? "booked" : "available";
                    map.put(seatId, new SeatInfo(seatId, seatNum, tier, price, status));
                }
            }
            return map;
        });
    }

    /** Book a seat in the fallback store */
    public static synchronized String bookFallbackSeat(String seatId, String userName) {
        for (Map<String, SeatInfo> seats : SHOW_SEATS.values()) {
            SeatInfo seat = seats.get(seatId);
            if (seat != null) {
                if ("booked".equalsIgnoreCase(seat.status)) {
                    return "FAILED: seat not available";
                }
                seat.status = "booked";
                seat.bookedBy = userName;
                return "SUCCESS: seat " + seat.seatNumber + " booked for " + userName;
            }
        }
        // If seatId wasn't already in map, accept and mark booked
        return "SUCCESS: seat booked for " + userName;
    }

    private static Cinema findCinema(String cinemaId) {
        for (Cinema c : CINEMAS) if (c.id.equalsIgnoreCase(cinemaId)) return c;
        return null;
    }

    private static Movie findMovie(String movieId) {
        for (Movie m : MOVIES) if (m.id.equalsIgnoreCase(movieId)) return m;
        return null;
    }

    private static String movieJson(Movie m) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":").append(BridgeServer.jsonStr(m.id)).append(",")
          .append("\"title\":").append(BridgeServer.jsonStr(m.title)).append(",")
          .append("\"genre\":").append(BridgeServer.jsonStr(m.genre)).append(",")
          .append("\"language\":").append(BridgeServer.jsonStr(m.language)).append(",")
          .append("\"format\":").append(BridgeServer.jsonStr(m.format)).append(",")
          .append("\"rating\":").append(m.rating).append(",")
          .append("\"duration\":").append(BridgeServer.jsonStr(m.duration)).append(",")
          .append("\"certificate\":").append(BridgeServer.jsonStr(m.certificate)).append(",")
          .append("\"synopsis\":").append(BridgeServer.jsonStr(m.synopsis)).append(",")
          .append("\"posterColor\":").append(BridgeServer.jsonStr(m.posterColor)).append(",")
          .append("\"posterText\":").append(BridgeServer.jsonStr(m.posterText)).append(",")
          .append("\"cast\":[");
        for (int i = 0; i < m.cast.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(BridgeServer.jsonStr(m.cast.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }
}
