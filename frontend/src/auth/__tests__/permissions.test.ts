import { describe, expect, it } from 'vitest';
import type { CurrentUserResponse } from '../../api/generated/types.gen';
import {
  canCloseMission,
  canCreateMission,
  canManageRequirements,
  canModifyMission,
  canStartMission,
} from '../permissions';

const LEAD: CurrentUserResponse = {
  id: 'a1000000-0000-0000-0000-000000000002',
  fullName: 'Marcus Reyes',
  email: 'marcus.reyes@orbitaldynamics.example',
  role: 'MISSION_LEAD',
  organisationId: 'a0000000-0000-0000-0000-000000000001',
  organisationName: 'Orbital Dynamics',
};

const OTHER_LEAD: CurrentUserResponse = {
  ...LEAD,
  id: 'a1000000-0000-0000-0000-000000000003',
  fullName: 'Priya Raman',
};

const DIRECTOR: CurrentUserResponse = {
  ...LEAD,
  id: 'a1000000-0000-0000-0000-000000000001',
  fullName: 'Vera Lindholm',
  role: 'DIRECTOR',
};

const CREW: CurrentUserResponse = {
  ...LEAD,
  id: 'a1000000-0000-0000-0000-000000000004',
  fullName: 'Ada Kowalski',
  role: 'CREW_MEMBER',
};

function mission(status: 'PLAN' | 'APPROVED' | 'ACTIVE' | 'CLOSED' | 'REJECTED') {
  return { status, missionLead: { id: LEAD.id, fullName: LEAD.fullName } };
}

/**
 * These mirror invariants M2, M6 and BR-10. They are not security - the server decides - but a
 * button that is going to fail should not be offered, and a screen that hides an action somebody
 * is allowed to take is just as wrong.
 */
describe('canCreateMission', () => {
  it('is only for mission leads, because directors do not own missions', () => {
    expect(canCreateMission(LEAD)).toBe(true);
    expect(canCreateMission(DIRECTOR)).toBe(false);
    expect(canCreateMission(CREW)).toBe(false);
    expect(canCreateMission(null)).toBe(false);
  });
});

describe('canModifyMission', () => {
  it('allows the owning lead and any director', () => {
    expect(canModifyMission(LEAD, mission('PLAN'))).toBe(true);
    expect(canModifyMission(DIRECTOR, mission('PLAN'))).toBe(true);
  });

  it('refuses another lead and any crew member', () => {
    expect(canModifyMission(OTHER_LEAD, mission('PLAN'))).toBe(false);
    expect(canModifyMission(CREW, mission('PLAN'))).toBe(false);
  });

  it('refuses everyone once the mission is closed, because closing is terminal', () => {
    expect(canModifyMission(LEAD, mission('CLOSED'))).toBe(false);
    expect(canModifyMission(DIRECTOR, mission('CLOSED'))).toBe(false);
  });
});

describe('canManageRequirements', () => {
  it('is the owning lead only, and only while planning', () => {
    expect(canManageRequirements(LEAD, mission('PLAN'))).toBe(true);
    expect(canManageRequirements(LEAD, mission('APPROVED'))).toBe(false);
    expect(canManageRequirements(LEAD, mission('ACTIVE'))).toBe(false);
  });

  it('excludes directors, who may edit the mission but do not do the planning', () => {
    expect(canManageRequirements(DIRECTOR, mission('PLAN'))).toBe(false);
  });
});

describe('canStartMission', () => {
  it('needs an approved mission and someone who may change it', () => {
    expect(canStartMission(LEAD, mission('APPROVED'))).toBe(true);
    expect(canStartMission(DIRECTOR, mission('APPROVED'))).toBe(true);
    expect(canStartMission(LEAD, mission('PLAN'))).toBe(false);
    expect(canStartMission(LEAD, mission('ACTIVE'))).toBe(false);
    expect(canStartMission(OTHER_LEAD, mission('APPROVED'))).toBe(false);
  });
});

describe('canCloseMission', () => {
  it('is reachable from every non-terminal status - this is also how a mission is aborted', () => {
    (['PLAN', 'APPROVED', 'ACTIVE', 'REJECTED'] as const).forEach((status) => {
      expect(canCloseMission(LEAD, mission(status))).toBe(true);
    });
    expect(canCloseMission(LEAD, mission('CLOSED'))).toBe(false);
  });
});
