package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One submit-and-decide cycle, as the API reports it - FR-6.
 *
 * <p>{@code decidedBy}, {@code decidedAt} and {@code comment} are absent while the cycle is
 * pending. With {@code default-property-inclusion: non_null} they do not appear in the JSON at
 * all, which is the same wire behaviour {@code closeReason} already has on a mission.
 *
 * <p><strong>The history is returned whole, not paged</strong>, unlike every other list in this
 * API. FR-6 asks for every cycle, and the screen that shows them needs the count to label itself
 * before it can render anything. That rests on an assumption worth stating rather than leaving
 * implicit: <em>the number of cycles on one mission stays small</em> - it grows only when a plan is
 * sent back and resubmitted, so single digits in practice. If that ever stops being true, the fix
 * is the standard {@code page}/{@code size} envelope with the same newest-first order, and a
 * caller that asks only for the first page.
 */
@Schema(description = "One submit-and-decide cycle on a mission.")
public record MissionApprovalResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVED")
        ApprovalDecision decision,

        @Schema(description = "The rejection reason, or a note left with a decision.",
                example = "The EVA line needs a second qualified operator before this can fly.")
        String comment,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserRef submittedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-02-01T09:00:00Z")
        Instant submittedAt,

        @Schema(description = "Absent while the cycle is still PENDING.")
        UserRef decidedBy,

        @Schema(description = "Absent while the cycle is still PENDING.",
                example = "2026-02-02T14:30:00Z")
        Instant decidedAt) {
}
