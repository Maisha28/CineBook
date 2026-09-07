# Experiment 3: Clock Synchronization

Builds on [Experiment 2](../experiment_2) to add:
- **Lamport logical clocks** -- every client (including each simulated user
  in `ConcurrentClient`) keeps its own `LamportClock` and ticks it before
  sending a booking request; the server keeps one shared clock and applies
  the receive rule on every incoming request, giving a single logical
  ordering of events. This is for ordering/logging only and never decides
  who wins a seat -- that's still the `synchronized` critical section.
- **Berkeley physical clock synchronization** -- the RMI server acts as
  coordinator: it collects participant clock readings, averages them, and
  returns a correction offset to each participant. See `ClockDemo.java`.

## Files
- `LamportClock.java` -- the logical clock implementation
- `ClockDemo.java` -- standalone Berkeley algorithm demo (run after `Server`)
- `BookingService.java`, `BookingServiceImpl.java`, `Client.java`, `Server.java`,
  `ConcurrentClient.java` -- same as Experiment 2, extended with Lamport
  timestamps and the Berkeley coordinator RPCs

## Run
From this folder:
```
javac -cp ../lib/postgresql-42.7.4.jar *.java
java -cp ".;../lib/postgresql-42.7.4.jar" Server
java -cp ".;../lib/postgresql-42.7.4.jar" ConcurrentClient   # or Client, or ClockDemo
```
