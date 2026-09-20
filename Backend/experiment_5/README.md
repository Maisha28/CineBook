# Experiment 5: Fault Tolerance with Primary-Backup Replication

CineBook now runs as **two servers** instead of one. The **Primary** handles
all normal client requests; the **Backup** stays on standby and keeps a
replicated copy of every booking operation. If the Primary crashes, the client
notices through missing heartbeats, activates the Backup, and keeps booking
without losing anything that was already committed.

```
Client --> Primary --(PREPARE / COMMIT)--> Backup
              |                              |
              +-------------> Database <-----+     (one shared Supabase DB)
```

> Both servers use the **same** Supabase database, so this experiment shows
> server-level primary-backup replication and failover, not physical
> replication of two separate databases. What is replicated between the servers
> is the log of booking operations.

## Files
- `BookingServer.java` -- the RMI interface both servers implement (client calls,
  replication calls, recovery calls)
- `ReplicatedBookingServer.java` -- the server: roles, synchronous replication,
  promotion, recovery
- `Client.java` -- heartbeat monitor, failover, retry with the same `operationId`;
  also an interactive console (`java Client`)
- `Server.java` -- start one server per terminal (`primary` or `backup`)
- `FailoverDemo.java` -- runs both servers + a client in one JVM and scripts the
  whole scenario into a single log
- `Cluster.java`, `Log.java`, `Role.java`, `NodeStatus.java`, `OperationRecord.java` -- shared types
- `BookingStore.java`, `JdbcBookingStore.java`, `InMemoryBookingStore.java` -- the
  shared database (Supabase, or an in-memory stand-in for `FailoverDemo --memory`)

## How it works

**Roles.** "Primary" and "Backup" are the two servers (ports 1201 and 1202); each
plays a role at any moment: `ACTIVE_PRIMARY` (serves bookings), `STANDBY`
(replicates only) or `RECOVERING` (restarted, catching up).

**Normal booking -- synchronous replication.** For every request the active
primary
1. sends `PREPARE` to the backup, which logs the operation as `PREPARED` and ACKs,
2. commits the booking to the database,
3. sends `COMMIT`, making the backup's replica record `COMMITTED`,
4. replies to the client.

The primary waits for the backup's ACK before touching the database, so the two
servers stay synchronized.

**Failure and failover.** The client sends a heartbeat to the active primary every
second. After 2 missed heartbeats (or a failed request) it declares the primary
failed, connects to the Backup and **activates** it (`STANDBY -> ACTIVE_PRIMARY`).
It then resends any interrupted booking with the **same `operationId`**.

**Idempotency.** The client generates the `operationId` once per booking. If the
old primary already committed a booking but died before replying, the backup
still holds that operation as `PREPARED`. On activation it asks the database
whether the booking exists; if so it marks the operation `COMMITTED`, and the
retry gets the original `SUCCESS` back rather than a second booking (or a
misleading "seat not available"). If the booking is not in the database, the
`PREPARED` record is discarded and the retry runs from scratch.

**Recovery of the old primary.** When the Primary restarts it does not serve
requests right away:
```
RECOVERING -> fetch committed operations from the active Backup
           -> verify them against the database
           -> Backup steps down to STANDBY (and returns anything committed meanwhile)
           -> Primary becomes ACTIVE_PRIMARY again
```
The client's heartbeat sees the Backup is no longer the primary and switches back.

**Degraded mode.** If the peer is unreachable, the active server carries on
without replication (logged as `UNREPLICATED`); the peer catches up from it when
it comes back. Otherwise a single failure would stop all bookings.

## Setup: database credentials
Nothing secret is stored in source. Copy `db.properties.example` to
`db.properties` (it is gitignored) and fill in the password, or set
`CINEBOOK_DB_URL`, `CINEBOOK_DB_USER`, `CINEBOOK_DB_PASSWORD`.
Not needed for `FailoverDemo --memory`.

No schema change is required: bookings use the existing `seats` and `bookings`
tables, and `bookings.server_node` records which server ("Primary" / "Backup")
wrote each booking.

## Run: single-JVM demo (one combined log)
From this folder:
```
javac -cp ../lib/postgresql-42.7.4.jar *.java
java -cp ".;../lib/postgresql-42.7.4.jar" FailoverDemo <showId>
```
(`java FailoverDemo --memory` runs the same story without a database.) It needs
4 available seats for the show, and books them. The phases:

1. **Normal operation** -- PREPARE / ACK / DB commit / COMMIT; both replica logs match.
2. **Primary crashes after the DB commit, before replying** -- the client retries
   with the same `operationId` on the Backup; the DB still has exactly one booking.
3. **Old Primary restarts** -- RECOVERING, syncs from the Backup, takes over again;
   Bob's original `operationId` is still recognised.
4. **Primary crashes while idle** -- the heartbeat detects it and the client activates the Backup.

## Run: separate processes (real distributed demo)
One terminal each, from this folder (`CP` = `".;../lib/postgresql-42.7.4.jar"`):
```
java -cp CP Server primary
java -cp CP Server backup
java -cp CP Client
```
Server console: status, show replica log, **crash now** (real process death via
`Runtime.halt`), or **crash right after the next database commit** (arms the
one-shot fault from phase 2). To reproduce the story by hand:

1. In the Client, list seats for a show and book one -- watch PREPARE/COMMIT in both server terminals.
2. In the Primary terminal choose `4` (arm crash), then book another seat from the
   Client. The Primary dies after committing; the Client fails over and retries.
3. Choose `3` in the Client ("retry with the SAME operationId") -- you get the original result, no new booking.
4. Restart `Server primary` and watch it recover; the Client switches back.

## Putting the demo seats back
`FailoverDemo` prints the exact SQL at the end (view the bookings, then reset the
seats it used).

## Limitations
- The database is shared, so it is still a single point of failure; only the servers are redundant.
- Replica logs are in memory. A restarted server re-learns them from its peer;
  if both servers restart together, the database is the only record.
- Promotion is driven by the client. A network partition that cuts the client off
  from a healthy primary could activate the backup while the primary still runs
  (split brain); real systems add fencing or a quorum.
