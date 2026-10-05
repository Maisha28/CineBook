import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Exp6Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Exp6Log() {}

    public static synchronized void log(String who, String msg) {
        System.out.printf("[%s] [%-7s] %s%n", LocalTime.now().format(TIME), who, msg);
    }
}
