import { ThemeProvider } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';
import { AuthContext, type AuthContextValue, type AuthStatus } from '../auth/AuthContext';
import { theme } from '../theme';

// SystemInfoPage issues a real query; it is not what these tests are about.
vi.mock('../pages/SystemInfoPage', () => ({
  default: () => <div>Backend status</div>,
}));

const DIRECTOR = {
  id: 'a1000000-0000-0000-0000-000000000001',
  fullName: 'Vera Lindholm',
  email: 'vera.lindholm@orbitaldynamics.example',
  role: 'DIRECTOR' as const,
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

function renderApp(status: AuthStatus) {
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
          <App />
        </AuthContext.Provider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

describe('App', () => {
  it('shows the sign-in form, and no account menu, when unauthenticated', () => {
    renderApp('anonymous');

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.queryByText('Backend status')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Vera Lindholm/ })).not.toBeInTheDocument();
  });

  it('shows the application and the account menu when authenticated', () => {
    renderApp('authenticated');

    expect(screen.getByText('Backend status')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Vera Lindholm/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument();
  });

  it('reaches sign out through the account menu', async () => {
    renderApp('authenticated');

    // Deliberately not on the page directly any more - it lives behind the dropdown.
    expect(screen.queryByRole('menuitem', { name: /Sign out/ })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Vera Lindholm/ }));

    expect(await screen.findByRole('menuitem', { name: /Sign out/ })).toBeInTheDocument();
  });

  it('shows neither screen while a stored session is being restored', () => {
    // Otherwise every refresh flashes a login form at someone who is already signed in.
    renderApp('loading');

    expect(screen.getByText('Restoring session…')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument();
    expect(screen.queryByText('Backend status')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Vera Lindholm/ })).not.toBeInTheDocument();
  });
});
