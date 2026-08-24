/**
 * Where the session lives between page loads.
 *
 * `localStorage` so a refresh - or a Vite hot reload - does not sign you out. The trade-off is
 * real and worth stating: anything in web storage is readable by injected script, whereas an
 * httpOnly cookie would not be. A cookie would mean re-enabling CSRF protection and abandoning the
 * "token API with no cookie-based session" stance the backend's SecurityConfig takes, which is not
 * a worthwhile trade for a locally-run demo with no third-party scripts on the page.
 *
 * Only the token and its expiry are kept. The user record is re-fetched from `/api/auth/me` on
 * startup, which doubles as a check that the token is still live - it may have been revoked by a
 * logout in another tab.
 */
const STORAGE_KEY = 'mc.auth';

export interface StoredSession {
  token: string;
  expiresAt: string;
}

export function readSession(): StoredSession | null {
  let raw: string | null;
  try {
    raw = window.localStorage.getItem(STORAGE_KEY);
  } catch {
    // Storage can be unavailable entirely (private mode, blocked cookies).
    return null;
  }
  if (!raw) return null;

  try {
    const parsed = JSON.parse(raw) as Partial<StoredSession>;
    if (typeof parsed.token !== 'string' || typeof parsed.expiresAt !== 'string') {
      return null;
    }
    // An expired token is no better than no token; skip the pointless round trip.
    if (new Date(parsed.expiresAt).getTime() <= Date.now()) {
      clearSession();
      return null;
    }
    return { token: parsed.token, expiresAt: parsed.expiresAt };
  } catch {
    // Corrupt value from an older version of the app, or something else entirely.
    clearSession();
    return null;
  }
}

export function writeSession(session: StoredSession): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Not fatal: the session simply will not survive a reload.
  }
}

export function clearSession(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing useful to do.
  }
}
