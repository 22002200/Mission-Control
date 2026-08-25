import type { AssignmentResponse } from '../api/generated/types.gen';

/**
 * How assignment vocabulary is spelled and coloured on screen.
 *
 * One module for the same reason `missionLabels` is one: the mission page, the matching board and
 * a crew member's own list all show these, and three copies of a colour map is three chances for
 * them to disagree. Deliberately separate from `missionLabels` rather than merged into it - an
 * assignment status and a mission status are different vocabularies that happen to be rendered the
 * same way, and folding them together would invite a lookup that takes either.
 */

export type AssignmentStatus = AssignmentResponse['status'];

/** MUI palette keys, so a chip picks up the theme rather than a hard-coded colour. */
type ChipColour = 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning';

export const ASSIGNMENT_STATUS_LABELS: Record<AssignmentStatus, string> = {
  OFFERED: 'Offered',
  ACCEPTED: 'Accepted',
  DECLINED: 'Declined',
  WITHDRAWN: 'Withdrawn',
};

/**
 * WITHDRAWN is `default`, not `error`. Nobody did anything wrong - the lead released the place, or
 * the mission closed with the offer unanswered - and colouring it like a refusal would say
 * something untrue about why. The same distinction `missionLabels` draws for a CANCELLED approval.
 *
 * OFFERED is `warning` because it is the only one that is waiting on somebody.
 */
const ASSIGNMENT_STATUS_COLOURS: Record<AssignmentStatus, ChipColour> = {
  OFFERED: 'warning',
  ACCEPTED: 'success',
  DECLINED: 'error',
  WITHDRAWN: 'default',
};

export function assignmentStatusLabel(status: AssignmentStatus): string {
  return ASSIGNMENT_STATUS_LABELS[status];
}

export function assignmentStatusColour(status: AssignmentStatus): ChipColour {
  return ASSIGNMENT_STATUS_COLOURS[status];
}

/** The `timeframe` filter, spelled for people rather than for the wire. */
export const TIMEFRAME_LABELS = {
  CURRENT: 'Running now',
  UPCOMING: 'Still to come',
  PAST: 'Finished',
} as const;

export type Timeframe = keyof typeof TIMEFRAME_LABELS;

/**
 * How a requirement's staffing reads in one line.
 *
 * Three numbers rather than two, because "1 of 2" hides whether the missing person is being waited
 * on or has not been asked yet - and those need different actions from a mission lead.
 */
export function staffingSummary(
  requiredCount: number,
  acceptedCount: number,
  offeredCount: number,
): string {
  const base = `${acceptedCount} of ${requiredCount} accepted`;
  return offeredCount > 0 ? `${base}, ${offeredCount} awaiting a reply` : base;
}
