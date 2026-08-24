import { ThemeProvider } from '@mui/material/styles';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../../auth/AuthContext';
import { theme } from '../../theme';
import AccountMenu from '../AccountMenu';

const DIRECTOR = {
  id: 'a1000000-0000-0000-0000-000000000001',
  fullName: 'Vera Lindholm',
  email: 'vera.lindholm@orbitaldynamics.example',
  role: 'DIRECTOR' as const,
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

function renderMenu(overrides: Partial<AuthContextValue> = {}) {
  const logout = vi.fn().mockResolvedValue(undefined);
  const value: AuthContextValue = {
    status: 'authenticated',
    user: DIRECTOR,
    login: vi.fn(),
    logout,
    ...overrides,
  };

  render(
    <ThemeProvider theme={theme}>
      <AuthContext.Provider value={value}>
        <AccountMenu />
      </AuthContext.Provider>
    </ThemeProvider>,
  );

  return { logout };
}

function trigger() {
  return screen.getByRole('button', { name: /Vera Lindholm/ });
}

describe('AccountMenu', () => {
  it('shows the signed-in user as the trigger', () => {
    renderMenu();

    expect(trigger()).toBeInTheDocument();
  });

  it('keeps the menu closed until the trigger is clicked', () => {
    renderMenu();

    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /Sign out/ })).not.toBeInTheDocument();
    expect(trigger()).toHaveAttribute('aria-haspopup', 'true');
    expect(trigger()).not.toHaveAttribute('aria-expanded');
  });

  it('opens a menu containing sign out', async () => {
    renderMenu();

    // Held rather than re-queried: MUI's Menu is modal, so once it opens the trigger is
    // aria-hidden and no longer reachable by role.
    const button = trigger();
    fireEvent.click(button);

    const menu = await screen.findByRole('menu');
    expect(within(menu).getByRole('menuitem', { name: /Sign out/ })).toBeInTheDocument();
    expect(button).toHaveAttribute('aria-expanded', 'true');
  });

  it('shows the role and organisation inside the menu, not on the trigger', async () => {
    renderMenu();

    // The collapsed trigger is just the name; the detail belongs in the dropdown.
    const button = trigger();
    expect(button).not.toHaveTextContent('Orbital Dynamics');

    fireEvent.click(button);

    const menu = await screen.findByRole('menu');
    expect(within(menu).getByText(/Director · Orbital Dynamics/)).toBeInTheDocument();
    expect(within(menu).getByText(DIRECTOR.email)).toBeInTheDocument();
  });

  it('renders the role readably rather than as the wire value', async () => {
    renderMenu({ user: { ...DIRECTOR, role: 'MISSION_LEAD' } });

    fireEvent.click(screen.getByRole('button', { name: /Vera Lindholm/ }));

    const menu = await screen.findByRole('menu');
    expect(within(menu).getByText(/Mission Lead · Orbital Dynamics/)).toBeInTheDocument();
    expect(within(menu).queryByText(/MISSION_LEAD/)).not.toBeInTheDocument();
  });

  it('signs out when the menu item is chosen', async () => {
    const { logout } = renderMenu();

    fireEvent.click(trigger());
    fireEvent.click(await screen.findByRole('menuitem', { name: /Sign out/ }));

    await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
  });

  it('closes the menu when dismissed without signing out', async () => {
    const { logout } = renderMenu();

    fireEvent.click(trigger());
    await screen.findByRole('menu');

    fireEvent.keyDown(await screen.findByRole('menu'), { key: 'Escape', code: 'Escape' });

    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
    expect(logout).not.toHaveBeenCalled();
  });

  it('renders nothing when there is no user', () => {
    renderMenu({ user: null });

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
