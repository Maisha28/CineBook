# Experiment 2: Multithreading

Builds on [Experiment 1](../experiment_1) to demonstrate a classic
read-check-then-write race condition when multiple clients book the same
seat concurrently, and how `synchronized` fixes it.

## Files
- `BookingService.java`, `Server.java`, `Client.java` -- same RMI setup as Experiment 1
- `BookingServiceImpl.java` -- `bookSeat` now logs which thread handled each
  request and sleeps briefly between the availability check and the write,
  widening the race window
- `ConcurrentClient.java` -- fires N threads at the same seat at once

## Run
From this folder:
```
javac -cp ../lib/postgresql-42.7.4.jar *.java
java -cp ".;../lib/postgresql-42.7.4.jar" Server
java -cp ".;../lib/postgresql-42.7.4.jar" ConcurrentClient   # in a second terminal
```

To reproduce the race, remove `synchronized` from `bookSeat` in
`BookingServiceImpl.java` and rerun -- multiple threads can succeed on the
same seat. Add it back and only one thread should win.
