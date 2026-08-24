/**
 * The bearer token, held where the generated client can reach it.
 *
 * `runtimeConfig.ts` is evaluated when the API client module is first imported, long before React
 * mounts, so the client's `auth` callback cannot close over React state. This tiny module is the
 * bridge: `AuthProvider` writes the token here, and the client reads it on every request.
 */
let token: string | null = null;

export function getAuthToken(): string | null {
  return token;
}

export function setAuthToken(next: string | null): void {
  token = next;
}
