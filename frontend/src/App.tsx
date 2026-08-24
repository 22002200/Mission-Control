import AccountMenu from './components/AccountMenu';
import { useAuth } from './auth/useAuth';
import LoginPage from './pages/LoginPage';
import SystemInfoPage from './pages/SystemInfoPage';

export default function App() {
  const { status } = useAuth();

  return (
    <main className="mc-shell">
      {/* Fixed to the viewport corner, so it sits outside the centred column. */}
      {status === 'authenticated' && <AccountMenu />}

      <h1 className="mc-title">Mission Control</h1>
      <p className="mc-subtitle">Space mission planning and crew assignment.</p>

      {/* Restoring a stored session takes one request; showing the login form in the meantime
          would make every refresh flash a sign-in screen at an already-signed-in user. */}
      {status === 'loading' && <p className="mc-subtitle">Restoring session…</p>}

      {status === 'anonymous' && <LoginPage />}

      {status === 'authenticated' && <SystemInfoPage />}
    </main>
  );
}
