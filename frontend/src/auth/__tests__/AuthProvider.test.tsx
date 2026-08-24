import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAuthToken } from '../../api/authToken';
import { currentUser, login, logout } from '../../api/generated';
import { AuthProvider } from '../AuthProvider';
import { useAuth } from '../useAuth';

// The generated SDK is the boundary. Mocking it keeps these tests about the provider's behaviour
// rather than about fetch, and avoids adding an HTTP mocking dependency for a handful of tests.
vi.mock('../../api/generated', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  currentUser: vi.fn(),
}));

const DIRECTOR = {
  id: 'a1000000-0000-0000-0000-000000000001',
  fullName: 'Vera Lindholm',
  email: 'vera.lindholm@orbitaldynamics.example',
  role: 'DIRECTOR' as const,
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

function Probe() {
  const { status, user, login: signIn, logout: signOut } = useAuth();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="user">{user?.fullName ?? 'none'}</span>
      <button onClick={() => void signIn('vera@x.example', 'Password123!')}>sign in</button>
      <button onClick={() => void signOut()}>sign out</button>
    </div>
  );
}

function renderProvider() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

function storedSession(token: string, hoursAhead = 8) {
  window.localStorage.setItem(
    'mc.auth',
    JSON.stringify({
      token,
      expiresAt: new Date(Date.now() + hoursAhead * 3_600_000).toISOString(),
    }),
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.clearAllMocks();
  });

  it('settles on anonymous when nothing is stored', async () => {
    renderProvider();

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
    expect(currentUser).not.toHaveBeenCalled();
  });

  it('restores a stored session by confirming it against /me', async () => {
    storedSession('stored-token');
    vi.mocked(currentUser).mockResolvedValue({ data: DIRECTOR } as never);

    renderProvider();

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(screen.getByTestId('user')).toHaveTextContent('Vera Lindholm');
    expect(getAuthToken()).toBe('stored-token');
  });

  it('signs out when a stored token is no longer accepted', async () => {
    storedSession('revoked-token');
    vi.mocked(currentUser).mockResolvedValue({ data: undefined, error: {} } as never);

    renderProvider();

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
    expect(window.localStorage.getItem('mc.auth')).toBeNull();
  });

  it('stores the session on a successful login', async () => {
    vi.mocked(login).mockResolvedValue({
      data: {
        token: 'fresh-token',
        expiresAt: new Date(Date.now() + 8 * 3_600_000).toISOString(),
        user: DIRECTOR,
      },
    } as never);

    renderProvider();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));

    fireEvent.click(screen.getByText('sign in'));

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(getAuthToken()).toBe('fresh-token');
    expect(window.localStorage.getItem('mc.auth')).toContain('fresh-token');
  });

  it('clears everything on logout', async () => {
    storedSession('stored-token');
    vi.mocked(currentUser).mockResolvedValue({ data: DIRECTOR } as never);
    vi.mocked(logout).mockResolvedValue({ data: undefined } as never);

    renderProvider();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    fireEvent.click(screen.getByText('sign out'));

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
    expect(getAuthToken()).toBeNull();
    expect(window.localStorage.getItem('mc.auth')).toBeNull();
  });

  it('signs out locally even when the logout request fails', async () => {
    // A token already revoked elsewhere makes logout fail. Refusing to sign out would strand the
    // user on a screen where nothing works.
    storedSession('stored-token');
    vi.mocked(currentUser).mockResolvedValue({ data: DIRECTOR } as never);
    vi.mocked(logout).mockRejectedValue(new Error('network down'));

    renderProvider();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    fireEvent.click(screen.getByText('sign out'));

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
    expect(window.localStorage.getItem('mc.auth')).toBeNull();
  });
});
