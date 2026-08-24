import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/useAuth';

/**
 * The sign-in screen.
 *
 * Shown whenever there is no valid session. There is no router - the app has exactly two states,
 * and conditional rendering in `App` expresses that more honestly than a route table with one
 * entry would.
 */
export default function LoginPage() {
  const { login } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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
    <section className="mc-card mc-login">
      <h2>Sign in</h2>

      <form className="mc-form" onSubmit={handleSubmit} noValidate>
        <div className="mc-field">
          <label className="mc-label" htmlFor="email">
            Email
          </label>
          <input
            id="email"
            className="mc-input"
            type="email"
            name="email"
            autoComplete="username"
            autoFocus
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>

        <div className="mc-field">
          <label className="mc-label" htmlFor="password">
            Password
          </label>
          <input
            id="password"
            className="mc-input"
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>

        {error && (
          <p className="mc-error" role="alert">
            {error}
          </p>
        )}

        <button className="mc-button" type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="mc-hint">
        Demo data: sign in as <code>vera.lindholm@orbitaldynamics.example</code> with{' '}
        <code>Password123!</code>. Other seeded accounts are listed in the README.
      </p>
    </section>
  );
}
