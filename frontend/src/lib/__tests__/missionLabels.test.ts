import { describe, expect, it } from 'vitest';
import {
  MISSION_SECTIONS,
  STATUS_LABELS,
  formatRole,
  initials,
  sectionFor,
  statusColour,
  statusLabel,
  type MissionStatus,
} from '../missionLabels';

const ALL_STATUSES = Object.keys(STATUS_LABELS) as MissionStatus[];

describe('mission sections', () => {
  it('covers every status exactly once', () => {
    // The guard that matters. A status belonging to no section would vanish from the board
    // silently, and one belonging to two would show the same mission twice.
    const covered = MISSION_SECTIONS.flatMap((section) => section.statuses);

    expect([...covered].sort()).toEqual([...ALL_STATUSES].sort());
    expect(new Set(covered).size).toBe(covered.length);
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
    MISSION_SECTIONS.forEach((section) => {
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
