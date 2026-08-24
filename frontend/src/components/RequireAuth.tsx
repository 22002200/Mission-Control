import Typography from '@mui/material/Typography';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../auth/useAuth';

/**
 * Guards the authenticated part of the application.
 *
 * The `loading` branch matters: restoring a stored session takes one request, and redirecting to
 * the login page while that is in flight would flash a sign-in screen at somebody who is already
 * signed in, on every refresh.
 *
 * The attempted path is handed to the login page in location state, so signing in returns you
 * where you were rather than dumping you on the mission board.
 */
export default function RequireAuth({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <Typography color="text.secondary" sx={{ p: 4 }}>
        Restoring session…
      </Typography>
    );
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <>{children}</>;
}
