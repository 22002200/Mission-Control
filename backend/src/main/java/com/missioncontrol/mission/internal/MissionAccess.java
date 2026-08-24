package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.CurrentUser;
import com.missioncontrol.shared.UserRole;
import org.springframework.stereotype.Component;

/**
 * Who may see a mission, and who may change it.
 *
 * <p>Kept out of {@code PreAuthorize} because neither rule is about a role on its own. 'The owning
 * lead, or any director in the same organisation' needs the mission in hand, and a role expression
 * cannot see one. The only pure role check in this module is on create, where there is no mission
 * yet.
 *
 * <p>Its own bean rather than private methods on a service, so both services apply exactly the
 * same rules rather than two implementations of them that drift apart. They did drift once: an
 * earlier version answered 404 when a lead read someone else's mission but 403 when they tried to
 * add a requirement to it, which told the caller the mission existed - the very thing the 404 was
 * there to hide.
 */
@Component
class MissionAccess {

    private final CurrentUser currentUser;
    private final MissionStaffing staffing;

    MissionAccess(CurrentUser currentUser, MissionStaffing staffing) {
        this.currentUser = currentUser;
        this.staffing = staffing;
    }

    /**
     * Whether this caller is allowed to know the mission exists at all - FR-2 turned around.
     *
     * <p>A mission lead sees the ones they own; a crew member sees the ones they hold an
     * assignment on; a director sees everything in the organisation. Anything else is reported as
     * absent, which is the same answer another tenant gets and for the same reason.
     */
    void requireVisible(MissionEntity mission) {
        boolean visible = switch (currentUser.role()) {
            case DIRECTOR -> true;
            case MISSION_LEAD -> mission.isOwnedBy(currentUser.userId());
            case CREW_MEMBER -> staffing
                    .missionIdsAssignedTo(currentUser.userId(), mission.getOrganisationId())
                    .contains(mission.getId());
        };

        if (!visible) {
            throw new MissionNotFoundException();
        }
    }

    /**
     * A director, or the lead who owns it. Directors are included because they can already see
     * every mission in the organisation and are accountable for all of them.
     *
     * <p>Assumes visibility has been established first, so the only caller this can refuse is one
     * who can genuinely see the mission: a crew member on its crew.
     */
    void requireCanModify(MissionEntity mission) {
        if (currentUser.role() == UserRole.DIRECTOR || mission.isOwnedBy(currentUser.userId())) {
            return;
        }
        throw new MissionForbiddenException(
                "Only the mission lead who owns this mission, or a director, can change it.");
    }

    /**
     * The owning lead alone - BR-10 read strictly.
     *
     * <p>Narrower than {@link #requireCanModify}, and the spec is explicit about it: the
     * requirements endpoints are listed as owner-only while the mission endpoints say owner or
     * director. Describing the crew a mission needs is planning work, and planning belongs to
     * whoever is doing it.
     */
    void requireIsOwner(MissionEntity mission) {
        if (!mission.isOwnedBy(currentUser.userId())) {
            throw new MissionForbiddenException(
                    "Only the mission lead who owns this mission can change its crew requirements.");
        }
    }
}
