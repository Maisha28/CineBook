# Experiment 1: RMI / RPC

Baseline distributed booking service: a `Server` exposes `BookingService`
over Java RMI, and a `Client` looks it up in the registry and calls it as
if it were local. Backed by a Postgres (Supabase) database.

## Files
- `BookingService.java` -- remote interface (the RPC contract)
- `BookingServiceImpl.java` -- server-side implementation
- `Server.java` -- starts the RMI registry and binds the service
- `Client.java` -- looks up the service and books a seat

## Run
From this folder:
```
javac -cp ../lib/postgresql-42.7.4.jar *.java
java -cp ".;../lib/postgresql-42.7.4.jar" Server
java -cp ".;../lib/postgresql-42.7.4.jar" Client   # in a second terminal
```
