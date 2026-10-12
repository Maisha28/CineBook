import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe logger for Experiment 9: MPI Collective Communication
 * (Broadcast, Scatter, Gather) across the CineBook Booking Cluster.
 */
public final class Exp9Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Exp9Log() {}

    public static synchronized void log(String who, String msg) {
        System.out.printf("[%s] [%-16s] %s%n", LocalTime.now().format(TIME), who, msg);
    }
}
