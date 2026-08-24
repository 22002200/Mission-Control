import { client } from './generated/client.gen';

/**
 * Reacts to a token the server no longer accepts.
 *
 * A token can stop working while the app is open - it expires after eight hours, or the user logs
 * out in another tab, which revokes every token they hold. Without this the UI would sit there
 * showing an authenticated shell where every request quietly fails.
 *
 * Login is excluded deliberately. A 401 from `/api/auth/login` means the password was wrong, not
 * that a session ended, and treating it as the latter would replace "email or password is
 * incorrect" with a spurious "your session has expired".
 */
type UnauthorizedHandler = () => void;

let onUnauthorized: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler;
}

client.interceptors.response.use((response, request) => {
  const isLogin = new URL(request.url, window.location.origin).pathname === '/api/auth/login';

  if (response.status === 401 && !isLogin) {
    onUnauthorized?.();
  }
  return response;
});
