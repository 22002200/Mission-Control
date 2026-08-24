import { ThemeProvider } from '@mui/material/styles';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../../auth/AuthContext';
import { LoginFailedError } from '../../auth/LoginFailedError';
import { theme } from '../../theme';
import LoginPage from '../LoginPage';

function renderWith(login: AuthContextValue['login']) {
  const value: AuthContextValue = {
    status: 'anonymous',
    user: null,
    login,
    logout: vi.fn(),
  };
  // A router is needed now: the page redirects an already-signed-in visitor onwards, and reads
  // the attempted path out of location state.
  return render(
    <ThemeProvider theme={theme}>
      <AuthContext.Provider value={value}>
        <MemoryRouter initialEntries={['/login']}>
          <LoginPage />
        </MemoryRouter>
      </AuthContext.Provider>
    </ThemeProvider>,
  );
}

function fillIn(email: string, password: string) {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
}

describe('LoginPage', () => {
  it('renders an email and a masked password field', () => {
    renderWith(vi.fn());

    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('submits the entered credentials', async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    renderWith(login);

    fillIn('vera.lindholm@orbitaldynamics.example', 'Password123!');
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() =>
      expect(login).toHaveBeenCalledWith('vera.lindholm@orbitaldynamics.example', 'Password123!'),
    );
  });

  it('shows the reason when the credentials are rejected', async () => {
    const login = vi
      .fn()
      .mockRejectedValue(
        new LoginFailedError(
          'Email or password is incorrect.',
          'urn:mission-control:invalid-credentials',
        ),
      );
    renderWith(login);

    fillIn('vera@x.example', 'wrong');
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
  });

  it('shows the disabled-account message distinctly', async () => {
    const login = vi
      .fn()
      .mockRejectedValue(
        new LoginFailedError(
          'This account has been disabled.',
          'urn:mission-control:account-disabled',
        ),
      );
    renderWith(login);

    fillIn('oona@x.example', 'Password123!');
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('This account has been disabled.');
  });

  it('disables the button while the request is in flight', async () => {
    let release: () => void = () => {};
    const login = vi.fn().mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          release = resolve;
        }),
    );
    renderWith(login);

    fillIn('vera@x.example', 'Password123!');
    fireEvent.click(screen.getByRole('button', { name: /Signing in|Sign in/ }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled());

    release();
  });

  it('never renders the typed password back into the document', async () => {
    // The value lives in the input, but nothing should echo it into an error message or the DOM
    // text - that is how passwords end up in screenshots and bug reports.
    const login = vi
      .fn()
      .mockRejectedValue(new LoginFailedError('Email or password is incorrect.'));
    renderWith(login);

    fillIn('vera@x.example', 'super-secret-value');
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await screen.findByRole('alert');
    expect(document.body.textContent).not.toContain('super-secret-value');
  });
});
