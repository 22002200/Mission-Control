import { ThemeProvider } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  acceptAssignment,
  declineAssignment,
  listMyAssignments,
} from '../../api/generated/sdk.gen';
import type {
  CurrentUserResponse,
  MyAssignmentPage,
  MyAssignmentResponse,
} from '../../api/generated/types.gen';
import { AuthContext, type AuthContextValue } from '../../auth/AuthContext';
import { theme } from '../../theme';
import MyAssignmentsSection from '../assignments/MyAssignmentsSection';

// Mock `sdk.gen`, not the barrel: the generated react-query helpers import straight from it.
vi.mock('../../api/generated/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/generated/sdk.gen')>()),
  listMyAssignments: vi.fn(),
  acceptAssignment: vi.fn(),
  declineAssignment: vi.fn(),
}));

const CREW: CurrentUserResponse = {
  id: 'a1000000-0000-0000-0000-000000000007',
  fullName: 'Dana Osei',
  email: 'dana.osei@orbitaldynamics.example',
  role: 'CREW_MEMBER',
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

function assignment(overrides: Partial<MyAssignmentResponse> = {}): MyAssignmentResponse {
  return {
    id: 'b6000000-0000-0000-0000-000000000001',
    status: 'OFFERED',
    offeredAt: '2026-01-06T09:00:00Z',
    mission: {
      id: 'a4000000-0000-0000-0000-000000000005',
      name: 'Helix Resupply',
      status: 'APPROVED',
      startsAt: '2026-09-20T05:30:00Z',
      endsAt: '2026-10-02T12:00:00Z',
    },
    requirementTitle: 'Flight Engineer',
    ...overrides,
  };
}

function page(content: MyAssignmentResponse[]): MyAssignmentPage {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 };
}

function renderSection(content: MyAssignmentResponse[] = [assignment()]) {
  vi.mocked(listMyAssignments).mockResolvedValue({ data: page(content) } as never);

  const value: AuthContextValue = {
    status: 'authenticated',
    user: CREW,
    login: vi.fn(),
    logout: vi.fn(),
  };

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={value}>
          <MemoryRouter>
            <MyAssignmentsSection />
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

/** The query the component sent on its most recent list call. */
function lastQuery(): Record<string, unknown> {
  const calls = vi.mocked(listMyAssignments).mock.calls;
  const options = calls[calls.length - 1]![0] as { query?: Record<string, unknown> };
  return options.query ?? {};
}

describe('MyAssignmentsSection', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows an open offer with the mission, the line and the dates', async () => {
    renderSection();

    expect(await screen.findByText('Helix Resupply')).toBeInTheDocument();
    expect(screen.getByText('Flight Engineer')).toBeInTheDocument();
    // Two chips, because they say different things: where the mission is, and where this person's
    // place on it has got to.
    expect(screen.getByText('Offered')).toBeInTheDocument();
    expect(screen.getByText('Approved')).toBeInTheDocument();
  });

  it('accepts an offer and asks the server to refresh what it affects', async () => {
    vi.mocked(acceptAssignment).mockResolvedValue({ data: {} } as never);
    renderSection();

    fireEvent.click(await screen.findByRole('button', { name: 'Accept' }));

    await waitFor(() =>
      expect(acceptAssignment).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { assignmentId: 'b6000000-0000-0000-0000-000000000001' },
        }),
      ),
    );
  });

  it('declines an offer without sending a body', async () => {
    vi.mocked(declineAssignment).mockResolvedValue({ data: {} } as never);
    renderSection();

    fireEvent.click(await screen.findByRole('button', { name: 'Decline' }));

    // No reason field: feature 07 originally allowed one and there was nowhere to store it, so it
    // was dropped rather than accepted and discarded.
    await waitFor(() => expect(declineAssignment).toHaveBeenCalledTimes(1));
    const options = vi.mocked(declineAssignment).mock.calls[0]![0] as { body?: unknown };
    expect(options.body).toBeUndefined();
  });

  it('names the clashing mission when an acceptance conflicts', async () => {
    vi.mocked(acceptAssignment).mockRejectedValue({
      type: 'urn:mission-control:schedule-conflict',
      detail: 'This clashes with Vesta Flyby, which you have already accepted.',
    });
    renderSection();

    fireEvent.click(await screen.findByRole('button', { name: 'Accept' }));

    // The error this feature exists to produce, and a normal outcome rather than a fault: offers
    // reserve nobody, so two leads may legitimately ask the same person for the same dates.
    expect(await screen.findByText(/This clashes with Vesta Flyby/)).toBeInTheDocument();
  });

  it('offers no buttons once a place has been accepted', async () => {
    renderSection([assignment({ status: 'ACCEPTED', respondedAt: '2026-01-07T10:00:00Z' })]);

    expect(await screen.findByText('Accepted')).toBeInTheDocument();
    // Once accepted they are assigned. Releasing them is the mission lead's decision, so there is
    // no button here rather than one that would be refused.
    expect(screen.queryByRole('button', { name: 'Accept' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Decline' })).not.toBeInTheDocument();
  });

  it('puts open offers first, however the server ordered them', async () => {
    renderSection([
      assignment({ id: 'settled', status: 'ACCEPTED', respondedAt: '2026-01-07T10:00:00Z' }),
      assignment({ id: 'open', status: 'OFFERED' }),
    ]);

    await screen.findByText('Accepted');
    // The server sorts by when the offer was made, which buries the one row needing an answer.
    const statuses = screen.getAllByText(/^(Offered|Accepted)$/).map((node) => node.textContent);
    expect(statuses[0]).toBe('Offered');
  });

  it('sends the status and timeframe filters, and resets to the first page', async () => {
    renderSection();
    await screen.findByText('Helix Resupply');

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Assignment status' }));
    fireEvent.click(await screen.findByRole('option', { name: 'Accepted' }));

    await waitFor(() => expect(lastQuery().status).toBe('ACCEPTED'));

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'When' }));
    fireEvent.click(await screen.findByRole('option', { name: 'Still to come' }));

    await waitFor(() => expect(lastQuery().timeframe).toBe('UPCOMING'));
    expect(lastQuery().page).toBe(0);
  });

  it('says nothing has been offered yet rather than showing an empty box', async () => {
    renderSection([]);

    expect(
      await screen.findByText('You have not been offered a place on any mission yet.'),
    ).toBeInTheDocument();
  });
});
