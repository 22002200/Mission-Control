import { ThemeProvider } from '@mui/material/styles';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getMission, listMissionApprovals, listSkills } from '../../api/generated/sdk.gen';
import type { CurrentUserResponse, MissionResponse } from '../../api/generated/types.gen';
import { AuthContext, type AuthContextValue } from '../../auth/AuthContext';
import { theme } from '../../theme';
import MissionDetailPage from '../MissionDetailPage';

// Mock `sdk.gen`, not the barrel: the generated react-query helpers import straight from it.
vi.mock('../../api/generated/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/generated/sdk.gen')>()),
  getMission: vi.fn(),
  listSkills: vi.fn(),
  startMission: vi.fn(),
  deleteRequirement: vi.fn(),
  listMissionApprovals: vi.fn(),
}));

const MISSION_ID = 'a4000000-0000-0000-0000-000000000001';

const LEAD: CurrentUserResponse = {
  id: 'a1000000-0000-0000-0000-000000000002',
  fullName: 'Marcus Reyes',
  email: 'marcus.reyes@orbitaldynamics.example',
  role: 'MISSION_LEAD',
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

// A distinct id matters: sharing the lead's would make the ownership checks pass by accident
// and the director cases would prove nothing.
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
    requirements: [],
    ...overrides,
  };
}

function renderDetail(user: CurrentUserResponse, data: MissionResponse = mission()) {
  vi.mocked(getMission).mockResolvedValue({ data } as never);
  // The page now carries an approval history, which fetches on mount. Left unmocked it would try
  // the network and fail the query, and every case here would render an error instead of a mission.
  vi.mocked(listMissionApprovals).mockResolvedValue({ data: [] } as never);
  vi.mocked(listSkills).mockResolvedValue({
    data: { content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 },
  } as never);

  const value: AuthContextValue = {
    status: 'authenticated',
    user,
    login: vi.fn(),
    logout: vi.fn(),
  };
  return render(<Harness user={value} />);
}

/** The provider stack every case here needs. The edit dialog holds date pickers. */
function Harness({ user }: { user: AuthContextValue }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <ThemeProvider theme={theme}>
      <LocalizationProvider dateAdapter={AdapterDayjs}>
        <QueryClientProvider client={queryClient}>
          <AuthContext.Provider value={user}>
            <MemoryRouter initialEntries={[`/missions/${MISSION_ID}`]}>
              <Routes>
                <Route path="/missions/:missionId" element={<MissionDetailPage />} />
              </Routes>
            </MemoryRouter>
          </AuthContext.Provider>
        </QueryClientProvider>
      </LocalizationProvider>
    </ThemeProvider>
  );
}

describe('MissionDetailPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows the mission, its status and its lead', async () => {
    renderDetail(LEAD);

    expect(await screen.findByRole('heading', { name: 'Aurora Survey' })).toBeInTheDocument();
    expect(screen.getByText('Plan')).toBeInTheDocument();
    expect(screen.getByText('Marcus Reyes')).toBeInTheDocument();
  });

  it('says the times are local, because a shifted timeline would be a serious bug', async () => {
    renderDetail(LEAD);

    expect(await screen.findByText('Times are shown in your local timezone.')).toBeInTheDocument();
  });

  it('lets the owning lead add requirements while the mission is in PLAN', async () => {
    renderDetail(LEAD);

    expect(await screen.findByRole('button', { name: 'Add requirement' })).toBeInTheDocument();
  });

  it('does not let a director add requirements - that is planning work', async () => {
    renderDetail(DIRECTOR);

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    expect(screen.queryByRole('button', { name: 'Add requirement' })).not.toBeInTheDocument();
    // A director may still edit the mission itself.
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
  });

  it('stops offering requirement changes once the mission leaves PLAN', async () => {
    renderDetail(LEAD, mission({ status: 'APPROVED' }));

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    expect(screen.queryByRole('button', { name: 'Add requirement' })).not.toBeInTheDocument();
  });

  it('shows Start on an approved mission, disabled until it is crewed', async () => {
    renderDetail(LEAD, mission({ status: 'APPROVED', fullyStaffed: false }));

    // Disabled rather than hidden: 'why can I not start this' is the question this page answers.
    expect(await screen.findByRole('button', { name: 'Start mission' })).toBeDisabled();
  });

  it('enables Start once every requirement is filled', async () => {
    renderDetail(LEAD, mission({ status: 'APPROVED', fullyStaffed: true }));

    expect(await screen.findByRole('button', { name: 'Start mission' })).toBeEnabled();
  });

  it('offers no Start button on a mission that is not approved', async () => {
    renderDetail(LEAD, mission({ status: 'PLAN' }));

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    expect(screen.queryByRole('button', { name: 'Start mission' })).not.toBeInTheDocument();
  });

  it('offers nothing to change on a closed mission, and shows the outcome', async () => {
    renderDetail(
      LEAD,
      mission({ status: 'CLOSED', closeReason: 'ABORTED', closeComment: 'Funding withdrawn.' }),
    );

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Close mission' })).not.toBeInTheDocument();
    expect(screen.getByText('Funding withdrawn.')).toBeInTheDocument();
  });

  it('renders a requirement with its skills and staffing', async () => {
    renderDetail(
      LEAD,
      mission({
        requirements: [
          {
            id: 'a5000000-0000-0000-0000-000000000001',
            title: 'Flight Engineer',
            description: 'Repairs.',
            requiredCount: 2,
            acceptedCount: 0,
            skills: [
              {
                skillId: 'a2000000-0000-0000-0000-000000000001',
                skillName: 'EVA Operations',
                minimumProficiency: 3,
                mandatory: true,
                weight: 2,
              },
            ],
          },
        ],
      }),
    );

    expect(await screen.findByText('Flight Engineer')).toBeInTheDocument();
    expect(screen.getByText('0 of 2 accepted')).toBeInTheDocument();
    expect(screen.getByText('EVA Operations')).toBeInTheDocument();
    expect(screen.getByText('Mandatory')).toBeInTheDocument();
  });

  it('explains a mission it cannot load rather than showing an empty page', async () => {
    vi.mocked(getMission).mockRejectedValue({
      type: 'urn:mission-control:not-found',
    } as never);

    const value: AuthContextValue = {
      status: 'authenticated',
      user: LEAD,
      login: vi.fn(),
      logout: vi.fn(),
    };
    render(<Harness user={value} />);

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'This mission does not exist, or you do not have access to it.',
      ),
    );
  });
});
