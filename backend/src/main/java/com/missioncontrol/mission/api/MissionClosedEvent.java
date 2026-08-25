package com.missioncontrol.mission.api;

import java.time.Instant;
import java.util.UUID;

/**
 * A mission has been closed, for whatever reason.
 *
 * <p>An event rather than a call, because closing a mission has to withdraw its outstanding offers
 * - feature 07's FR-8 - and that is a write into a module this one must never depend on. A read
 * model cannot help: {@link StaffingReadModel} answers questions and does not act. So this module
 * announces what happened and stops caring, which is exactly the case architecture.md describes
 * when it says an event is for a side effect the originating module should not know about.
 *
 * <p><strong>Listeners run inside the closing transaction.</strong> A plain {@code EventListener}
 * is synchronous and joins the publisher's transaction, so the close and everything it triggers
 * commit or roll back together. That is deliberate, and it is why this needs no Event Publication
 * Registry: architecture.md's caveat about events not being transactional is about a listener that
 * runs after commit, and nothing here does.
 *
 * <p>The mission row is already write-locked by the time this is published, because every command
 * that changes a mission takes that lock first. A listener that writes rows of its own therefore
 * takes them in the same order as every other staffing command, which is what keeps close and
 * accept from deadlocking.
 *
 * @param missionId      the mission that closed
 * @param organisationId its tenant, so a listener never has to look one up to scope its own write
 * @param closedAt       when, UTC - the same instant the mission itself recorded
 */
public record MissionClosedEvent(UUID missionId, UUID organisationId, Instant closedAt) {
}
