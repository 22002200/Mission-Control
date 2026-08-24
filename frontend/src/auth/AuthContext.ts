import { createContext } from 'react';
import type { CurrentUserResponse } from '../api/generated/types.gen';

/**
 * `loading` covers the gap between startup and knowing whether a stored token is still good.
 * Without it the app would flash the login screen on every refresh for an already-signed-in user.
 */
export type AuthStatus = 'loading' | 'anonymous' | 'authenticated';

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUserResponse | null;
  /** Throws on failure so the form can render the reason. */
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

/**
 * Split into its own module so neither the provider nor the hook exports a non-component
 * alongside a component - `react-refresh/only-export-components` is enabled and would warn.
 */
export const AuthContext = createContext<AuthContextValue | null>(null);
