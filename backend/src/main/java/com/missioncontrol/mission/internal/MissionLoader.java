package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Loading a mission the caller is allowed to see, in the one order that is safe.
 *
 * <p>Its own bean rather than a private helper on each service, because the order of these steps
 * is the rule and there is more than one service that has to get it right. {@link MissionAccess}
 * records what happens otherwise: an earlier version of this codebase answered 404 when a lead
 * read another lead's mission but 403 when they tried to add a requirement to it, which told the
 * caller the mission existed - the very thing the 404 was there to hide.
 *
 * <p>Two orderings live here:
 *
 * <ul>
 *   <li><strong>Tenant, then visibility.</strong> A mission in another organisation never comes
 *       back from the query at all; one the caller has no visibility of is refused by
 *       {@code MissionAccess}. Both answer 404, which is what makes another tenant's mission
 *       indistinguishable from one that was never created.</li>
 *   <li><strong>Lock, then fetch.</strong> For a command, the write lock is taken on the bare row
 *       before the detail read attaches the requirements - see
 *       {@link MissionRepository#lockByIdAndOrganisationId}, which explains why it cannot be one
 *       statement and why this has to be the first entity load in the transaction.</li>
 * </ul>
 *
 * <p>Deliberately not folded into {@code MissionAccess}: that type is a pure policy object with no
 * repository, which is what lets both service tests construct it directly rather than mocking it.
 */
@Component
class MissionLoader {

    private final MissionRepository missions;
    private final MissionAccess access;
    private final CurrentUser currentUser;

    MissionLoader(MissionRepository missions, MissionAccess access, CurrentUser currentUser) {
        this.missions = missions;
        this.access = access;
        this.currentUser = currentUser;
    }

    /** For a read that needs the mission itself but not its requirements. */
    MissionEntity visible(UUID id) {
        return checked(missions.findByIdAndOrganisationId(id, currentUser.organisationId())
                .orElseThrow(MissionNotFoundException::new));
    }

    /** For a read that renders the requirements too, in one query rather than one per row. */
    MissionEntity visibleDetail(UUID id) {
        return checked(detail(id));
    }

    /**
     * For any command that changes the mission.
     *
     * <p>Locks first, so two callers racing the same transition serialise and the loser reads the
     * status the winner committed rather than the one it started from.
     */
    MissionEntity visibleForUpdate(UUID id) {
        missions.lockByIdAndOrganisationId(id, currentUser.organisationId())
                .orElseThrow(MissionNotFoundException::new);
        // Same row, now with its requirements attached: the persistence context returns the very
        // instance the lock was taken on, so this is a second query and not a second mission.
        return checked(detail(id));
    }

    private MissionEntity detail(UUID id) {
        return missions.findDetailByIdAndOrganisationId(id, currentUser.organisationId())
                .orElseThrow(MissionNotFoundException::new);
    }

    private MissionEntity checked(MissionEntity mission) {
        access.requireVisible(mission);
        return mission;
    }
}
