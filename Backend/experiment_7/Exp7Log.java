import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe logger for Experiment 7: Load Balancing & Fault Tolerance.
 */
public final class Exp7Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Exp7Log() {}

    public static synchronized void log(String who, String msg) {
        System.out.printf("[%s] [%-12s] %s%n", LocalTime.now().format(TIME), who, msg);
    }
}
