/**
 * Experiment metadata and concept mapping for CineBook
 * Directly cross-referenced with the Java backend source code.
 */
export const EXPERIMENTS_DATA = {
  exp1: {
    id: 'exp1',
    num: 'Experiment 1',
    title: 'Java RMI Basics',
    icon: '🔗',
    badge: 'Remote Method Invocation',
    javaFiles: [
      'Backend/experiment_1/BookingService.java',
      'Backend/experiment_1/BookingServiceImpl.java',
      'Backend/experiment_1/Server.java',
      'Backend/experiment_1/Client.java'
    ],
    concepts: ['RMI Registry', 'Remote Interface', 'UnicastRemoteObject', 'PostgreSQL / Supabase'],
    summary: 'Demonstrates basic Java RMI where the client invokes remote methods on the CineBook server to fetch available seats and book tickets.',
    defaultShowId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  },
  exp2: {
    id: 'exp2',
    num: 'Experiment 2',
    title: 'Concurrent Booking & Race Conditions',
    icon: '⚡',
    badge: 'Thread Concurrency',
    javaFiles: [
      'Backend/experiment_2/BookingService.java',
      'Backend/experiment_2/BookingServiceImpl.java',
      'Backend/experiment_2/Server.java',
      'Backend/experiment_2/ConcurrentBookingClient.java'
    ],
    concepts: ['Race Condition', 'synchronized Method', 'Multi-threaded Clients', 'ACID Isolation'],
    summary: 'Multiple threads attempt to book the exact same seat at the exact same millisecond. Demonstrates how synchronized blocks prevent double-booking.',
    defaultShowId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  },
  exp3: {
    id: 'exp3',
    num: 'Experiment 3',
    title: 'Logical Clocks & Synchronization',
    icon: '🕐',
    badge: 'Lamport & Berkeley',
    javaFiles: [
      'Backend/experiment_3/BookingService.java',
      'Backend/experiment_3/BookingServiceImpl.java',
      'Backend/experiment_3/Server.java',
      'Backend/experiment_3/LamportClock.java',
      'Backend/experiment_3/Client.java'
    ],
    concepts: ['Lamport Timestamps', 'Causal Ordering', 'Berkeley Algorithm', 'Clock Skew Correction'],
    summary: 'Tracks distributed causality using Lamport logical timestamps for each booking event, and uses the Berkeley algorithm to calculate clock drift adjustments.',
    defaultShowId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  },
  exp4: {
    id: 'exp4',
    num: 'Experiment 4',
    title: 'Bully Leader Election Algorithm',
    icon: '👑',
    badge: 'Distributed Consensus',
    javaFiles: [
      'Backend/experiment_4/Process.java',
      'Backend/experiment_4/ProcessNode.java',
      'Backend/experiment_4/ElectionDemo.java'
    ],
    concepts: ['Bully Algorithm', 'Coordinator Failure', 'Heartbeat Timeout', 'Dynamic Re-election'],
    summary: 'A cluster of 5 independent RMI process nodes (P1–P5 on ports 1101–1105). When the current leader crashes, survivor nodes execute the Bully algorithm to elect the highest alive node.',
    ports: [1101, 1102, 1103, 1104, 1105],
  }
}
