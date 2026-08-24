import type { CurrentUserResponse, MissionResponse } from '../api/generated/types.gen';

/**
 * What the signed-in user may do, as the UI understands it.
 *
 * None of this is security. The backend decides, and it will refuse anything these predicates get
 * wrong; the point here is not to offer a button that is going to fail. Keeping the rules in one
 * module rather than inline in each screen means the mission page and the requirement cards cannot
 * disagree about who owns what.
 *
 * The rules mirror invariants M2, M6 and BR-10 in `docs/data-model.md`, and feature 05's
 * BR-2, BR-3 and BR-7.
 */

/** M2: directors do not own missions, so only a lead can start one. */
export function canCreateMission(user: CurrentUserResponse | null): boolean {
  return user?.role === 'MISSION_LEAD';
}

/**
 * M6: the owning lead, or any director in the same organisation.
 *
 * A crew member assigned to the mission can see it but never change it.
 */
export function canModifyMission(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead' | 'status'>,
): boolean {
  if (!user) return false;
  if (mission.status === 'CLOSED') return false;
  return user.role === 'DIRECTOR' || mission.missionLead.id === user.id;
}

/**
 * BR-10: only the owning lead, and only while the mission is still in PLAN.
 *
 * Narrower than editing the mission itself on purpose - changing the crew a mission needs after it
 * has been approved would invalidate the approval, and directors do not do the planning.
 */
export function canManageRequirements(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead' | 'status'>,
): boolean {
  return !!user && mission.status === 'PLAN' && mission.missionLead.id === user.id;
}

/**
 * Feature 06: the owning lead, or any director in the same organisation.
 *
 * Deliberately says nothing about status. Matching works in every state, and running it while a
 * mission is still in PLAN is the point - it is how a lead finds out whether the plan is staffable
 * before submitting it.
 *
 * Wider than `canManageRequirements`, which is PLAN-only and owner-only, and narrower than
 * visibility: a crew member assigned to the mission can read it but cannot suggest crew for it.
 */
export function canMatchCrew(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead'>,
): boolean {
  if (!user) return false;
  return user.role === 'DIRECTOR' || mission.missionLead.id === user.id;
}

/** Only an APPROVED mission can be started, and only by someone who may modify it. */
export function canStartMission(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead' | 'status'>,
): boolean {
  return mission.status === 'APPROVED' && canModifyMission(user, mission);
}

/** Closing is reachable from every non-terminal status - that is also how a mission is aborted. */
export function canCloseMission(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead' | 'status'>,
): boolean {
  return canModifyMission(user, mission);
}

/**
 * BR-2: only the lead who owns a mission may put it up for approval, and only from PLAN.
 *
 * Deliberately says nothing about crew requirements, even though the server refuses a mission with
 * none (M12). That case is shown as a disabled button with a reason rather than a missing one -
 * the same treatment `canStartMission` gets for an uncrewed mission, and for the same reason: the
 * question the screen has to answer is "why can I not submit this?".
 */
export function canSubmitForApproval(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead' | 'status'>,
): boolean {
  return !!user && mission.status === 'PLAN' && mission.missionLead.id === user.id;
}

/**
 * BR-3: a director, and only on a mission that is actually waiting for one.
 *
 * No ownership test is needed. M2 stops a director owning a mission at all, so a director can never
 * be deciding their own work - which is BR-8, holding by construction rather than by a check.
 */
export function canDecideMission(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'status'>,
): boolean {
  return user?.role === 'DIRECTOR' && mission.status === 'PENDING_APPROVAL';
}

/**
 * BR-7: the owning lead alone may take a rejected plan back to planning.
 *
 * Narrower than `canModifyMission`, which would include directors, and narrower than invariant M6
 * allows - the API table for feature 05 says owner-only. Having another go at a plan is planning
 * work; a director's way out of a rejected mission is to close it.
 */
export function canReplanMission(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead' | 'status'>,
): boolean {
  return !!user && mission.status === 'REJECTED' && mission.missionLead.id === user.id;
}
