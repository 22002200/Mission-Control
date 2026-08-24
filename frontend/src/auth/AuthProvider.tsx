import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { setAuthToken } from '../api/authToken';
import { setUnauthorizedHandler } from '../api/interceptors';
import { currentUser, login as loginRequest, logout as logoutRequest } from '../api/generated';
import type { CurrentUserResponse, ProblemDetail } from '../api/generated/types.gen';
import { AuthContext, type AuthContextValue, type AuthStatus } from './AuthContext';
import { LoginFailedError } from './LoginFailedError';
import { clearSession, readSession, writeSession } from './tokenStorage';

function messageFor(problem: ProblemDetail | undefined, status: number | undefined): string {
  if (problem?.type === 'urn:mission-control:account-disabled') {
    return problem.detail ?? 'This account has been disabled.';
  }
  if (problem?.type === 'urn:mission-control:invalid-credentials' || status === 401) {
    return 'Email or password is incorrect.';
  }
  if (problem?.type === 'urn:mission-control:validation-failed') {
    return 'Enter both an email address and a password.';
  }
  return 'Could not sign in. Please try again.';
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  // Read storage once, during the first render, so the initial status is already correct. Starting
  // at 'loading' unconditionally and fixing it in an effect would mean a synchronous setState in an
  // effect body - a cascading render, and a lint error that is right to complain.
  const [storedSession] = useState(readSession);
  const [status, setStatus] = useState<AuthStatus>(storedSession ? 'loading' : 'anonymous');
  const [user, setUser] = useState<CurrentUserResponse | null>(null);

  // Guards against a state update after unmount during the startup round trip.
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const endSession = useCallback(() => {
    setAuthToken(null);
    clearSession();
    setUser(null);
    setStatus('anonymous');
    // Without this the next person to sign in on this browser would briefly see the previous
    // user's cached data.
    queryClient.clear();
  }, [queryClient]);

  // A token rejected mid-session - expired, or revoked by a logout elsewhere - drops the UI back
  // to the login screen rather than leaving it wedged.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (mounted.current) endSession();
    });
    return () => setUnauthorizedHandler(null);
  }, [endSession]);

  // Restore a stored session on startup. Fetching /me both rehydrates the user and confirms the
  // token is still accepted.
  useEffect(() => {
    if (!storedSession) return;

    setAuthToken(storedSession.token);

    let cancelled = false;
    void currentUser().then(({ data }) => {
      if (cancelled) return;
      if (data) {
        setUser(data);
        setStatus('authenticated');
      } else {
        // The interceptor has already cleared things on a 401; this covers everything else.
        endSession();
      }
    });

    return () => {
      cancelled = true;
    };
  }, [storedSession, endSession]);

  const login = useCallback(async (email: string, password: string) => {
    const { data, error, response } = await loginRequest({ body: { email, password } });

    if (!data) {
      throw new LoginFailedError(
        messageFor(error as ProblemDetail | undefined, response?.status),
        (error as ProblemDetail | undefined)?.type,
      );
    }

    setAuthToken(data.token);
    writeSession({ token: data.token, expiresAt: data.expiresAt });
    setUser(data.user);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } catch {
      // Swallowed on purpose, and not rethrown. Signing out is a local act: the token is dropped
      // here regardless, and the server-side revocation is best effort. If the token had already
      // been revoked - by an earlier logout in another tab - the request fails, and surfacing
      // that would either strand the user on a dead screen or, since nothing awaits this, produce
      // an unhandled rejection.
    } finally {
      endSession();
    }
  }, [endSession]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout }),
    [status, user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
