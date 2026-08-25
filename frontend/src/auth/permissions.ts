import type {
  AssignmentResponse,
  CurrentUserResponse,
  MissionResponse,
  MyAssignmentResponse,
} from '../api/generated/types.gen';

/**
 * What the signed-in user may do, as the UI understands it.
 *
 * None of this is security. The backend decides, and it will refuse anything these predicates get
 * wrong; the point here is not to offer a button that is going to fail. Keeping the rules in one
 * module rather than inline in each screen means the mission page and the requirement cards cannot
 * disagree about who owns what.
 *
 * The rules mirror invariants M2, M6 and BR-10 in `docs/data-model.md`, feature 05's BR-2, BR-3
 * and BR-7, and feature 07's BR-6 and BR-9.
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
 * Feature 07 BR-1 and BR-9: the owning lead, and only while the mission is APPROVED.
 *
 * Narrower than `canMatchCrew` in both directions that matter. A director may run a match and read
 * the crew but may never offer anybody a place - their lever on a mission they disagree with is
 * closing it. And matching works in every status, while offering works in exactly one: a mission
 * already flying is not taking on crew, and a seat vacated after launch is dealt with by editing
 * the plan, which sends it back to PLAN under M5.
 */
export function canOfferCrew(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead' | 'status'>,
): boolean {
  return !!user && mission.status === 'APPROVED' && mission.missionLead.id === user.id;
}

/**
 * Feature 07 BR-9: withdrawing a place is the owning lead's alone.
 *
 * Not a director's, which is narrower than invariant M6 allows and matches `canReplanMission`
 * rather than `canModifyMission`. Unlike offering, this is not restricted by mission status: crew
 * can be released from a mission that is already running, and BR-11 says doing so does not send it
 * backwards.
 *
 * A terminal assignment has nothing left to withdraw, so the button goes rather than failing.
 */
export function canWithdrawAssignment(
  user: CurrentUserResponse | null,
  mission: Pick<MissionResponse, 'missionLead'>,
  assignment: Pick<AssignmentResponse, 'status'>,
): boolean {
  if (!user || mission.missionLead.id !== user.id) return false;
  return assignment.status === 'OFFERED' || assignment.status === 'ACCEPTED';
}

/**
 * Feature 07 BR-6: only the crew member named on an offer may answer it, and only while it is open.
 *
 * The caller is always the named crew member here - `GET /api/assignments/me` returns nothing else
 * - so the only real question is whether the offer is still open. Once accepted they are assigned:
 * releasing them is the lead's decision, not theirs, which is why this covers decline as well as
 * accept.
 */
export function canRespondToOffer(
  user: CurrentUserResponse | null,
  assignment: Pick<MyAssignmentResponse, 'status'>,
): boolean {
  return user?.role === 'CREW_MEMBER' && assignment.status === 'OFFERED';
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
