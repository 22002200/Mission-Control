import { Navigate, Route, Routes } from 'react-router';
import AppLayout from './components/AppLayout';
import RequireAuth from './components/RequireAuth';
import LoginPage from './pages/LoginPage';
import MissionDetailPage from './pages/MissionDetailPage';
import MissionsPage from './pages/MissionsPage';

/**
 * The route table.
 *
 * There is a router now. Until this feature the application had exactly two states - signed in or
 * not - and conditional rendering said that more plainly than a one-entry route table would have.
 * Missions changed that: a list and a detail view that has to be linkable, bookmarkable and
 * survive a refresh is precisely what a router is for.
 *
 * Everything except the login page sits behind `RequireAuth`, so a new screen cannot be added
 * without a deliberate decision about whether it is public.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/missions" element={<MissionsPage />} />
        <Route path="/missions/:missionId" element={<MissionDetailPage />} />
      </Route>

      {/* Missions is the only destination, so anything else lands there rather than on a
          not-found page that would only ever say the same thing. */}
      <Route path="*" element={<Navigate to="/missions" replace />} />
    </Routes>
  );
}
