import type {
  CurrentUserResponse,
  MissionApprovalResponse,
  MissionResponse,
  MissionSummaryResponse,
} from '../api/generated/types.gen';

/**
 * How mission vocabulary is spelled and grouped on screen.
 *
 * One module so the board, the cards and the detail page cannot drift apart. In particular the
 * status-to-section mapping lives here and nowhere else: a status that belongs to no section would
 * silently vanish from the board, which is the kind of bug that only shows up when somebody asks
 * where their mission went.
 */

export type MissionStatus = MissionResponse['status'];
export type MissionCloseReason = NonNullable<MissionResponse['closeReason']>;
export type ApprovalDecision = MissionApprovalResponse['decision'];

/** MUI palette keys, so a chip picks up the theme rather than a hard-coded colour. */
type ChipColour = 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning';

export const STATUS_LABELS: Record<MissionStatus, string> = {
  PLAN: 'Plan',
  PENDING_APPROVAL: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  ACTIVE: 'Active',
  CLOSED: 'Closed',
};

export const CLOSE_REASON_LABELS: Record<MissionCloseReason, string> = {
  COMPLETED: 'Completed',
  ABORTED: 'Aborted',
  REJECTED: 'Rejected',
};

export const APPROVAL_DECISION_LABELS: Record<ApprovalDecision, string> = {
  PENDING: 'Awaiting a decision',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

/**
 * CANCELLED is `default`, not `error`. Nobody rejected the plan - the mission was closed while the
 * cycle was still open - and colouring it like a rejection would say something untrue about why.
 */
const APPROVAL_DECISION_COLOURS: Record<ApprovalDecision, ChipColour> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  CANCELLED: 'default',
};

export function decisionLabel(decision: ApprovalDecision): string {
  return APPROVAL_DECISION_LABELS[decision];
}

export function decisionColour(decision: ApprovalDecision): ChipColour {
  return APPROVAL_DECISION_COLOURS[decision];
}

const STATUS_COLOURS: Record<MissionStatus, ChipColour> = {
  PLAN: 'default',
  PENDING_APPROVAL: 'warning',
  APPROVED: 'info',
  REJECTED: 'error',
  ACTIVE: 'success',
  CLOSED: 'default',
};

/**
 * The three groups the mission board is divided into.
 *
 * Draft is everything still being decided; Active is what is flying now; Completed is history.
 * Rejected sits in Draft rather than in a fourth group because a rejected mission is not finished
 * - it goes back to planning or it gets closed, and either way it still needs someone's attention.
 */
export interface MissionSection {
  readonly key: 'awaiting' | 'draft' | 'active' | 'completed';
  readonly title: string;
  readonly statuses: readonly MissionStatus[];
  readonly emptyMessage: string;
}

export const MISSION_SECTIONS: readonly MissionSection[] = [
  {
    key: 'draft',
    title: 'Draft',
    statuses: ['PLAN', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'],
    emptyMessage: 'Nothing in planning.',
  },
  {
    key: 'active',
    title: 'Active',
    statuses: ['ACTIVE'],
    emptyMessage: 'No missions are running.',
  },
  {
    key: 'completed',
    title: 'Completed',
    statuses: ['CLOSED'],
    emptyMessage: 'Nothing has been closed yet.',
  },
];

/**
 * The board a director sees, with the decisions waiting on them lifted out of Draft.
 *
 * Feature 05 gives directors something to do on this screen rather than merely something to look
 * at, and FR-8 says they must be able to find it. Burying two missions awaiting a decision among
 * everything else still in planning is how a queue goes unnoticed.
 *
 * PENDING_APPROVAL is *moved*, not duplicated: a mission appearing in two sections would be
 * counted twice and paged independently in each, which is worse than being in the wrong one.
 */
const AWAITING_APPROVAL_SECTION: MissionSection = {
  key: 'awaiting',
  title: 'Awaiting approval',
  statuses: ['PENDING_APPROVAL'],
  emptyMessage: 'Nothing is waiting on your decision.',
};

const DIRECTOR_SECTIONS: readonly MissionSection[] = [
  AWAITING_APPROVAL_SECTION,
  { ...MISSION_SECTIONS[0]!, statuses: ['PLAN', 'APPROVED', 'REJECTED'] },
  ...MISSION_SECTIONS.slice(1),
];

/**
 * Which grouping applies to this user.
 *
 * A lead and a crew member keep the original three sections. A lead does not need the split - every
 * mission in their Draft section is theirs and they already know which they have submitted - and a
 * crew member sees neither.
 */
export function sectionsForRole(role: CurrentUserResponse['role'] | undefined): readonly MissionSection[] {
  return role === 'DIRECTOR' ? DIRECTOR_SECTIONS : MISSION_SECTIONS;
}

/**
 * Every status belongs to exactly one section; this is the reverse lookup.
 *
 * Takes the list to search, because which section owns PENDING_APPROVAL now depends on who is
 * looking. Resolving against a different list from the one being rendered is how the status filter
 * would end up labelling a section that is not on screen.
 */
export function sectionFor(
  status: MissionStatus,
  sections: readonly MissionSection[] = MISSION_SECTIONS,
): MissionSection {
  const section = sections.find((candidate) => candidate.statuses.includes(status));
  if (!section) {
    throw new Error(`No mission section covers status ${status}`);
  }
  return section;
}

/**
 * What a chip should say.
 *
 * A closed mission shows why it closed rather than the word 'Closed'. Completed and Aborted are
 * the distinction that matters once a mission is over, and 'Closed' hides it.
 */
export function statusLabel(mission: Pick<MissionSummaryResponse, 'status' | 'closeReason'>) {
  if (mission.status === 'CLOSED' && mission.closeReason) {
    return CLOSE_REASON_LABELS[mission.closeReason];
  }
  return STATUS_LABELS[mission.status];
}

export function statusColour(status: MissionStatus): ChipColour {
  return STATUS_COLOURS[status];
}

/** `MISSION_LEAD` reads badly in a UI; the wire format is not the display format. */
export function formatRole(role: string): string {
  return role
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

export function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0]!.charAt(0);
  const last = parts.length > 1 ? parts[parts.length - 1]!.charAt(0) : '';
  return (first + last).toUpperCase();
}
