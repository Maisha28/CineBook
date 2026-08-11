import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;

public class Client {
public static void main(String[] args) {
try {
Registry registry = LocateRegistry.getRegistry("localhost", 1099);
BookingService service = (BookingService) registry.lookup("BookingService");

Scanner sc = new Scanner(System.in);
System.out.print("Enter show ID (from your shows table): ");
String showId = sc.nextLine().trim();

List<String> seats = service.getAvailableSeats(showId);
System.out.println("Available seats:");
seats.forEach(System.out::println);

System.out.print("Enter seat id to book: ");
String seatId = sc.nextLine().trim();
System.out.print("Enter your name: ");
String name = sc.nextLine().trim();

String result = service.bookSeat(seatId, name);
System.out.println(result);
} catch (Exception e) {
e.printStackTrace();
}
}
}
