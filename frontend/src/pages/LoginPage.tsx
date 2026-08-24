import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../auth/useAuth';

/** Where to land after signing in, when the visitor was not aiming anywhere in particular. */
const DEFAULT_DESTINATION = '/missions';

/**
 * The sign-in screen.
 *
 * Migrated from the hand-rolled form it used to be, so the whole application speaks one styling
 * system. The behaviour is unchanged: one error line for the form as a whole, because the backend
 * deliberately does not say which of the email and the password was wrong.
 *
 * No client-side validation. The server is the only thing that can decide whether these
 * credentials are good, and a required-field check here would only add a second, weaker set of
 * rules to keep in step.
 */
export default function LoginPage() {
  const { login, status } = useAuth();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Someone who is already signed in has no business on this page - they get here by typing the
  // URL or by pressing back after signing in.
  if (status === 'authenticated') {
    const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
    return <Navigate to={from ?? DEFAULT_DESTINATION} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await login(email, password);
      // On success this component unmounts, so there is no state to reset.
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not sign in.');
      setSubmitting(false);
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        px: 2,
      }}
    >
      <Box sx={{ width: '100%', maxWidth: '26rem' }}>
        <Typography variant="h1" gutterBottom>
          Mission Control
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 4 }}>
          Space mission planning and crew assignment.
        </Typography>

        <Paper variant="outlined" sx={{ p: 3, borderColor: 'divider' }}>
          <Typography variant="overline" color="text.secondary" component="h2">
            Sign in
          </Typography>

          <Box component="form" onSubmit={handleSubmit} noValidate sx={{ mt: 2 }}>
            <Stack spacing={2}>
              <TextField
                id="email"
                label="Email"
                type="email"
                name="email"
                autoComplete="username"
                autoFocus
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />

              <TextField
                id="password"
                label="Password"
                type="password"
                name="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />

              {error && <Alert severity="error">{error}</Alert>}

              <Button type="submit" variant="contained" disabled={submitting}>
                {submitting ? 'Signing in…' : 'Sign in'}
              </Button>
            </Stack>
          </Box>

          <Typography variant="body2" color="text.secondary" sx={{ mt: 3 }}>
            Demo data: sign in as <code>marcus.reyes@orbitaldynamics.example</code> with{' '}
            <code>Password123!</code>. Other seeded accounts are listed in the README.
          </Typography>
        </Paper>
      </Box>
    </Box>
  );
}
