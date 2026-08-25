package com.missioncontrol.assignment.internal;

import com.missioncontrol.mission.api.MissionPlan;
import com.missioncontrol.mission.api.MissionWindow;
import com.missioncontrol.platform.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Who may do what to an assignment - BR-6 and BR-9.
 *
 * <p>Its own bean rather than private methods on the service, following {@code MissionAccess}: a
 * pure policy object with no repository, so a unit test can construct it directly rather than
 * mocking its way to one. That module's documentation records what happens when the same rule is
 * written twice, and this module has the same shape of temptation - offer and withdraw are both
 * lead-only and would each have grown their own copy.
 *
 * <p><strong>The two halves never overlap.</strong> Offering and withdrawing are the owning mission
 * lead's; accepting and declining are the named crew member's. Nobody holds both, and that is not
 * an accident of the rules but the point of them - a lead who could accept on somebody's behalf
 * could crew a mission without anyone agreeing to fly it.
 *
 * <p>Note what this class does <em>not</em> do: decide whether the caller may see the mission at
 * all. That is {@code mission}'s job and it answers 404, which is what keeps another tenant's
 * mission indistinguishable from one that was never created. By the time anything here runs, the
 * caller has already been established as someone who can see what they are asking about.
 */
@Component
class AssignmentAccess {

    private final CurrentUser currentUser;

    AssignmentAccess(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * BR-9 for offering: the owning mission lead alone.
     *
     * <p>Narrower than the owner-or-director rule {@code MissionPlans} has already applied, and
     * deliberately so. A director reaching here can see the mission, so they get a 403 rather than
     * a 404 - the refusal is about the verb, not about the mission's existence.
     */
    void requireCanOffer(MissionPlan mission) {
        if (!mission.isOwnedBy(currentUser.userId())) {
            throw AssignmentForbiddenException.notTheOwningLead("offer places on it");
        }
    }

    /**
     * BR-9 for withdrawing: the owning mission lead alone, again.
     *
     * <p>A director may read every assignment in the organisation and withdraw none. Their lever on
     * a mission they disagree with is closing it, which withdraws the outstanding offers anyway -
     * see BR-8 - and does so as a decision about the mission rather than about one person's place
     * on it.
     */
    void requireCanWithdraw(MissionWindow mission) {
        if (!mission.isOwnedBy(currentUser.userId())) {
            throw AssignmentForbiddenException.notTheOwningLead("withdraw crew from it");
        }
    }

    /**
     * BR-6: only the crew member named on the assignment may answer it.
     *
     * <p>Takes the caller's resolved crew profile rather than looking it up, so the one lookup that
     * establishes who the caller is happens once per request in the service and this stays a pure
     * comparison.
     *
     * @param verb completes the sentence 'Only the crew member offered this place can ... it'
     */
    void requireIsTheCrewMember(AssignmentEntity assignment, UUID callerCrewMemberId, String verb) {
        if (!assignment.isOfferedTo(callerCrewMemberId)) {
            throw AssignmentForbiddenException.notTheCrewMember(verb);
        }
    }
}
