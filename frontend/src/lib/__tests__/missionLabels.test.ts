import { describe, expect, it } from 'vitest';
import {
  APPROVAL_DECISION_LABELS,
  MISSION_SECTIONS,
  STATUS_LABELS,
  decisionColour,
  decisionLabel,
  formatRole,
  initials,
  sectionFor,
  sectionsForRole,
  statusColour,
  statusLabel,
  type MissionStatus,
} from '../missionLabels';

const ALL_STATUSES = Object.keys(STATUS_LABELS) as MissionStatus[];

describe('mission sections', () => {
  // The guard that matters, and it has to hold for both groupings now that a director's board
  // differs from everyone else's. A status belonging to no section would vanish from the board
  // silently, and one belonging to two would show the same mission twice - counted twice, and paged
  // independently in each.
  it.each([
    ['everyone', sectionsForRole('MISSION_LEAD')],
    ['a director', sectionsForRole('DIRECTOR')],
    ['a signed-out user', sectionsForRole(undefined)],
  ])('covers every status exactly once for %s', (_who, sections) => {
    const covered = sections.flatMap((section) => section.statuses);

    expect([...covered].sort()).toEqual([...ALL_STATUSES].sort());
    expect(new Set(covered).size).toBe(covered.length);
  });

  it('lifts the decisions waiting on a director out of Draft', () => {
    const sections = sectionsForRole('DIRECTOR');

    expect(sections.map((section) => section.key)).toEqual([
      'awaiting',
      'draft',
      'active',
      'completed',
    ]);
    expect(sectionFor('PENDING_APPROVAL', sections).key).toBe('awaiting');
    // Moved, not copied.
    expect(sections[1]!.statuses).not.toContain('PENDING_APPROVAL');
  });

  it('leaves a lead and a crew member the original three sections', () => {
    expect(sectionsForRole('MISSION_LEAD')).toBe(MISSION_SECTIONS);
    expect(sectionsForRole('CREW_MEMBER')).toBe(MISSION_SECTIONS);
  });

  it('puts rejected missions in Draft, because they still need a decision', () => {
    expect(sectionFor('REJECTED').key).toBe('draft');
    expect(sectionFor('PLAN').key).toBe('draft');
    expect(sectionFor('PENDING_APPROVAL').key).toBe('draft');
    expect(sectionFor('APPROVED').key).toBe('draft');
  });

  it('separates what is flying from what is finished', () => {
    expect(sectionFor('ACTIVE').key).toBe('active');
    expect(sectionFor('CLOSED').key).toBe('completed');
  });

  it('gives every section something to say when it is empty', () => {
    [...MISSION_SECTIONS, ...sectionsForRole('DIRECTOR')].forEach((section) => {
      expect(section.emptyMessage).not.toHaveLength(0);
    });
  });
});

describe('statusLabel', () => {
  it('shows why a mission closed rather than the word Closed', () => {
    // Completed and Aborted are the distinction that matters once a mission is over.
    expect(statusLabel({ status: 'CLOSED', closeReason: 'COMPLETED' })).toBe('Completed');
    expect(statusLabel({ status: 'CLOSED', closeReason: 'ABORTED' })).toBe('Aborted');
  });

  it('falls back to Closed when no reason came back', () => {
    expect(statusLabel({ status: 'CLOSED' })).toBe('Closed');
  });

  it('spells out the other statuses in prose', () => {
    expect(statusLabel({ status: 'PENDING_APPROVAL' })).toBe('Pending approval');
    expect(statusLabel({ status: 'PLAN' })).toBe('Plan');
  });
});

describe('statusColour', () => {
  it('gives every status a colour', () => {
    ALL_STATUSES.forEach((status) => {
      expect(statusColour(status)).toBeTruthy();
    });
  });

  it('marks a running mission as good and a rejected one as bad', () => {
    expect(statusColour('ACTIVE')).toBe('success');
    expect(statusColour('REJECTED')).toBe('error');
  });
});

describe('display helpers', () => {
  it('turns a wire role into something readable', () => {
    expect(formatRole('MISSION_LEAD')).toBe('Mission Lead');
    expect(formatRole('DIRECTOR')).toBe('Director');
  });

  it('builds initials from the first and last name', () => {
    expect(initials('Marcus Reyes')).toBe('MR');
    expect(initials('Vera')).toBe('V');
    expect(initials('  ')).toBe('?');
  });
});

describe('approval decisions', () => {
  it('names every decision the API can return', () => {
    expect(Object.keys(APPROVAL_DECISION_LABELS).sort()).toEqual([
      'APPROVED',
      'CANCELLED',
      'PENDING',
      'REJECTED',
    ]);
  });

  it('reads a pending cycle as waiting rather than as a decision', () => {
    expect(decisionLabel('PENDING')).toBe('Awaiting a decision');
  });

  it('does not colour a cancelled cycle like a rejection', () => {
    // Nobody rejected the plan - the mission was closed while the cycle was still open - so
    // colouring it red would say something untrue about why it ended.
    expect(decisionColour('REJECTED')).toBe('error');
    expect(decisionColour('CANCELLED')).toBe('default');
    expect(decisionColour('APPROVED')).toBe('success');
  });
});
