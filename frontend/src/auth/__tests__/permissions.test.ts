import { describe, expect, it } from 'vitest';
import type { CurrentUserResponse } from '../../api/generated/types.gen';
import {
  canCloseMission,
  canCreateMission,
  canDecideMission,
  canManageRequirements,
  canMatchCrew,
  canModifyMission,
  canOfferCrew,
  canReplanMission,
  canRespondToOffer,
  canStartMission,
  canSubmitForApproval,
  canWithdrawAssignment,
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

function mission(
  status: 'PLAN' | 'PENDING_APPROVAL' | 'APPROVED' | 'ACTIVE' | 'CLOSED' | 'REJECTED',
) {
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

/**
 * Feature 05. These three are where the roles genuinely divide: a lead proposes and a director
 * decides, and no user is ever offered both halves.
 */
describe('canSubmitForApproval', () => {
  it('is the owning lead only, and only from PLAN', () => {
    expect(canSubmitForApproval(LEAD, mission('PLAN'))).toBe(true);
    expect(canSubmitForApproval(OTHER_LEAD, mission('PLAN'))).toBe(false);
    // A director can see the mission but not submit it - BR-2, and M2 means they never own one.
    expect(canSubmitForApproval(DIRECTOR, mission('PLAN'))).toBe(false);
    expect(canSubmitForApproval(CREW, mission('PLAN'))).toBe(false);
    expect(canSubmitForApproval(null, mission('PLAN'))).toBe(false);
  });

  it('is not offered once the mission has left PLAN', () => {
    (['PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'ACTIVE', 'CLOSED'] as const).forEach(
      (status) => {
        expect(canSubmitForApproval(LEAD, mission(status))).toBe(false);
      },
    );
  });

  it('says nothing about crew requirements', () => {
    // M12 is the server's to enforce. The screen shows the button disabled with a reason instead,
    // because an absent button does not answer "why can I not submit this?".
    expect(canSubmitForApproval(LEAD, mission('PLAN'))).toBe(true);
  });
});

describe('canDecideMission', () => {
  it('is any director, and only while the mission is awaiting a decision', () => {
    expect(canDecideMission(DIRECTOR, mission('PENDING_APPROVAL'))).toBe(true);
    // No ownership test is needed: M2 stops a director owning a mission, so BR-8 holds by
    // construction rather than by a check here.
    expect(canDecideMission(LEAD, mission('PENDING_APPROVAL'))).toBe(false);
    expect(canDecideMission(CREW, mission('PENDING_APPROVAL'))).toBe(false);
    expect(canDecideMission(null, mission('PENDING_APPROVAL'))).toBe(false);
  });

  it('is not offered from any other status', () => {
    (['PLAN', 'APPROVED', 'REJECTED', 'ACTIVE', 'CLOSED'] as const).forEach((status) => {
      expect(canDecideMission(DIRECTOR, mission(status))).toBe(false);
    });
  });
});

describe('canReplanMission', () => {
  it('is the owning lead only, and only from REJECTED', () => {
    expect(canReplanMission(LEAD, mission('REJECTED'))).toBe(true);
    expect(canReplanMission(OTHER_LEAD, mission('REJECTED'))).toBe(false);
    // Narrower than M6 on purpose: a director's way out of a rejected mission is to close it.
    expect(canReplanMission(DIRECTOR, mission('REJECTED'))).toBe(false);
    expect(canReplanMission(null, mission('REJECTED'))).toBe(false);
  });

  it('is not offered from an approved mission, even though APPROVED to PLAN is a legal move', () => {
    // That arrow belongs to editing - M5 - not to this action. The server refuses it too.
    expect(canReplanMission(LEAD, mission('APPROVED'))).toBe(false);
    expect(canReplanMission(LEAD, mission('ACTIVE'))).toBe(false);
    expect(canReplanMission(LEAD, mission('PLAN'))).toBe(false);
    expect(canReplanMission(LEAD, mission('CLOSED'))).toBe(false);
  });
});

describe('canMatchCrew', () => {
  it('is the owning lead or any director', () => {
    expect(canMatchCrew(LEAD, mission('PLAN'))).toBe(true);
    expect(canMatchCrew(DIRECTOR, mission('PLAN'))).toBe(true);
    expect(canMatchCrew(OTHER_LEAD, mission('PLAN'))).toBe(false);
    // A crew member assigned to the mission can read it but never suggest crew for it.
    expect(canMatchCrew(CREW, mission('PLAN'))).toBe(false);
    expect(canMatchCrew(null, mission('PLAN'))).toBe(false);
  });

  it('is offered in every status, including PLAN and CLOSED', () => {
    // Sizing a plan before submitting it is the point, so unlike the other predicates this one
    // deliberately does not test the status at all.
    (['PLAN', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'ACTIVE', 'CLOSED'] as const).forEach(
      (status) => {
        expect(canMatchCrew(LEAD, mission(status))).toBe(true);
      },
    );
  });
});

describe('canOfferCrew', () => {
  it('is the owning lead alone, and only on an APPROVED mission', () => {
    expect(canOfferCrew(LEAD, mission('APPROVED'))).toBe(true);
    // A director may run a match and read the crew, and offers nobody anything - BR-9.
    expect(canOfferCrew(DIRECTOR, mission('APPROVED'))).toBe(false);
    expect(canOfferCrew(OTHER_LEAD, mission('APPROVED'))).toBe(false);
    expect(canOfferCrew(CREW, mission('APPROVED'))).toBe(false);
    expect(canOfferCrew(null, mission('APPROVED'))).toBe(false);
  });

  it('is not offered in any other status, ACTIVE included', () => {
    // Narrower than canMatchCrew on purpose. A mission already flying is not taking on crew, and a
    // seat vacated after launch is dealt with by editing the plan - which sends it back to PLAN.
    (['PLAN', 'PENDING_APPROVAL', 'REJECTED', 'ACTIVE', 'CLOSED'] as const).forEach((status) => {
      expect(canOfferCrew(LEAD, mission(status))).toBe(false);
    });
  });
});

describe('canWithdrawAssignment', () => {
  const offered = { status: 'OFFERED' } as const;
  const accepted = { status: 'ACCEPTED' } as const;

  it('is the owning lead alone - not a director, unlike editing the mission', () => {
    expect(canWithdrawAssignment(LEAD, mission('ACTIVE'), accepted)).toBe(true);
    expect(canWithdrawAssignment(DIRECTOR, mission('ACTIVE'), accepted)).toBe(false);
    expect(canWithdrawAssignment(OTHER_LEAD, mission('ACTIVE'), accepted)).toBe(false);
    expect(canWithdrawAssignment(CREW, mission('ACTIVE'), accepted)).toBe(false);
  });

  it('covers an open offer as well as an acceptance', () => {
    expect(canWithdrawAssignment(LEAD, mission('APPROVED'), offered)).toBe(true);
  });

  it('is not offered on a settled assignment, which has nothing left to withdraw', () => {
    expect(canWithdrawAssignment(LEAD, mission('ACTIVE'), { status: 'DECLINED' })).toBe(false);
    expect(canWithdrawAssignment(LEAD, mission('ACTIVE'), { status: 'WITHDRAWN' })).toBe(false);
  });

  it('is not restricted by mission status - crew can be released mid-flight', () => {
    // BR-11: doing so does not send the mission backwards, so there is no reason to hide it.
    (['APPROVED', 'ACTIVE', 'PLAN'] as const).forEach((status) => {
      expect(canWithdrawAssignment(LEAD, mission(status), accepted)).toBe(true);
    });
  });
});

describe('canRespondToOffer', () => {
  it('is the crew member, and only while the offer is open', () => {
    expect(canRespondToOffer(CREW, { status: 'OFFERED' })).toBe(true);
    expect(canRespondToOffer(LEAD, { status: 'OFFERED' })).toBe(false);
    expect(canRespondToOffer(DIRECTOR, { status: 'OFFERED' })).toBe(false);
    expect(canRespondToOffer(null, { status: 'OFFERED' })).toBe(false);
  });

  it('is gone once accepted, because being let off is the mission lead’s decision', () => {
    expect(canRespondToOffer(CREW, { status: 'ACCEPTED' })).toBe(false);
    expect(canRespondToOffer(CREW, { status: 'DECLINED' })).toBe(false);
    expect(canRespondToOffer(CREW, { status: 'WITHDRAWN' })).toBe(false);
  });
});
