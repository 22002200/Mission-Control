import type { CurrentUserResponse, MissionResponse } from '../api/generated/types.gen';

/**
 * What the signed-in user may do, as the UI understands it.
 *
 * None of this is security. The backend decides, and it will refuse anything these predicates get
 * wrong; the point here is not to offer a button that is going to fail. Keeping the rules in one
 * module rather than inline in each screen means the mission page and the requirement cards cannot
 * disagree about who owns what.
 *
 * The rules mirror invariants M2, M6 and BR-10 in `docs/data-model.md`.
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
