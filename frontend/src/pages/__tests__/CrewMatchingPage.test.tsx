import { ThemeProvider } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getMission, matchAll, matchRequirement } from '../../api/generated/sdk.gen';
import type {
  CandidateResponse,
  CurrentUserResponse,
  MissionResponse,
  RequirementMatchResponse,
} from '../../api/generated/types.gen';
import { AuthContext, type AuthContextValue } from '../../auth/AuthContext';
import { theme } from '../../theme';
import CrewMatchingPage from '../CrewMatchingPage';

// Mock `sdk.gen`, not the barrel: the generated react-query helpers import straight from it.
vi.mock('../../api/generated/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/generated/sdk.gen')>()),
  getMission: vi.fn(),
  matchAll: vi.fn(),
  matchRequirement: vi.fn(),
}));

const MISSION_ID = 'a4000000-0000-0000-0000-000000000001';
const FLIGHT_ENGINEER = 'a5000000-0000-0000-0000-000000000001';
const SCIENCE_OFFICER = 'a5000000-0000-0000-0000-000000000002';

const ADA = 'a3000000-0000-0000-0000-000000000001';
const BRUNO = 'a3000000-0000-0000-0000-000000000002';
const CHEN = 'a3000000-0000-0000-0000-000000000003';

const LEAD: CurrentUserResponse = {
  id: 'a1000000-0000-0000-0000-000000000002',
  fullName: 'Marcus Reyes',
  email: 'marcus.reyes@orbitaldynamics.example',
  role: 'MISSION_LEAD',
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

/** A distinct id matters: sharing the lead's would make the ownership check pass by accident. */
const OTHER_LEAD: CurrentUserResponse = {
  ...LEAD,
  id: 'a1000000-0000-0000-0000-000000000003',
  fullName: 'Priya Raman',
};

const DIRECTOR: CurrentUserResponse = {
  ...LEAD,
  id: 'a1000000-0000-0000-0000-000000000001',
  role: 'DIRECTOR',
  fullName: 'Vera Lindholm',
};

function mission(overrides: Partial<MissionResponse> = {}): MissionResponse {
  return {
    id: MISSION_ID,
    name: 'Aurora Survey',
    description: 'Mapping auroral activity.',
    status: 'PLAN',
    startsAt: '2026-09-01T08:00:00Z',
    endsAt: '2026-09-14T17:00:00Z',
    missionLead: { id: LEAD.id, fullName: LEAD.fullName },
    fullyStaffed: false,
    requirements: [
      {
        id: FLIGHT_ENGINEER,
        title: 'Flight Engineer',
        requiredCount: 2,
        acceptedCount: 0,
        skills: [],
      },
    ],
    ...overrides,
  };
}

function candidate(
  crewMemberId: string,
  fullName: string,
  score: number,
  shortfalls: CandidateResponse['shortfalls'] = [],
): CandidateResponse {
  return {
    crewMemberId,
    fullName,
    score,
    breakdown: {
      skillScore: score,
      experienceBonus: 0,
      completedMissions: 0,
      loadPenalty: 0,
      recentAssignments: 0,
    },
    skills: [
      {
        skillId: 'a2000000-0000-0000-0000-000000000001',
        skillName: 'EVA Operations',
        required: 3,
        actual: 3,
        mandatory: true,
        weight: 1,
        contribution: 1,
      },
    ],
    shortfalls,
  };
}

function requirementMatch(
  overrides: Partial<RequirementMatchResponse> = {},
): RequirementMatchResponse {
  return {
    requirementId: FLIGHT_ENGINEER,
    title: 'Flight Engineer',
    requiredCount: 2,
    acceptedCount: 0,
    offeredCount: 0,
    openSeats: 2,
    remainingCount: 0,
    candidates: [],
    ...overrides,
  };
}

function renderPage(user: CurrentUserResponse, data: MissionResponse = mission()) {
  vi.mocked(getMission).mockResolvedValue({ data } as never);

  const value: AuthContextValue = {
    status: 'authenticated',
    user,
    login: vi.fn(),
    logout: vi.fn(),
  };

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={value}>
          <MemoryRouter initialEntries={[`/missions/${MISSION_ID}/crew`]}>
            <Routes>
              <Route path="/missions/:missionId/crew" element={<CrewMatchingPage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

/** The `exclude` array the component sent on its most recent per-requirement call. */
function lastExclude(): string[] {
  const calls = vi.mocked(matchRequirement).mock.calls;
  const options = calls[calls.length - 1]![0] as { query?: { exclude?: string[] } };
  return options.query?.exclude ?? [];
}

describe('CrewMatchingPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists the mission requirements with empty seats before anything is matched', async () => {
    renderPage(LEAD);

    expect(await screen.findByText('Flight Engineer')).toBeInTheDocument();
    expect(screen.getByText('0 of 2 seats drafted')).toBeInTheDocument();
    expect(screen.getAllByText('Empty')).toHaveLength(2);
  });

  it('says plainly that nothing is saved and nobody is offered anything', async () => {
    renderPage(LEAD);

    expect(await screen.findByText(/A draft is not saved/)).toBeInTheDocument();
  });

  it('fills the seats when the lead drafts a whole crew', async () => {
    vi.mocked(matchAll).mockResolvedValue({
      data: {
        missionId: MISSION_ID,
        requirements: [
          requirementMatch({
            candidates: [candidate(ADA, 'Ada Kowalski', 1), candidate(CHEN, 'Chen Ibarra', 0.75)],
            remainingCount: 1,
          }),
        ],
      },
    } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match all' }));

    expect(await screen.findByText('Seat 1 — Ada Kowalski')).toBeInTheDocument();
    expect(screen.getByText('Seat 2 — Chen Ibarra')).toBeInTheDocument();
    expect(screen.getByText('2 of 2 seats drafted')).toBeInTheDocument();
  });

  it('shows the score breakdown when a candidate is expanded', async () => {
    vi.mocked(matchRequirement).mockResolvedValue({
      data: requirementMatch({
        candidates: [candidate(BRUNO, 'Bruno Sato', 0.5)],
        remainingCount: 0,
      }),
    } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Why Bruno Sato is ranked here' }));

    // FR-4: which skills matched, and what the experience and load terms contributed.
    expect(await screen.findByText('EVA Operations')).toBeInTheDocument();
    expect(screen.getByText(/Skill fit 0\.500/)).toBeInTheDocument();
    expect(screen.getByText(/Experience \+0\.000 \(0 completed\)/)).toBeInTheDocument();
    expect(screen.getByText(/Load −0\.000 \(0 recent\)/)).toBeInTheDocument();
  });

  it('flags a candidate who falls short on a preferred skill', async () => {
    vi.mocked(matchRequirement).mockResolvedValue({
      data: requirementMatch({
        candidates: [
          candidate(CHEN, 'Chen Ibarra', 0.75, [
            {
              skillId: 'a2000000-0000-0000-0000-000000000002',
              skillName: 'Robotics',
              required: 4,
              actual: 2,
              mandatory: false,
              weight: 1,
              contribution: 0.5,
            },
          ]),
        ],
      }),
    } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match' }));

    expect(await screen.findByText('Short on 1')).toBeInTheDocument();
  });

  /**
   * The rule the whole exclude contract exists for. A rematch has to leave out everyone already
   * drafted anywhere on the mission as well as everyone this line has already shown - otherwise
   * the next page repeats people, or drafts one person into two seats.
   */
  it('excludes everyone already drafted and already shown when rematching', async () => {
    vi.mocked(matchAll).mockResolvedValue({
      data: {
        missionId: MISSION_ID,
        requirements: [
          requirementMatch({
            candidates: [candidate(ADA, 'Ada Kowalski', 1)],
            openSeats: 1,
            remainingCount: 2,
          }),
          requirementMatch({
            requirementId: SCIENCE_OFFICER,
            title: 'Science Officer',
            openSeats: 1,
            candidates: [candidate(CHEN, 'Chen Ibarra', 0.9)],
            remainingCount: 2,
          }),
        ],
      },
    } as never);
    vi.mocked(matchRequirement).mockResolvedValue({
      data: requirementMatch({
        openSeats: 1,
        candidates: [candidate(BRUNO, 'Bruno Sato', 0.5)],
        remainingCount: 1,
      }),
    } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match all' }));
    await screen.findByText('Seat 1 — Ada Kowalski');

    const flightEngineer = screen.getByText('Flight Engineer').closest('.MuiCard-root')!;
    fireEvent.click(within(flightEngineer as HTMLElement).getByRole('button', { name: 'Rematch' }));

    await waitFor(() => expect(matchRequirement).toHaveBeenCalled());
    // Ada was drafted on this line, Chen on the other one, and both were shown by Match all.
    expect(lastExclude()).toEqual(expect.arrayContaining([ADA, CHEN]));
  });

  it('accumulates the seen set across rematches so the list does not cycle', async () => {
    vi.mocked(matchRequirement)
      .mockResolvedValueOnce({
        data: requirementMatch({
          candidates: [candidate(ADA, 'Ada Kowalski', 1)],
          remainingCount: 2,
        }),
      } as never)
      .mockResolvedValueOnce({
        data: requirementMatch({
          candidates: [candidate(CHEN, 'Chen Ibarra', 0.75)],
          remainingCount: 1,
        }),
      } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match' }));
    await screen.findByRole('button', { name: 'Why Ada Kowalski is ranked here' });

    fireEvent.click(screen.getByRole('button', { name: 'Rematch' }));
    await waitFor(() => expect(matchRequirement).toHaveBeenCalledTimes(2));
    expect(lastExclude()).toContain(ADA);

    fireEvent.click(screen.getByRole('button', { name: 'Rematch' }));
    await waitFor(() => expect(matchRequirement).toHaveBeenCalledTimes(3));
    // Both batches, not just the one currently on screen. Without this the third call would ask
    // for the first page again.
    expect(lastExclude()).toEqual(expect.arrayContaining([ADA, CHEN]));
  });

  it('stops offering a rematch once nobody else is eligible', async () => {
    vi.mocked(matchRequirement).mockResolvedValue({
      data: requirementMatch({ candidates: [], remainingCount: 0 }),
    } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match' }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Match' })).toBeDisabled(),
    );
    expect(screen.getByText('Nobody else is eligible for this requirement.')).toBeInTheDocument();
  });

  it('says how many candidates are still unseen', async () => {
    vi.mocked(matchRequirement).mockResolvedValue({
      data: requirementMatch({
        candidates: [candidate(ADA, 'Ada Kowalski', 1)],
        remainingCount: 4,
      }),
    } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match' }));

    expect(await screen.findByText('4 other candidates not yet shown.')).toBeInTheDocument();
  });

  it('drafts a suggestion into the first empty seat, and can undo it', async () => {
    vi.mocked(matchRequirement).mockResolvedValue({
      data: requirementMatch({
        candidates: [candidate(ADA, 'Ada Kowalski', 1)],
        remainingCount: 1,
      }),
    } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Draft' }));

    expect(await screen.findByText('Seat 1 — Ada Kowalski')).toBeInTheDocument();

    // Removing puts them back in the suggestions rather than losing them entirely.
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(
      await screen.findByRole('button', { name: 'Why Ada Kowalski is ranked here' }),
    ).toBeInTheDocument();
    expect(screen.getByText('0 of 2 seats drafted')).toBeInTheDocument();
  });

  it('refuses to draft anyone once every seat is taken, and says why', async () => {
    vi.mocked(matchRequirement).mockResolvedValue({
      data: requirementMatch({
        openSeats: 1,
        candidates: [candidate(ADA, 'Ada Kowalski', 1), candidate(CHEN, 'Chen Ibarra', 0.75)],
        remainingCount: 0,
      }),
    } as never);

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match' }));
    fireEvent.click((await screen.findAllByRole('button', { name: 'Draft' }))[0]!);

    const remaining = await screen.findAllByRole('button', { name: 'Draft' });
    expect(remaining[0]).toBeDisabled();
    expect(remaining[0]).toHaveAttribute(
      'title',
      'Every seat is drafted. Remove someone first.',
    );
  });

  it('lets a director draft a crew as well as the owning lead', async () => {
    renderPage(DIRECTOR);

    expect(await screen.findByRole('button', { name: 'Match all' })).toBeInTheDocument();
  });

  it('refuses a lead who does not own the mission, rather than offering a failing button', async () => {
    renderPage(OTHER_LEAD);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Only the mission lead who owns this mission, or a director/,
    );
    expect(screen.queryByRole('button', { name: 'Match all' })).not.toBeInTheDocument();
  });

  it('explains that a mission with nothing to staff has nobody to look for', async () => {
    renderPage(LEAD, mission({ requirements: [] }));

    expect(await screen.findByText(/no crew requirements yet/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Match all' })).toBeDisabled();
  });

  it('reports a failed match rather than leaving the screen unchanged', async () => {
    vi.mocked(matchAll).mockRejectedValue({
      type: 'urn:mission-control:forbidden',
      detail: 'Only the mission lead who owns this mission can do that.',
    });

    renderPage(LEAD);
    fireEvent.click(await screen.findByRole('button', { name: 'Match all' }));

    expect(await screen.findByText(/Only the mission lead who owns this mission can do that/))
      .toBeInTheDocument();
  });
});
