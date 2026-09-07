# CineBook — Distributed Movie Ticket Booking System

A distributed movie-ticket-booking platform inspired by BookMyShow and District, demonstrating 4 core Distributed Computing laboratory experiments.

---

## Quick Start (One-Click)

Double-click or run from the project root:
```cmd
start-all.bat
```
This automatically launches both the **Java Bridge Server** (port 8080) and the **React Frontend** (port 3000) in separate terminal windows.

- **Movie Ticketing UI**: [http://localhost:3000](http://localhost:3000)
- **CineBook Systems Lab**: [http://localhost:3000/lab](http://localhost:3000/lab)
- **Java Bridge REST API**: [http://localhost:8080](http://localhost:8080)

---

## Manual Step-by-Step Start

### 1. Start the Java Backend Bridge
In a command prompt:
```cmd
cd Backend\bridge
build.bat
run.bat
```
*Listens on `http://localhost:8080` and bridges HTTP requests to the Java RMI backend.*

### 2. Start the React Frontend
In a second command prompt:
```cmd
cd Frontend
npm install    (first time only)
npm run dev
```
*Listens on `http://localhost:3000` with live hot-reloading.*

---

## Distributed Systems Experiments in CineBook Lab (`/lab`)

1. **Experiment 1: Java RMI Remote Booking Service** (`Backend/experiments/exp1_rmi`)
   - Queries `LocateRegistry.getRegistry(1099)` and performs remote method invocations (`getAvailableSeats`, `bookSeat`).
2. **Experiment 2: Concurrency & Race Conditions** (`Backend/experiments/exp2_concurrency`)
   - Simulates multi-threaded race conditions on shared seat reservations and demonstrates `synchronized` mutual exclusion locks.
3. **Experiment 3: Distributed Clock Synchronization** (`Backend/experiments/exp3_clock`)
   - Interactive Berkeley master-slave clock offset calculation and Lamport logical event ordering.
4. **Experiment 4: Bully Leader Election Algorithm** (`Backend/experiments/exp4_election`)
   - 5-node cluster (P1..P5 on ports 1101..1105) with node failure injection and coordinator takeover.

---

## Folder Structure

```
CineBook/
├── Backend/
│   ├── experiments/         # Core Distributed Computing experiments
│   │   ├── exp1_rmi/        # Java RMI Interfaces & Services
│   │   ├── exp2_concurrency/ # Multi-threaded seat booking
│   │   ├── exp3_clock/      # Berkeley & Lamport clocks
│   │   └── exp4_election/   # Bully Leader Election nodes
│   ├── bridge/              # Java HTTP REST-to-RMI Bridge (:8080)
│   │   ├── src/             # BridgeServer, CatalogHandler, Exp12Handler, etc.
│   │   ├── build.bat
│   │   └── run.bat
│   └── lib/                 # PostgreSQL JDBC driver
├── Frontend/                # React 19 + Vite UI (:3000)
│   ├── src/
│   │   ├── api/             # HTTP Bridge client
│   │   ├── components/      # Navbar, Footer, Toast
│   │   ├── pages/           # Home, MovieDetails, SeatBooking, BookingConfirmation
│   │   └── pages/lab/       # CineBook Systems Lab diagnostic workbench
│   ├── package.json
│   └── vite.config.js
├── start-all.bat            # 1-click startup for both servers
├── start-backend.bat        # Starts only Java bridge
└── start-frontend.bat       # Starts only React dev server
```
