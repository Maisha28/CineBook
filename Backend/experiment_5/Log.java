import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

// One timestamped line per event: "[time] [who] message". Servers, the
// client and the demo all print through here so a single-JVM run reads as
// one interleaved timeline.
public final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {}

    public static synchronized void log(String who, String msg) {
        System.out.printf("[%s] [%-7s] %s%n", LocalTime.now().format(TIME), who, msg);
    }
}
