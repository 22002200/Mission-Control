package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Enough of a crew member to put a name to a place on a mission.
 *
 * <p>{@code id} is the <strong>crew profile id</strong>, not the account id. That is the id this
 * module stores, the id feature 06's {@code CandidateResponse} returns, and therefore the id a
 * client already holds when it offers somebody a place - so a match suggestion turns into an offer
 * without a lookup in between.
 *
 * <p>The name is not this module's data. It is resolved through {@code identity} in one bulk call
 * per response, never one per row, which is what feature 07's NFR-4 asks for. Two hops - crew
 * profile to account, account to name - and neither of them a loop.
 *
 * @param id       the crew profile
 * @param fullName as the person is displayed
 */
@Schema(description = "A crew member, named.")
record CrewMemberRef(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The crew profile id, as used when offering a place.")
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Ada Kowalski")
        String fullName) {
}
