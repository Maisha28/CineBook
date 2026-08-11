import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {
public static void main(String[] args) {
try {
BookingServiceImpl service = new BookingServiceImpl();
Registry registry = LocateRegistry.createRegistry(1099);
registry.rebind("BookingService", service);
System.out.println("CineBook RMI server ready on port 1099");
} catch (Exception e) {
e.printStackTrace();
}
}
}
