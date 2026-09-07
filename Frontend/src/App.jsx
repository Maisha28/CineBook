import { Routes, Route, Navigate } from 'react-router-dom'
import Navbar from './components/Navbar'
import Footer from './components/Footer'
import Home from './pages/Home'
import MovieDetailsPage from './pages/MovieDetailsPage'
import SeatBookingPage from './pages/SeatBookingPage'
import BookingConfirmationPage from './pages/BookingConfirmationPage'
import LabHub from './pages/lab/LabHub'
import { ToastProvider } from './components/Toast'

export default function App() {
  return (
    <ToastProvider>
      <div className="app-shell">
        <Navbar />
        <main className="main-content">
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/movie/:id" element={<MovieDetailsPage />} />
            <Route path="/book/:showId" element={<SeatBookingPage />} />
            <Route path="/booking/confirmation" element={<BookingConfirmationPage />} />
            <Route path="/lab" element={<LabHub />} />

            {/* Backward-compatible redirects */}
            <Route path="/exp1" element={<Navigate to="/lab?tab=rmi" replace />} />
            <Route path="/exp2" element={<Navigate to="/lab?tab=concurrency" replace />} />
            <Route path="/exp3" element={<Navigate to="/lab?tab=clocks" replace />} />
            <Route path="/exp4" element={<Navigate to="/lab?tab=election" replace />} />

            {/* Fallback */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <Footer />
      </div>
    </ToastProvider>
  )
}
