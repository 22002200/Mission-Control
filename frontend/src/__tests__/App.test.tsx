import { ThemeProvider } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';
import { AuthContext, type AuthContextValue, type AuthStatus } from '../auth/AuthContext';
import { theme } from '../theme';

// The pages issue real queries; routing is what these tests are about.
vi.mock('../pages/MissionsPage', () => ({
  default: () => <div>Mission board</div>,
}));
vi.mock('../pages/MissionDetailPage', () => ({
  default: () => <div>Mission detail</div>,
}));

const DIRECTOR = {
  id: 'a1000000-0000-0000-0000-000000000001',
  fullName: 'Vera Lindholm',
  email: 'vera.lindholm@orbitaldynamics.example',
  role: 'DIRECTOR' as const,
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

function renderApp(status: AuthStatus, initialEntry = '/missions') {
  const value: AuthContextValue = {
    status,
    user: status === 'authenticated' ? DIRECTOR : null,
    login: vi.fn(),
    logout: vi.fn(),
  };

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={value}>
          <MemoryRouter initialEntries={[initialEntry]}>
            <App />
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

describe('App', () => {
  it('sends an anonymous visitor to the sign-in form', () => {
    renderApp('anonymous');

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.queryByText('Mission board')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Vera Lindholm/ })).not.toBeInTheDocument();
  });

  it('shows the mission board and the account menu when authenticated', () => {
    renderApp('authenticated');

    expect(screen.getByText('Mission board')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Vera Lindholm/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument();
  });

  it('routes straight to one mission when the URL names it', () => {
    // The whole reason for adding a router: a mission has to be linkable.
    renderApp('authenticated', '/missions/a4000000-0000-0000-0000-000000000001');

    expect(screen.getByText('Mission detail')).toBeInTheDocument();
  });

  it('sends an unknown path to the mission board', () => {
    renderApp('authenticated', '/nowhere');

    expect(screen.getByText('Mission board')).toBeInTheDocument();
  });

  it('keeps a deep link behind the sign-in form when there is no session', () => {
    renderApp('anonymous', '/missions/a4000000-0000-0000-0000-000000000001');

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.queryByText('Mission detail')).not.toBeInTheDocument();
  });

  it('shows neither screen while a stored session is being restored', () => {
    // Otherwise every refresh flashes a login form at someone who is already signed in.
    renderApp('loading');

    expect(screen.getByText('Restoring session…')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument();
    expect(screen.queryByText('Mission board')).not.toBeInTheDocument();
  });
});
