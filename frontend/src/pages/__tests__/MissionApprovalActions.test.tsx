import { ThemeProvider } from '@mui/material/styles';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  approveMission,
  getMission,
  listMissionApprovals,
  listSkills,
  rejectMission,
  replanMission,
  submitMission,
} from '../../api/generated/sdk.gen';
import type {
  CrewRequirementResponse,
  CurrentUserResponse,
  MissionApprovalResponse,
  MissionResponse,
} from '../../api/generated/types.gen';
import { AuthContext, type AuthContextValue } from '../../auth/AuthContext';
import { theme } from '../../theme';
import MissionDetailPage from '../MissionDetailPage';

// Mock `sdk.gen`, not the barrel: the generated react-query helpers import straight from it, so a
// mock on the barrel is simply never consulted.
vi.mock('../../api/generated/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/generated/sdk.gen')>()),
  getMission: vi.fn(),
  listSkills: vi.fn(),
  listMissionApprovals: vi.fn(),
  submitMission: vi.fn(),
  approveMission: vi.fn(),
  rejectMission: vi.fn(),
  replanMission: vi.fn(),
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

// A distinct id matters: sharing the lead's would make the ownership checks pass by accident.
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

/** One staffing line, so a mission satisfies M12 and can be submitted. */
function requirement(): CrewRequirementResponse {
  return {
    id: 'a5000000-0000-0000-0000-000000000001',
    title: 'Flight Engineer',
    requiredCount: 1,
    acceptedCount: 0,
    skills: [],
  };
}

function cycle(overrides: Partial<MissionApprovalResponse> = {}): MissionApprovalResponse {
  return {
    id: 'a6000000-0000-0000-0000-000000000001',
    decision: 'PENDING',
    submittedBy: { id: LEAD.id, fullName: LEAD.fullName },
    submittedAt: '2026-02-01T09:00:00Z',
    ...overrides,
  };
}

function renderDetail(
  user: CurrentUserResponse,
  data: MissionResponse = mission(),
  approvals: MissionApprovalResponse[] = [],
) {
  vi.mocked(getMission).mockResolvedValue({ data } as never);
  vi.mocked(listMissionApprovals).mockResolvedValue({ data: approvals } as never);
  vi.mocked(listSkills).mockResolvedValue({
    data: { content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 },
  } as never);

  const value: AuthContextValue = {
    status: 'authenticated',
    user,
    login: vi.fn(),
    logout: vi.fn(),
  };

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ThemeProvider theme={theme}>
      <LocalizationProvider dateAdapter={AdapterDayjs}>
        <QueryClientProvider client={queryClient}>
          <AuthContext.Provider value={value}>
            <MemoryRouter initialEntries={[`/missions/${MISSION_ID}`]}>
              <Routes>
                <Route path="/missions/:missionId" element={<MissionDetailPage />} />
              </Routes>
            </MemoryRouter>
          </AuthContext.Provider>
        </QueryClientProvider>
      </LocalizationProvider>
    </ThemeProvider>,
  );
}

/** The dialog's submit button, as opposed to the action-bar button that opened it. */
function dialogSubmit(name: string) {
  const button = screen
    .getAllByRole('button', { name })
    .find((candidate) => candidate.getAttribute('type') === 'submit');
  if (!button) throw new Error(`No submit button named ${name}`);
  return button;
}

/**
 * Feature 05, from the screen's side.
 *
 * The through-line of every case here is that the two halves of the approval gate never appear
 * together, because no user can do both: a lead proposes, a director decides. That is the only
 * place in the product where the roles genuinely divide, so it is the part most worth pinning down.
 */
describe('Submitting a plan for approval', () => {
  beforeEach(() => vi.clearAllMocks());

  it('is offered to the owning lead on a staffed plan', async () => {
    renderDetail(LEAD, mission({ requirements: [requirement()] }));

    expect(await screen.findByRole('button', { name: 'Submit for approval' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
  });

  it('is disabled rather than absent when there is nothing to staff', async () => {
    // M12. An absent button does not answer "why can I not submit this?", which is the question
    // this screen exists to answer - the same treatment Start already gets on an uncrewed mission.
    renderDetail(LEAD, mission({ requirements: [] }));

    expect(await screen.findByRole('button', { name: 'Submit for approval' })).toBeDisabled();
    expect(submitMission).not.toHaveBeenCalled();
  });

  it('sends the mission id and nothing else', async () => {
    vi.mocked(submitMission).mockResolvedValue({
      data: mission({ status: 'PENDING_APPROVAL' }),
    } as never);
    renderDetail(LEAD, mission({ requirements: [requirement()] }));

    fireEvent.click(await screen.findByRole('button', { name: 'Submit for approval' }));

    await waitFor(() => expect(submitMission).toHaveBeenCalledTimes(1));
    expect(vi.mocked(submitMission).mock.calls[0]?.[0]?.path).toEqual({ id: MISSION_ID });
  });

  it('is not offered to a director - BR-2, and the server would refuse it', async () => {
    renderDetail(DIRECTOR, mission({ requirements: [requirement()] }));

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    expect(screen.queryByRole('button', { name: 'Submit for approval' })).not.toBeInTheDocument();
  });

  it('reports a conflict from a stale view rather than failing quietly', async () => {
    vi.mocked(submitMission).mockRejectedValue({
      type: 'urn:mission-control:invalid-transition',
      detail: 'A mission in PENDING_APPROVAL cannot move to PENDING_APPROVAL.',
      currentStatus: 'PENDING_APPROVAL',
    } as never);
    renderDetail(LEAD, mission({ requirements: [requirement()] }));

    fireEvent.click(await screen.findByRole('button', { name: 'Submit for approval' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A mission in PENDING_APPROVAL cannot move to PENDING_APPROVAL.',
    );
  });

  it('explains a missing requirement if the server refuses anyway', async () => {
    vi.mocked(submitMission).mockRejectedValue({
      type: 'urn:mission-control:mission-has-no-requirements',
    } as never);
    renderDetail(LEAD, mission({ requirements: [requirement()] }));

    fireEvent.click(await screen.findByRole('button', { name: 'Submit for approval' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Add at least one crew requirement before submitting this mission for approval.',
    );
  });
});

describe('Deciding a mission', () => {
  beforeEach(() => vi.clearAllMocks());

  it('offers a director Approve and Reject on a mission awaiting one', async () => {
    renderDetail(DIRECTOR, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    expect(await screen.findByRole('button', { name: 'Approve' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument();
  });

  it('offers the lead neither, even on their own mission - BR-3', async () => {
    renderDetail(LEAD, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument();
  });

  it('will not send a rejection with no reason - BR-6', async () => {
    renderDetail(DIRECTOR, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Reject' }));

    // Disabled rather than fired-and-rejected: the requirement is visible before the request,
    // instead of arriving as a 400 the caller has to interpret.
    expect(dialogSubmit('Reject')).toBeDisabled();
    expect(rejectMission).not.toHaveBeenCalled();
  });

  it('sends the reason once one is typed', async () => {
    vi.mocked(rejectMission).mockResolvedValue({
      data: mission({ status: 'REJECTED' }),
    } as never);
    renderDetail(DIRECTOR, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Reject' }));
    fireEvent.change(screen.getByLabelText(/Reason/), {
      target: { value: '  The window clashes with the Vesta flyby.  ' },
    });
    fireEvent.click(dialogSubmit('Reject'));

    await waitFor(() => expect(rejectMission).toHaveBeenCalledTimes(1));
    // Trimmed, because whitespace satisfies the letter of "a reason was given" and none of its
    // purpose. The server takes the same view.
    expect(vi.mocked(rejectMission).mock.calls[0]?.[0]?.body).toEqual({
      comment: 'The window clashes with the Vesta flyby.',
    });
  });

  it('treats whitespace as no reason at all', async () => {
    renderDetail(DIRECTOR, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Reject' }));
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: '   ' } });

    expect(dialogSubmit('Reject')).toBeDisabled();
  });

  it('approves with the note omitted rather than blank', async () => {
    vi.mocked(approveMission).mockResolvedValue({
      data: mission({ status: 'APPROVED' }),
    } as never);
    renderDetail(DIRECTOR, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));
    fireEvent.click(dialogSubmit('Approve'));

    await waitFor(() => expect(approveMission).toHaveBeenCalledTimes(1));
    expect(vi.mocked(approveMission).mock.calls[0]?.[0]?.body).toEqual({ comment: undefined });
  });

  it('carries an approval note when one is given', async () => {
    vi.mocked(approveMission).mockResolvedValue({
      data: mission({ status: 'APPROVED' }),
    } as never);
    renderDetail(DIRECTOR, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));
    fireEvent.change(screen.getByLabelText(/Note/), { target: { value: 'Cleared.' } });
    fireEvent.click(dialogSubmit('Approve'));

    await waitFor(() =>
      expect(vi.mocked(approveMission).mock.calls[0]?.[0]?.body).toEqual({ comment: 'Cleared.' }),
    );
  });

  it('shows a lost race as a conflict the caller can act on', async () => {
    vi.mocked(approveMission).mockRejectedValue({
      type: 'urn:mission-control:invalid-transition',
      detail: 'A mission in APPROVED cannot move to APPROVED.',
      currentStatus: 'APPROVED',
    } as never);
    renderDetail(DIRECTOR, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));
    fireEvent.click(dialogSubmit('Approve'));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A mission in APPROVED cannot move to APPROVED.',
    );
  });
});

describe('Returning a rejected plan to planning', () => {
  beforeEach(() => vi.clearAllMocks());

  it('is offered to the owning lead', async () => {
    vi.mocked(replanMission).mockResolvedValue({ data: mission({ status: 'PLAN' }) } as never);
    renderDetail(LEAD, mission({ status: 'REJECTED' }), [
      cycle({ decision: 'REJECTED', comment: 'Too tight.' }),
    ]);

    fireEvent.click(await screen.findByRole('button', { name: 'Return to plan' }));

    await waitFor(() => expect(replanMission).toHaveBeenCalledTimes(1));
  });

  it('is not offered to a director - their way out is to close it', async () => {
    renderDetail(DIRECTOR, mission({ status: 'REJECTED' }), [
      cycle({ decision: 'REJECTED', comment: 'No.' }),
    ]);

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    expect(screen.queryByRole('button', { name: 'Return to plan' })).not.toBeInTheDocument();
  });

  it('is not offered on an approved mission, though APPROVED to PLAN is a legal move', async () => {
    // That arrow belongs to editing - M5 - and the server refuses this endpoint from APPROVED.
    renderDetail(LEAD, mission({ status: 'APPROVED' }));

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    expect(screen.queryByRole('button', { name: 'Return to plan' })).not.toBeInTheDocument();
  });
});

describe('Approval history', () => {
  beforeEach(() => vi.clearAllMocks());

  it('says so when a mission has never been submitted', async () => {
    renderDetail(LEAD, mission(), []);

    expect(
      await screen.findByText('This mission has not been submitted for approval yet.'),
    ).toBeInTheDocument();
  });

  it('opens itself on a rejection, because that reason is what the lead must act on', async () => {
    renderDetail(LEAD, mission({ status: 'REJECTED' }), [
      cycle({
        decision: 'REJECTED',
        comment: 'The window clashes with the Vesta flyby.',
        decidedBy: { id: DIRECTOR.id, fullName: DIRECTOR.fullName },
        decidedAt: '2026-02-02T14:30:00Z',
      }),
    ]);

    // Asserted through aria-expanded rather than visibility. MUI keeps a collapsed accordion's
    // children mounted, so `toBeInTheDocument` would pass either way and prove nothing - and jsdom
    // runs no transition, so `toBeVisible` is not reliable either.
    //
    // Waited for rather than read once: the accordion renders before its query settles, and at that
    // first render there is no rejection to open it. That gap is exactly the bug `defaultExpanded`
    // had, so the test has to be able to see it.
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /Approval history/ })).toHaveAttribute(
        'aria-expanded',
        'true',
      ),
    );
    expect(screen.getByText('The window clashes with the Vesta flyby.')).toBeInTheDocument();
  });

  it('stays shut when the newest cycle is not a rejection', async () => {
    // Most visits to this page are not about the history, so it stays out of the way by default.
    renderDetail(LEAD, mission({ status: 'APPROVED' }), [
      cycle({
        decision: 'APPROVED',
        decidedBy: { id: DIRECTOR.id, fullName: DIRECTOR.fullName },
        decidedAt: '2026-02-02T14:30:00Z',
      }),
    ]);

    // Awaited on the count first, so the assertion runs after the data landed rather than during
    // the pending render, where everything is shut regardless.
    await screen.findByText('· 1 cycle');
    expect(screen.getByRole('button', { name: /Approval history/ })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
  });

  it('counts the cycles it holds', async () => {
    renderDetail(LEAD, mission({ status: 'REJECTED' }), [
      cycle({ id: 'newest', decision: 'REJECTED', comment: 'Second look, still no.' }),
      cycle({ id: 'oldest', decision: 'REJECTED', comment: 'First rejection.' }),
    ]);

    expect(await screen.findByText('· 2 cycles')).toBeInTheDocument();
  });

  it('names who submitted a cycle and who decided it', async () => {
    renderDetail(LEAD, mission({ status: 'REJECTED' }), [
      cycle({
        decision: 'REJECTED',
        comment: 'Too tight.',
        decidedBy: { id: DIRECTOR.id, fullName: DIRECTOR.fullName },
        decidedAt: '2026-02-02T14:30:00Z',
      }),
    ]);

    expect(await screen.findByText(/Submitted by Marcus Reyes/)).toBeInTheDocument();
    expect(screen.getByText(/Vera Lindholm/)).toBeInTheDocument();
  });

  it('reads a pending cycle as awaiting a decision', async () => {
    renderDetail(DIRECTOR, mission({ status: 'PENDING_APPROVAL' }), [cycle()]);

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    fireEvent.click(screen.getByText('Approval history'));

    expect(await screen.findByText('Awaiting a decision')).toBeInTheDocument();
  });

  it('does not claim a closed mission is still waiting on anyone', async () => {
    // The server cancels an open cycle when the mission closes, so this only covers a row written
    // before that behaviour existed - but 'awaiting a decision' on a closed mission would be a lie.
    renderDetail(LEAD, mission({ status: 'CLOSED', closeReason: 'ABORTED' }), [cycle()]);

    await screen.findByRole('heading', { name: 'Aurora Survey' });
    fireEvent.click(screen.getByText('Approval history'));

    expect(await screen.findByText('Not decided')).toBeInTheDocument();
    expect(screen.queryByText('Awaiting a decision')).not.toBeInTheDocument();
  });
});
