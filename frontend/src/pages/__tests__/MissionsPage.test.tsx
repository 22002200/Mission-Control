import { ThemeProvider } from '@mui/material/styles';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listMissions } from '../../api/generated/sdk.gen';
import type { CurrentUserResponse } from '../../api/generated/types.gen';
import { AuthContext, type AuthContextValue } from '../../auth/AuthContext';
import { theme } from '../../theme';
import MissionsPage from '../MissionsPage';

// The generated SDK is the boundary. Mocking it keeps these tests about the board's behaviour
// rather than about fetch, and avoids adding an HTTP mocking dependency for a handful of tests.
//
// Mock `sdk.gen` rather than the barrel: the generated react-query helpers import straight from
// it, so a mock on the barrel is simply never consulted.
vi.mock('../../api/generated/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/generated/sdk.gen')>()),
  listMissions: vi.fn(),
  listSkills: vi.fn().mockResolvedValue({
    data: { content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 },
  }),
}));

const LEAD: CurrentUserResponse = {
  id: 'a1000000-0000-0000-0000-000000000002',
  fullName: 'Marcus Reyes',
  email: 'marcus.reyes@orbitaldynamics.example',
  role: 'MISSION_LEAD',
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

const DIRECTOR: CurrentUserResponse = {
  ...LEAD,
  id: 'a1000000-0000-0000-0000-000000000001',
  role: 'DIRECTOR',
  fullName: 'Vera Lindholm',
};

function summary(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'a4000000-0000-0000-0000-000000000001',
    name: 'Aurora Survey',
    status: 'PLAN',
    startsAt: '2026-09-01T08:00:00Z',
    endsAt: '2026-09-14T17:00:00Z',
    missionLead: { id: LEAD.id, fullName: LEAD.fullName },
    acceptedCount: 0,
    requiredCount: 4,
    fullyStaffed: false,
    ...overrides,
  };
}

/** Answers each section's query with whatever matches the statuses it asked for. */
function respondWith(missions: ReturnType<typeof summary>[]) {
  vi.mocked(listMissions).mockImplementation((options) => {
    const wanted = (options?.query?.status ?? []) as string[];
    const matching = missions.filter((mission) => wanted.includes(mission.status as string));
    return Promise.resolve({
      data: {
        content: matching,
        page: 0,
        size: 12,
        totalElements: matching.length,
        totalPages: matching.length > 0 ? 1 : 0,
      },
    }) as never;
  });
}

function renderBoard(user: CurrentUserResponse, initialEntry = '/missions') {
  const value: AuthContextValue = {
    status: 'authenticated',
    user,
    login: vi.fn(),
    logout: vi.fn(),
  };
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <ThemeProvider theme={theme}>
      {/* The create dialog holds date pickers, which need an adapter in scope. */}
      <LocalizationProvider dateAdapter={AdapterDayjs}>
        <QueryClientProvider client={queryClient}>
          <AuthContext.Provider value={value}>
            <MemoryRouter initialEntries={[initialEntry]}>
              <MissionsPage />
            </MemoryRouter>
          </AuthContext.Provider>
        </QueryClientProvider>
      </LocalizationProvider>
    </ThemeProvider>,
  );
}

describe('MissionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    respondWith([]);
  });

  it('shows every lifecycle section', async () => {
    renderBoard(DIRECTOR);

    expect(await screen.findByRole('heading', { name: 'Draft' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Active' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Completed' })).toBeInTheDocument();
  });

  it('gives a director their own queue of decisions to make', async () => {
    // FR-8. Directors have to be able to find the missions waiting on them, and two of those buried
    // among everything else still in planning is how a queue goes unnoticed.
    renderBoard(DIRECTOR);

    expect(
      await screen.findByRole('heading', { name: 'Awaiting approval' }),
    ).toBeInTheDocument();
  });

  it('does not show a mission lead an approval queue they cannot act on', async () => {
    renderBoard(LEAD);

    expect(await screen.findByRole('heading', { name: 'Draft' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Awaiting approval' })).not.toBeInTheDocument();
  });

  it('asks each section only for the statuses it owns', async () => {
    renderBoard(DIRECTOR);

    await waitFor(() => expect(listMissions).toHaveBeenCalledTimes(4));

    const requested = vi.mocked(listMissions).mock.calls.map((call) => call[0]?.query?.status);
    // PENDING_APPROVAL is moved out of Draft rather than duplicated: a mission in two sections
    // would be counted twice and paged independently in each.
    expect(requested).toContainEqual(['PENDING_APPROVAL']);
    expect(requested).toContainEqual(['PLAN', 'APPROVED', 'REJECTED']);
    expect(requested).toContainEqual(['ACTIVE']);
    expect(requested).toContainEqual(['CLOSED']);
  });

  it('leaves a mission lead the original three sections', async () => {
    renderBoard(LEAD);

    await waitFor(() => expect(listMissions).toHaveBeenCalledTimes(3));

    const requested = vi.mocked(listMissions).mock.calls.map((call) => call[0]?.query?.status);
    expect(requested).toContainEqual(['PLAN', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED']);
  });

  it('keeps an empty section visible and says it is empty', async () => {
    // Hiding it would leave a lead wondering whether they have nothing active or whether the page
    // is broken.
    renderBoard(DIRECTOR);

    expect(await screen.findByText('No missions are running.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Active' })).toBeInTheDocument();
  });

  it('places each mission under the section its status belongs to', async () => {
    respondWith([
      summary({ id: '1', name: 'Aurora Survey', status: 'PLAN' }),
      summary({ id: '2', name: 'Vesta Flyby', status: 'ACTIVE' }),
      summary({ id: '3', name: 'Kuiper Probe', status: 'CLOSED', closeReason: 'COMPLETED' }),
    ]);
    renderBoard(DIRECTOR);

    expect(await screen.findByText('Aurora Survey')).toBeInTheDocument();
    expect(screen.getByText('Vesta Flyby')).toBeInTheDocument();

    // Scoped to the card rather than the page: 'Completed' is also a section heading, so a bare
    // text query would match either and prove nothing about the chip.
    const closedCard = screen.getByRole('link', { name: /Kuiper Probe/ });
    expect(closedCard).toHaveTextContent('Completed');
  });

  it('collapses to one section when a specific status is chosen', async () => {
    renderBoard(DIRECTOR, '/missions?status=ACTIVE');

    expect(await screen.findByRole('heading', { name: 'Active' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Draft' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Completed' })).not.toBeInTheDocument();

    await waitFor(() => expect(listMissions).toHaveBeenCalledTimes(1));
    expect(vi.mocked(listMissions).mock.calls[0]![0]?.query?.status).toEqual(['ACTIVE']);
  });

  it('offers New mission to a mission lead', async () => {
    renderBoard(LEAD);

    expect(await screen.findByRole('button', { name: 'New mission' })).toBeInTheDocument();
  });

  it('does not offer New mission to a director, who cannot own one', async () => {
    renderBoard(DIRECTOR);

    await screen.findByRole('heading', { name: 'Draft' });
    expect(screen.queryByRole('button', { name: 'New mission' })).not.toBeInTheDocument();
  });

  it('passes the search term through to every section', async () => {
    renderBoard(DIRECTOR, '/missions?search=aurora');

    await waitFor(() =>
      expect(vi.mocked(listMissions).mock.calls.length).toBeGreaterThanOrEqual(3),
    );
    vi.mocked(listMissions).mock.calls.forEach((call) => {
      expect(call[0]?.query?.search).toBe('aurora');
    });
  });

  it('reports a failure without taking down the rest of the board', async () => {
    vi.mocked(listMissions).mockRejectedValue({
      type: 'urn:mission-control:not-found',
    } as never);
    renderBoard(DIRECTOR);

    const alerts = await screen.findAllByRole('alert');
    expect(alerts.length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { name: 'Draft' })).toBeInTheDocument();
  });

  it('opens the create dialog from the New mission button', async () => {
    renderBoard(LEAD);

    fireEvent.click(await screen.findByRole('button', { name: 'New mission' }));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'New mission' })).toBeInTheDocument();
  });
});
