package com.missioncontrol.mission.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.missioncontrol.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invariant M8 as the database enforces it, not as the service intends it.
 *
 * <p>Worth its own test because the constraint is a <em>partial</em> unique index, and both halves
 * of that are load-bearing in opposite directions. A plain unique index on {@code mission_id} would
 * stop the second submission of a mission that was rejected once, which FR-7 explicitly allows. No
 * index at all would let two concurrent submissions each open a cycle, and then a decision would
 * settle whichever one it happened to read.
 *
 * <p>{@code Transactional}, so every row here rolls back - the other integration tests share this
 * database and count what is in it.
 */
@Transactional
class MissionApprovalRepositoryIT extends AbstractIntegrationTest {

    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID DIRECTOR = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    /** Tethys Relay: seeded, and already carrying the one open cycle this file works around. */
    private static final UUID SEEDED_PENDING = UUID.fromString("a4000000-0000-0000-0000-000000000002");
    /** Aurora Survey: seeded in PLAN, so it has no cycles at all. */
    private static final UUID SEEDED_PLAN = UUID.fromString("a4000000-0000-0000-0000-000000000001");

    private static final Instant SUBMITTED = Instant.parse("2026-02-01T09:00:00Z");

    @Autowired private MissionApprovalRepository approvals;

    @Test
    @DisplayName("A mission cannot hold two PENDING cycles at once - M8")
    void refusesASecondOpenCycle() {
        approvals.saveAndFlush(cycle(SEEDED_PLAN, ApprovalDecision.PENDING, SUBMITTED));

        assertThatThrownBy(() -> approvals.saveAndFlush(
                cycle(SEEDED_PLAN, ApprovalDecision.PENDING, SUBMITTED.plusSeconds(60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Any number of settled cycles may coexist with one open cycle - FR-7")
    void allowsManySettledCycles() {
        approvals.saveAndFlush(cycle(SEEDED_PLAN, ApprovalDecision.REJECTED, SUBMITTED));
        approvals.saveAndFlush(
                cycle(SEEDED_PLAN, ApprovalDecision.REJECTED, SUBMITTED.plusSeconds(60)));
        approvals.saveAndFlush(
                cycle(SEEDED_PLAN, ApprovalDecision.CANCELLED, SUBMITTED.plusSeconds(120)));
        approvals.saveAndFlush(
                cycle(SEEDED_PLAN, ApprovalDecision.APPROVED, SUBMITTED.plusSeconds(180)));
        approvals.saveAndFlush(
                cycle(SEEDED_PLAN, ApprovalDecision.PENDING, SUBMITTED.plusSeconds(240)));

        assertThat(approvals.findHistory(SEEDED_PLAN, ORG)).hasSize(5);
    }

    @Test
    @DisplayName("The history comes back newest first, with a stable tiebreak")
    void historyIsNewestFirst() {
        approvals.saveAndFlush(cycle(SEEDED_PLAN, ApprovalDecision.REJECTED, SUBMITTED));
        approvals.saveAndFlush(
                cycle(SEEDED_PLAN, ApprovalDecision.APPROVED, SUBMITTED.plusSeconds(60)));

        List<MissionApprovalEntity> history = approvals.findHistory(SEEDED_PLAN, ORG);

        assertThat(history).extracting(MissionApprovalEntity::getDecision)
                .containsExactly(ApprovalDecision.APPROVED, ApprovalDecision.REJECTED);
    }

    @Test
    @DisplayName("The open cycle is found by mission, organisation and decision together")
    void findsTheOpenCycle() {
        assertThat(approvals.findByMissionIdAndOrganisationIdAndDecision(
                SEEDED_PENDING, ORG, ApprovalDecision.PENDING)).isPresent();

        // T1: the same row, asked for from the wrong organisation, is simply not there.
        UUID otherOrg = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        assertThat(approvals.findByMissionIdAndOrganisationIdAndDecision(
                SEEDED_PENDING, otherOrg, ApprovalDecision.PENDING)).isEmpty();
        assertThat(approvals.findHistory(SEEDED_PENDING, otherOrg)).isEmpty();
    }

    @Test
    @DisplayName("A rejection with no comment is refused by the schema, not only by the API - BR-6")
    void rejectionNeedsAComment() {
        MissionApprovalEntity uncommented = MissionApprovalEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(ORG)
                .missionId(SEEDED_PLAN)
                .submittedBy(LEAD)
                .submittedAt(SUBMITTED)
                .decidedBy(DIRECTOR)
                .decidedAt(SUBMITTED.plusSeconds(60))
                .decision(ApprovalDecision.REJECTED)
                .build();

        assertThatThrownBy(() -> approvals.saveAndFlush(uncommented))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("A settled cycle must say who decided it and when - NFR-4")
    void settledCycleNeedsADecider() {
        MissionApprovalEntity noDecider = MissionApprovalEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(ORG)
                .missionId(SEEDED_PLAN)
                .submittedBy(LEAD)
                .submittedAt(SUBMITTED)
                .decision(ApprovalDecision.APPROVED)
                .build();

        assertThatThrownBy(() -> approvals.saveAndFlush(noDecider))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("A pending cycle must not carry a decider - the same rule, the other way")
    void pendingCycleMustNotHaveADecider() {
        MissionApprovalEntity contradictory = MissionApprovalEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(ORG)
                .missionId(SEEDED_PLAN)
                .submittedBy(LEAD)
                .submittedAt(SUBMITTED)
                .decidedBy(DIRECTOR)
                .decidedAt(SUBMITTED.plusSeconds(60))
                .decision(ApprovalDecision.PENDING)
                .build();

        assertThatThrownBy(() -> approvals.saveAndFlush(contradictory))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static MissionApprovalEntity cycle(UUID missionId, ApprovalDecision decision,
                                               Instant submittedAt) {
        boolean settled = decision != ApprovalDecision.PENDING;
        return MissionApprovalEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(ORG)
                .missionId(missionId)
                .submittedBy(LEAD)
                .submittedAt(submittedAt)
                .decidedBy(settled ? DIRECTOR : null)
                .decidedAt(settled ? submittedAt.plusSeconds(30) : null)
                .decision(decision)
                .comment(decision == ApprovalDecision.REJECTED ? "Needs reshaping." : null)
                .build();
    }
}
