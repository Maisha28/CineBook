import axios from 'axios'

// All requests go to /api/* — Vite proxies them to http://localhost:8080
const http = axios.create({ baseURL: '/api', timeout: 15000 })

// ── Catalog & Booking API ───────────────────────────────────────────────
export const catalog = {
  getMovies:       () => http.get('/movies').then(r => r.data),
  getMovieById:    (id) => http.get(`/movies/${id}`).then(r => r.data),
  getCinemas:      () => http.get('/cinemas').then(r => r.data),
  getShows:        () => http.get('/shows').then(r => r.data),
  getSystemStatus: () => http.get('/system/status').then(r => r.data),
}

// ── Experiment 1 (RMI Booking) ──────────────────────────────────────────
export const exp1 = {
  getSeats: (showId) => http.get(`/exp1/seats?showId=${encodeURIComponent(showId)}`).then(r => r.data),
  bookSeat: (seatId, userName) => http.post('/exp1/book', { seatId, userName }).then(r => r.data),
}

// ── Experiment 2 (Concurrency) ─────────────────────────────────────────
export const exp2 = {
  getSeats:   (showId) => http.get(`/exp2/seats?showId=${encodeURIComponent(showId)}`).then(r => r.data),
  bookSeat:   (seatId, userName) => http.post('/exp2/book', { seatId, userName }).then(r => r.data),
  concurrent: (seatId, users) => http.post('/exp2/concurrent', { seatId, users }).then(r => r.data),
}

// ── Experiment 3 (Clocks) ──────────────────────────────────────────────
export const exp3 = {
  getSeats:  (showId) => http.get(`/exp3/seats?showId=${encodeURIComponent(showId)}`).then(r => r.data),
  bookSeat:  (seatId, userName) => http.post('/exp3/book', { seatId, userName }).then(r => r.data),
  lamport:   (clients) => http.post('/exp3/lamport', { clients }).then(r => r.data),
  berkeley:  (clients) => http.post('/exp3/berkeley', { clients }).then(r => r.data),
}

// ── Experiment 4 (Leader Election) ─────────────────────────────────────
export const exp4 = {
  status:       () => http.get('/exp4/status').then(r => r.data),
  startCluster: () => http.post('/exp4/start').then(r => r.data),
  crash:        (node) => http.post(`/exp4/crash?node=${node}`).then(r => r.data),
  elect:        (initiator) => http.post(`/exp4/elect?initiator=${initiator}`).then(r => r.data),
  stopCluster:  () => http.post('/exp4/stop').then(r => r.data),
  getLogs:      () => http.get('/exp4/logs').then(r => r.data),
}

// ── Seat String Parser ─────────────────────────────────────────────────
// Parses backend format: "A1 (id=seat-uuid)"
export function parseSeat(raw) {
  if (!raw) return { label: '', id: '' }
  const m = raw.match(/^(.+?)\s*\(id=([^)]+)\)$/)
  if (m) return { label: m[1].trim(), id: m[2].trim() }
  return { label: raw, id: raw }
}
