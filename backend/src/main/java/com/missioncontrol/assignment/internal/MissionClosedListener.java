package com.missioncontrol.assignment.internal;

import com.missioncontrol.mission.api.MissionClosedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Closing a mission withdraws the offers nobody answered - FR-8 and BR-8.
 *
 * <p>An unanswered offer to a finished mission is moot, and leaving it {@code OFFERED} would let a
 * crew member accept a place on something that is over. Accepted assignments are deliberately left
 * alone: they are the crew member's history, and history is derived from exactly those rows, so
 * withdrawing them would erase the record of what somebody flew.
 *
 * <p><strong>Synchronous, and inside the closing transaction.</strong> A plain {@code EventListener}
 * runs on the publishing thread before the publisher's transaction commits, so the close and these
 * withdrawals succeed or fail together. A {@code TransactionalEventListener} would run after commit
 * and could leave a closed mission holding live offers if this failed - and recovering from that
 * is what the Event Publication Registry exists for, which architecture.md deliberately has not
 * enabled.
 *
 * <p>By the time this runs, {@code MissionService.close} already holds the mission's write lock, so
 * this takes its row locks in the same order every other staffing command does - mission first,
 * assignments second - and cannot deadlock against an acceptance.
 *
 * <p>No {@code Transactional} of its own. Joining the caller's transaction is the entire point, and
 * annotating it would only invite somebody to add {@code REQUIRES_NEW} later and quietly break the
 * guarantee.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class MissionClosedListener {

    private final AssignmentRepository assignments;

    @EventListener
    void onMissionClosed(final MissionClosedEvent event) {
        int withdrawn = assignments.withdrawOutstandingOffers(
                event.missionId(),
                event.organisationId(),
                AssignmentStatus.OFFERED,
                AssignmentStatus.WITHDRAWN,
                event.closedAt());

        if (withdrawn > 0) {
            log.atInfo().setMessage("Mission closed; withdrew outstanding offers; missionId={}, withdrawn={}")
                    .addArgument(event.missionId()).addArgument(withdrawn).log();
        }
    }
}
