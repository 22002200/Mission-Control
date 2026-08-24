 ▐▛███▛█   Claude Code v2.1.241
▝▜██████▀  Opus 5 · API Usage Billing
  ▝▝ ▝▝    C:\Projects\Mission Control\backend


❯ Read docs/product.md, docs/architecture.md, docs/data-model.md. Implement     
  05-mission-approval.md. Clarify any contradictions or requirements as needed. 
  Explain any UI decisions. Do not read files under /transcripts/ or            
  /frontend/node_modules/ or /backend/target/ unless absolutely necessary, and  
  prompt me with the reason.                                                    

● I'll start by reading the docs the user named and the feature spec.

  Searched for 1 pattern, read 4 files (ctrl+o to expand)

● Now let me explore the codebase in parallel while I read the adjacent feature
  spec.

● 2 background agents launched (↓ to manage)
   ├ Explore backend mission module
   └ Explore frontend mission screens

  Read 3 files, ran 2 shell commands (ctrl+o to expand)

● I have a strong picture of the docs. Waiting on the two exploration agents to
  report.

  Read 1 file, ran 3 shell commands (ctrl+o to expand)

● Both exploration agents are still running. Let me check the test setup layout
  while I wait.

  Read 3 files, ran 1 shell command (ctrl+o to expand)

● Perfect — bulk name lookup already exists for the submittedBy/decidedBy
  fields. Still waiting on the two agents.

  Read 5 files (ctrl+o to expand)

● I have a detailed backend picture. The feature spec has no UI section, so
  before I finalise the plan I need your call on a few UI decisions. (The two
  exploration agents are still running; I'll fold their findings in.)

● User answered Claude's questions:
  ⎿  · The mission board at /missions currently buckets PLAN, PENDING_APPROVAL, 
     APPROVED and REJECTED into one "Draft" section. FR-8 says directors need to
     find missions awaiting their decision. How should that surface? → Extra 
     board section, directors only (Recommended)
     · Where should the submit / approve / reject / return-to-plan actions be 
     triggered from? → Mission detail page only (Recommended)
     · How should the approval history (GET /approvals — every submit-and-decide
     cycle) be displayed? → Collapsed section on mission detail (Recommended)
     · The seed data has missions already sitting in PENDING_APPROVAL, APPROVED 
     and REJECTED, but no approval records exist for them (the table arrives 
     with this feature). What should happen? → Leave seed data alone

● Agent "Explore frontend mission screens" finished · 2m 49s

● Agent "Explore backend mission module" finished · 4m 0s

● Both agents reported. I have what I need, with two genuine conflicts left to
  settle.

● User answered Claude's questions:
  ⎿  · CLAUDE.md says "avoid inline MUI styling and create a styled component if
     more than one override", but `styled()` appears nowhere in frontend/src 
     today — every component uses `sx`, theme-level `styleOverrides`, and small 
     local sub-components (`Fact`, `NavItem`). Feature 05 adds an 
     approval-history timeline and a decision dialog. Which do I follow? → 
     styled() for the new multi-override pieces
     · A mission can be closed from PENDING_APPROVAL (feature 04's close 
     action). Its open PENDING approval cycle then never gets a decision. 
     `ApprovalDecision` is a pinned, append-only enum of PENDING / APPROVED / 
     REJECTED. What should happen to that row? → Append a CANCELLED decision 
     code
● Decisions noted. Let me have a Plan agent stress-test the backend design while
  I draft.

● Plan(Design backend approval implementation)
  ⎿  Backgrounded agent (↓ to manage · ctrl+o to expand)

● Updated plan
  ⎿  /plan to preview

  Searched for 1 pattern, read 4 files (ctrl+o to expand)

● I have the full frontend picture now. Waiting on the Plan agent's backend
  design before finalising.

  Read 3 files (ctrl+o to expand)

● I have everything on the frontend side. Waiting for the backend design agent
  to report.

  Ran 2 shell commands (ctrl+o to expand)

● I have every backend pattern I need to mirror. Let me check on the design
  agent.

  Ran 2 shell commands (ctrl+o to expand)

● Updated plan
  ⎿  /plan to preview

● Frontend and docs sections are drafted. Checking whether the backend design
  agent has finished.

  Ran 3 shell commands (ctrl+o to expand)

● The design agent is still working. Let me draft the backend section from what
  I've verified directly, and reconcile when it reports.

● Updated plan
  ⎿  Error editing file

● Updated plan
  ⎿  /plan to preview

● Updated plan
  ⎿  /plan to preview

  Ran 1 shell command (ctrl+o to expand)

● Updated plan
  ⎿  /plan to preview

● The plan is complete. Let me give the design agent a final moment to report
  before I present it.
  Ran 1 shell command (ctrl+o to expand)

● User approved Claude's plan
  ⎿  Plan saved to: 
     ~\.claude\plans\read-docs-product-md-docs-architecture-m-async-kay.md · 
     /plan to edit
     Feature 05 — Mission Approval

     Context

     Feature 04 built mission planning: a Mission Lead can create a mission,
     describe the crew it
     needs, edit it, start it and close it. What it cannot do is get a mission
     out of PLAN. Every
     arrow in the lifecycle that involves a director — submit, approve, reject,
     return to plan — is
     unbuilt, which is why POST /start can never succeed today and why
     MissionStatus.APPROVED
     exists only in seed data.

     Feature 05 closes that gap. It is the only place in the product where roles
     genuinely divide:
     the person who plans a mission is never the person who approves it (M2
     makes a director
     incapable of owning one, so BR-8's "a director can never approve their own
     work" holds by
     construction).

     Concretely it delivers docs/features/05-mission-approval.md: five
     endpoints, a
     MissionApproval entity that keeps one row per submit-and-decide cycle, and
     the UI for both
     sides of the decision. It also closes invariant M12 — a mission with no
     crew requirements can no
     longer be submitted, which was the hole feature 04 had to paper over at
     start time.

     Not in scope: crew matching (06), assignment (07), dashboards (08). POST 
     /start still
     cannot succeed after this feature, because staffing counts do not exist
     until 07 — but for the
     first time a mission can legitimately reach APPROVED.

     Decisions taken before planning

     Recorded here because they are choices, not deductions, and the spec is
     silent on all five.

     ┌─────┬────────────────────────────────────────────────────────────────────
     ┐
     │  #  │                              Decision
     │
     ├─────┼────────────────────────────────────────────────────────────────────
     ┤
     │     │ Director queue — an extra "Awaiting approval" section on the
     │
     │ 1   │ existing /missions board, rendered only for a DIRECTOR. No new
     │
     │     │ route.
     │
     ├─────┼────────────────────────────────────────────────────────────────────
     ┤
     │     │ Action placement — Submit / Approve / Reject / Return to plan live
     │
     │ 2   │  on the mission detail page only, in the existing action row
     │
     │     │ beside Edit / Start / Close. A director must open a mission before
     │
     │     │  deciding it.
     │
     ├─────┼────────────────────────────────────────────────────────────────────
     ┤
     │     │ Approval history — a collapsed MUI Accordion on the mission detail
     │
     │ 3   │  page, below the crew requirements, expanded by default when the
     │
     │     │ newest cycle is a rejection.
     │
     ├─────┼────────────────────────────────────────────────────────────────────
     ┤
     │     │ Seed data is not changed. Consequence: approve/reject must
     │
     │ 4   │ tolerate a PENDING_APPROVAL mission that has no open cycle — see
     │
     │     │ Backend §5.
     │
     ├─────┼────────────────────────────────────────────────────────────────────
     ┤
     │     │ ApprovalDecision gains code 4 CANCELLED. Closing a mission that is
     │
     │ 5   │  awaiting a decision settles its open cycle rather than leaving it
     │
     │     │  dangling.
     │
     ├─────┼────────────────────────────────────────────────────────────────────
     ┤
     │     │ Styling — new multi-override pieces use styled(), per CLAUDE.md.
     │
     │ 6   │ This is the first styled() in the codebase; feature 04 uses sx
     │
     │     │ throughout.
     │
     └─────┴────────────────────────────────────────────────────────────────────
     ┘

     Contradictions found in the specs, and how they are resolved

     These are stated up front because the implementation depends on the
     reading.

     1. replan is owner-only, which is narrower than M6. data-model.md M6 says
        "only the owning
        mission lead, or a director in the same organisation, may modify a
        mission", and feature
        04 implements exactly that for edit / start / close. But 05's API table
        says
        POST /replan → "owner MISSION_LEAD", and its error table says 403 when
        "the caller is not
        the owner (submit, return)". Resolution: follow 05. Deciding to have
        another go at a
        rejected plan is planning work, and the module already has
        MissionAccess.requireIsOwner for
        exactly this narrowing (feature 04 uses it for the requirement
        endpoints, on the same
        reasoning). Recorded as a note on the spec, not silently.
     2. 403 vs 404 for a non-owning mission lead. 05's error table says "caller
        is not the owner →
        403", but feature 04 established — and documented at length — that a
        lead who cannot see
        another lead's mission gets 404, because a 403 confirms the mission
        exists. Resolution:
        requireVisible runs first and answers 404; 403 is left for a caller who
        genuinely can see
        the mission but may not act — a director attempting submit, or a crew
        member on the crew.
        This satisfies both of 05's acceptance criteria ("a non-owning mission
        lead cannot submit",
        which does not name a code, and "a director cannot submit a mission",
        which lands on 403).
     3. FR-8 has no endpoint. The spec's own API section resolves it: directors
        reach missions
        awaiting decision through GET /api/missions?status=PENDING_APPROVAL,
        which is already
        organisation-wide for a director. No backend work; decision 1 above
        covers the UI.
     4. The approvals list is unpaginated, against the page/size convention in
        docs/features/README.md. FR-6 says "returns every approval cycle, newest
        first" and the
        accordion needs them all to label itself. A mission accrues a cycle per
        resubmission, so the
        list is bounded by human patience. Deliberate deviation, noted in the
        spec.
     5. replan vs "return". FR-4 and the API table say replan; the error table
        says "return".
        The path is /replan and the TypeScript client function will be
        replanMission. The UI
        button reads "Return to plan", which is what the spec's prose calls it.

     Backend

     Everything lives in com.missioncontrol.mission.internal — ModularityTests
     will fail the build
     otherwise, and nothing outside the module needs any of it.
     allowedDependencies already covers
     identity :: api, so no package-info.java change is required.

     Much of what the spec asks for is already built and can simply be used:

     Spec need: All three new transitions (M3)
     Already exists: MissionStatus.canTransitionTo — PLAN→PENDING_APPROVAL,
     PENDING_APPROVAL→APPROVED/REJECTED, REJECTED→PLAN, exhaustively tested
     ────────────────────────────────────────
     Spec need: urn:mission-control:invalid-transition + currentStatus /
     attemptedTransition
     Already exists: InvalidMissionTransitionException
     ────────────────────────────────────────
     Spec need: 403 / 404 / 400 problem types
     Already exists: MissionForbiddenException, MissionNotFoundException,
     MissionValidationException
     ────────────────────────────────────────
     Spec need: Owner-only and director-or-owner checks
     Already exists: MissionAccess.requireIsOwner / requireVisible
     ────────────────────────────────────────
     Spec need: submittedBy / decidedBy name resolution
     Already exists: UserDirectory.findByIds (bulk) + MissionMapper.userRef +
     the
     UserRef record, whose Javadoc already says feature 05 will reuse it
     ────────────────────────────────────────
     Spec need: Closing a rejected mission as REJECTED
     Already exists: MissionService.closeReasonFor already permits it only from
     REJECTED

     1. Schema — db/changelog/modules/mission/003-create-mission-approval.yaml

     Picked up by includeAll; no master edit. Follows the house style exactly:
     author: mission-control, a comment: >- per changeSet naming the invariant,
     raw - sql: with explicit
     rollback: for anything createTable cannot express.

     ┌─────────────────┬────────────────┬──────────────────────────────────────┐
     │     Column      │      Type      │                Notes                 │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │ id              │ UUID           │ pk_mission_approval                  │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │ organisation_id │ UUID           │ not null, no FK (cross-module)       │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │                 │                │ not null, real FK                    │
     │ mission_id      │ UUID           │ fk_mission_approval_mission →        │
     │                 │                │ mission(id), deleteCascade: true —   │
     │                 │                │ same module                          │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │ submitted_by    │ UUID           │ not null, no FK                      │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │ submitted_at    │ TIMESTAMP WITH │ not null                             │
     │                 │  TIME ZONE     │                                      │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │ decided_by      │ UUID           │ null while pending                   │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │ decided_at      │ TIMESTAMP WITH │ null while pending                   │
     │                 │  TIME ZONE     │                                      │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │ decision        │ SMALLINT       │ not null, pinned code                │
     ├─────────────────┼────────────────┼──────────────────────────────────────┤
     │ comment         │ VARCHAR(1000)  │ rejection reason or approval note    │
     └─────────────────┴────────────────┴──────────────────────────────────────┘

     Constraints, each its own changeSet with a rollback:

     - ck_mission_approval_decision CHECK (decision BETWEEN 1 AND 4) — mirrors
       ck_mission_status.
     - ck_mission_approval_decided CHECK ((decision = 1) = (decided_by IS NULL 
       AND decided_at IS NULL)) — stated as an equivalence, exactly like
       ck_mission_close_reason_present, so neither
       a pending row carrying a decider nor a decided row missing one can exist.
       This is NFR-4 in the
       schema.
     - M8 as a partial unique index, raw SQL because Liquibase OSS createIndex
       has no WHERE:
       CREATE UNIQUE INDEX ux_mission_approval_pending ON mission_approval 
       (mission_id) WHERE decision = 1. This is what makes "at most one PENDING
       approval" true under concurrency rather
       than only in the happy path — two simultaneous submits cannot both open a
       cycle.
     - ix_mission_approval_mission ON mission_approval (mission_id, submitted_at
       DESC) — serves
       FR-6's newest-first read directly.

     No change to the mission table, and none is needed: ck_mission_status
     already permits every
     status feature 05 reaches.

     2. ApprovalDecision + converter

     Hand-rolled in the same shape as MissionStatus / MissionCloseReason (there
     is no shared
     CodedEnum and introducing one is out of scope): private final int code,
     public int code(),
     public static ApprovalDecision fromCode(int) throwing
     IllegalArgumentException, and a
     @Converter(autoApply = true) AttributeConverter<ApprovalDecision, Short> —
     Short, because the
     column is SMALLINT and ddl-auto: validate compares JDBC types.

     PENDING(1), APPROVED(2), REJECTED(3), CANCELLED(4) (decision 5). Public,
     like
     MissionStatus, because it appears in a public response record.

     3. MissionApprovalEntity

     Package-private @Entity @Table(name = "mission_approval") @Getter @Builder 
     @NoArgsConstructor(PROTECTED) @AllArgsConstructor(PRIVATE), id assigned
     with
     UUID.randomUUID() in the service, @Convert(converter = 
     ApprovalDecisionConverter.class) on
     decision. missionId is a plain UUID column, not a @ManyToOne — a
     MissionApproval is
     never loaded as part of the mission aggregate and giving MissionEntity a
     collection of them
     would drag approval rows into every mission read.

     No setters. One mutator, mirroring the existing style:

     /** Settles the open cycle. NFR-3: append-only means this happens once and 
     never again. */
     void decide(ApprovalDecision decision, UUID decidedBy, String comment,
     Instant now) { ... }

     It asserts the row is still PENDING and throws IllegalStateException
     otherwise — a
     programming error, not a user error, since the service checks the mission
     status first.

     4. MissionApprovalRepository

     Optional<MissionApprovalEntity> findByMissionIdAndDecision(UUID missionId,
     ApprovalDecision d);
     List<MissionApprovalEntity>
     findByMissionIdOrderBySubmittedAtDescIdDesc(UUID missionId);

     id as a tiebreaker on the sort, because two cycles created in the same test
     with a fixed
     Clock share a submittedAt and an unstable order makes FR-7's "two cycles in
     order" assertion
     flaky.

     The pessimistic lock goes on MissionRepository, not here:

     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("select m from MissionEntity m where m.id = :id and m.organisationId
     = :organisationId")
     Optional<MissionEntity> lockByIdAndOrganisationId(UUID id, UUID
     organisationId);

     Deliberately not the fetch-join query: Postgres refuses FOR UPDATE on the
     nullable side of
     an outer join, so locking and eager-loading the requirements cannot be the
     same statement. The
     approval transaction locks the bare row first, then calls the existing
     findDetailByIdAndOrganisationId for the response — which returns the same
     managed instance from
     the persistence context, so it is one extra query and no N+1.

     Why pessimistic rather than @Version: the spec says the second caller "sees
     the status has
     already moved and fails". A lock produces exactly that — the loser blocks,
     re-reads APPROVED,
     and gets 409 invalid-transition with currentStatus: "APPROVED", which is
     the response the
     error table specifies. Optimistic locking would instead surface a generic
     OptimisticLockingFailureException that has to be translated without knowing
     the new status, and
     adding @Version would change every existing mission write path for a
     guarantee only these four
     endpoints need.

     5. MissionApprovalService — a second @Service, not more methods on 
     MissionService

     MissionService is already 363 lines and owns planning. Approval is a
     distinct concern with a
     distinct collaborator set, and CrewRequirementService sets the precedent
     for a second service
     in the module. Dependencies: MissionRepository, MissionApprovalRepository,
     MissionAccess,
     MissionDetailAssembler, UserDirectory, CurrentUser, Clock.

     Extract MissionDetailAssembler (new package-private @Component) from
     MissionService.toDetail — the three bulk lookups plus
     MissionMapper.toResponse. All four
     approval endpoints return the mission detail, and duplicating that assembly
     is exactly how NFR-1
     gets broken by accident. MissionService.toDetail becomes a one-line
     delegation; no behaviour
     change, and MissionServiceTest needs only its constructor call updated.

     Each of the four methods is a single @Transactional:

     submit(id):  lock → requireVisible → requireIsOwner →
     requireTransition(PENDING_APPROVAL)
                  → requireHasRequirements   [M12 / BR-5, 409
     mission-has-no-requirements]
                  → openCycle(...)           [insert PENDING]
                  → mission.submit(now)
     approve(id): lock → requireVisible → requireIsDirector →
     requireTransition(APPROVED)
                  → openCycleOrSynthesise(...).decide(APPROVED, caller, comment,
     now)
                  → mission.approve(now)
     reject(id):  as approve, with decision REJECTED and a required comment
     replan(id):  lock → requireVisible → requireIsOwner →
     requireTransition(PLAN)
                  → mission.replan(now)      [BR-9: history untouched, no new
     row]

     NFR-1 note: requireHasRequirements reads mission.getRequirements(), which
     is lazy on the
     locked instance — call findDetailByIdAndOrganisationId before the check so
     the fetch join
     supplies it rather than a lazy load per requirement.

     openCycleOrSynthesise is decision 4's consequence. The seed puts two
     missions in
     PENDING_APPROVAL with no approval rows, because the table did not exist
     when they were written.
     Rather than refusing to decide them, the service falls back to opening a
     cycle from the mission's
     own facts — submittedBy = missionLeadId, submittedAt = 
     mission.getUpdatedAt() — and decides it
     in the same transaction. History is then never empty for a decided mission,
     the branch is four
     lines, and its Javadoc says plainly that it exists for pre-05 rows. Worth a
     note in the feature
     spec, because it is a compromise rather than a design.

     6. MissionAccess.requireIsDirector

     A fourth predicate beside the three that exist. BR-3 is a pure role check,
     so it could be
     @PreAuthorize("hasRole('DIRECTOR')") on the controller — but that fires
     before the mission
     is loaded, which would answer 403 to a director from another organisation
     where the spec's
     acceptance criteria demand 404. Putting it after requireVisible, in the
     bean that already holds
     the other three rules, gets the ordering right and keeps all four rules in
     one readable place.
     MissionController's class Javadoc already explains why this module prefers
     MissionAccess to
     role expressions.

     7. MissionEntity mutators

     Four more named methods in the existing style, each taking Instant now and
     setting only the
     status and updatedAt — the transition legality is MissionStatus's job and
     the authorisation is
     the service's, exactly as the class Javadoc states:

     submit(now) → PENDING_APPROVAL; approve(now) → APPROVED; reject(now) →
     REJECTED;
     replan(now) → PLAN.

     8. CANCELLED on close, without a cycle between the two services

     MissionService.close must now settle any open cycle, but MissionService
     calling
     MissionApprovalService (which would in turn need mission loading) is the
     kind of tangle worth
     avoiding. Keep it one-directional: MissionService gains a 
     MissionApprovalRepository
     dependency and, inside its existing close transaction, does

     approvals.findByMissionIdAndDecision(mission.getId(),
     ApprovalDecision.PENDING)
             .ifPresent(cycle -> cycle.decide(ApprovalDecision.CANCELLED,
     currentUser.userId(),
                     request.comment(), now));

     A repository is data access, not a second service, so there is no
     service-to-service edge and no
     duplicated transition logic. MissionApprovalService does not know close
     exists.

     9. Controller

     Five methods on the existing MissionController. Method names become 
     operationIds and
     therefore TypeScript client function names, so they are chosen once:
     submitMission,
     approveMission, rejectMission, replanMission, listMissionApprovals. Each
     gets the full
     @Operation + @ApiResponse(... ProblemDetail.class) set the other methods
     carry.

     Records: ApprovalDecisionRequest(@Size(max = 1000) String comment) for
     approve (whole body
     optional, substituted like closeMission does);
     RejectMissionRequest(@NotBlank @Size(max = 1000) String comment) for reject
     — @NotBlank is what produces the specified 400 validation-failed
     rather than a 409. Response:
     MissionApprovalResponse(UUID id, ApprovalDecision decision, String comment,
     UserRef submittedBy, Instant submittedAt, UserRef decidedBy, Instant 
     decidedAt), with @Schema(requiredMode = REQUIRED) on the non-null fields so
     the generated TypeScript is not needlessly optional.

     listMissionApprovals collects every distinct submittedBy and decidedBy
     across the whole
     list and makes one UserDirectory.findByIds call — the same shape
     MissionService.summariser
     uses, and the reason UserDirectory has no single-id method.

     The entity → response mapping is a new
     static MissionApprovalResponse toResponse(MissionApprovalEntity, Map<UUID, 
     UserSummary>) on the
     existing MissionMapper, so it reuses that class's private static 
     userRef(...) — including its
     "Unknown user" fallback for a dangling cross-module id — rather than
     widening userRef's
     visibility or duplicating the placeholder.

     10. New exception

     MissionHasNoRequirementsException — 409,
     urn:mission-control:mission-has-no-requirements,
     declared as a private static final URI TYPE beside the class like every
     other module URN. No
     GlobalExceptionHandler change: it handles ApiProblemException generically.

     Backend tests

     Mapping the spec's 15 acceptance criteria onto tiers, following the
     existing split:

     - MissionApprovalServiceTest (Mockito, real MissionAccess, Clock.fixed,
       @Nested
       groups) — owner submits, director-cannot-submit (403), non-owner lead
       (404), not in PLAN
       (409), no requirements (409), director approves, lead-approves (403),
       reject without a comment,
       replan from REJECTED, replan from elsewhere (409), and that
       openCycleOrSynthesise copes
       with a mission that has no open cycle.
     - MissionControllerApiTest — extend the existing slice with the five new
       endpoints: wire
       format, the required-comment 400, and the 409 problem body carrying
       currentStatus /
       attemptedTransition.
     - MissionApprovalIT (new, extends AbstractIntegrationTest) — the full
       submit → reject → replan → submit → approve loop against real Postgres,
       asserting FR-7's two
       cycles in order and that no point in the loop has two PENDING rows; a
       director from org B
       getting 404; closing a rejected mission with closeReason: REJECTED; and
       that closing from
       PENDING_APPROVAL leaves a CANCELLED cycle.
     - Concurrency ("two concurrent approvals ⇒ exactly one success and one 
       409"). This needs two
       real connections, so it cannot live in a @Transactional test —
       MissionLifecycleIT is
       @Transactional and would hand both threads the same transaction. Put it
       in the
       non-transactional MissionApprovalIT: submit one mission, then fire two
       approve calls from an
       ExecutorService synchronised on a CyclicBarrier, assert one 200 and one
       409, and clean
       the mission up in @AfterEach since nothing rolls back. The partial unique
       index gets the same
       treatment for two concurrent submits.
     - MissionEnumConverterTest — extend with ApprovalDecision, including
       CANCELLED.
     - MissionLifecycleIT — its class Javadoc says it exists only because the
       approval endpoints
       do not; rewrite that header and move the fixtures that can now be
       arranged over HTTP onto the
       real endpoints, leaving direct repository setup only where it is still
       the only way (ACTIVE).

     Frontend

     Feature 04 already ships the vocabulary this feature needs — STATUS_LABELS,
     STATUS_COLOURS
     and MissionStatusChip all cover PENDING_APPROVAL / APPROVED / REJECTED
     already, and
     MissionSection takes a section descriptor and queries it, so the director's
     queue needs no
     change to that component at all.

     Regenerate the client first

     frontend/src/api/generated/ is committed and hand-editing it is forbidden.
     With the backend
     running:

     docker compose exec -e OPENAPI_URL=http://backend:8080/v3/api-docs frontend
     npm run generate:api
     docker compose cp frontend:/app/src/api/generated
     ./frontend/src/api/generated

     Both steps — the second is what gets the output back onto the host.
     Everything below assumes
     submitMission / approveMission / rejectMission / replanMission /
     listMissionApprovals
     exist in sdk.gen.ts.

     src/auth/permissions.ts — four predicates

     Mirrors the server rules, in the established (user, mission) shape. Not
     security; the point is
     not to offer a button that will 403.

     /** BR-2 + M12: the owning lead, from PLAN, and only with something to 
     staff. */
     export function canSubmitForApproval(user, mission): boolean {
       return mission.status === 'PLAN' && mission.missionLead.id === user?.id;
     }

     /** BR-3: a director, and only on a mission awaiting a decision. */
     export function canDecideMission(user, mission): boolean {
       return user?.role === 'DIRECTOR' && mission.status ===
     'PENDING_APPROVAL';
     }

     /** BR-7 + the API table: the owning lead alone, deliberately narrower than
     M6. */
     export function canReplanMission(user, mission): boolean {
       return mission.status === 'REJECTED' && mission.missionLead.id ===
     user?.id;
     }

     canSubmitForApproval deliberately does not test requirements.length — the
     button is shown
     disabled with a title explaining why, exactly as Start mission already
     handles under-staffing
     (MissionDetailPage.tsx:136-151). Hiding it would leave "why can I not
     submit this?" unanswered.

     Tests go in the existing src/auth/__tests__/permissions.test.ts.

     src/lib/missionLabels.ts — a director-only fourth section

     sectionFor throws if a status belongs to no section, and the existing test
     asserts every status
     is covered exactly once — so the section list cannot simply be mutated per
     role. Change shape:

     - Widen MissionSection['key'] with 'awaiting'.
     - Add AWAITING_APPROVAL_SECTION (statuses: ['PENDING_APPROVAL'], empty
       message
       "Nothing is waiting on your decision.").
     - Add sectionsForRole(role): for DIRECTOR, the awaiting section followed by
       a Draft narrowed
       to ['PLAN', 'APPROVED', 'REJECTED'], then Active and Completed; for
       everyone else the
       existing MISSION_SECTIONS unchanged.
     - Give sectionFor(status, sections = MISSION_SECTIONS) an optional list, so
       the
       single-status-filter path in MissionsPage resolves against the same list
       it renders.
     - Add APPROVAL_DECISION_LABELS (Pending / Approved / Rejected / Cancelled)
       and a
       decision → chip-colour map (default / success / error / default).

     Update src/lib/__tests__/missionLabels.test.ts to run the coverage and
     no-overlap assertions
     over both role variants rather than the single constant.

     src/pages/MissionsPage.tsx

     One line of substance: sectionsForRole(user?.role) in place of
     MISSION_SECTIONS in the
     visibleSections memo, with sectionFor(status, roleSections) in the filtered
     branch. Per-section
     paging is keyed by section.key, so the new key participates for free.

     src/pages/MissionDetailPage.tsx

     Four buttons into the existing action row, ordered so the primary action
     for the caller's role is
     rightmost, and each following the established handleStart shape
     (setActionError(null) →
     await mutateAsync → refresh() → catch into messageForProblem):

     ┌───────────┬──────────────────────┬──────────────────────────────────────┐
     │  Button   │      Shown when      │              Behaviour               │
     ├───────────┼──────────────────────┼──────────────────────────────────────┤
     │ Submit    │                      │ variant="contained"; disabled with a │
     │ for       │ canSubmitForApproval │  title when                          │
     │ approval  │                      │ mission.requirements.length === 0    │
     ├───────────┼──────────────────────┼──────────────────────────────────────┤
     │ Approve   │ canDecideMission     │ opens ApproveMissionDialog (optional │
     │           │                      │  note)                               │
     ├───────────┼──────────────────────┼──────────────────────────────────────┤
     │           │                      │ color="error", opens                 │
     │ Reject    │ canDecideMission     │ RejectMissionDialog (required        │
     │           │                      │ comment)                             │
     ├───────────┼──────────────────────┼──────────────────────────────────────┤
     │ Return to │ canReplanMission     │ direct mutation, no dialog — it is   │
     │  plan     │                      │ reversible                           │
     └───────────┴──────────────────────┴──────────────────────────────────────┘

     Then <ApprovalHistory missionId={...} missionStatus={...} /> after the crew
     requirements block.

     New components under src/components/missions/

     - ApproveMissionDialog.tsx — the CloseMissionDialog shape exactly: outer
       Dialog with
       {open && <Form/>}, DialogContentText naming the mission, one optional
       multiline TextField
       (maxLength 1000, helperText="Optional"), Alert for the error, Cancel +
       Approve.
     - RejectMissionDialog.tsx — same shape, but the comment is required (BR-6):
       submit disabled
       while comment.trim() is empty, required, helperText explaining a
       rejection has to say why.
     - ApprovalHistory.tsx — an MUI Accordion titled "Approval history (N
       cycles)", its own
       useQuery(listMissionApprovalsOptions(...)), defaultExpanded when the
       newest entry is a
       rejection. Renders per cycle: a decision Chip, decidedBy.fullName and
       formatDateTime(decidedAt), the comment, and "Submitted by X, <date>". A
       PENDING row shows
       "Awaiting a decision". Loading / error / empty follow the three-branch
       pattern in
       MissionSection.tsx:64-76.

     Per decision 6 these are the first styled() components in the codebase: an
     ApprovalRow (the
     left rule and spacing of a timeline entry) and a DecisionMarker. Everything
     with a single
     override stays on sx — the rule is "more than one override", not "never
     sx".

     src/components/missions/CloseMissionDialog.tsx — FR-5

     Today it offers only COMPLETED and ABORTED, with a comment saying REJECTED
     "belongs to
     feature 05". Now it does: when mission.status === 'REJECTED', offer the
     Rejected option and
     preselect it, matching how the server already defaults. Drop the stale
     comment.

     src/lib/problemDetail.ts

     One new case — urn:mission-control:mission-has-no-requirements → "Add at
     least one crew
     requirement before submitting this mission for approval." Everything else
     feature 05 raises
     (invalid-transition, validation-failed, forbidden, not-found) is already
     handled. Covered
     in the existing src/lib/__tests__/problemDetail.test.ts.

     Frontend tests

     Vitest + RTL, vi.mock on sdk.gen not the barrel (the react-query helpers
     import straight
     from it), auth faked with <AuthContext.Provider>, a fresh QueryClient per
     render.

     - permissions.test.ts — the three new predicates, lead vs director vs crew,
       per status.
     - missionLabels.test.ts — both role variants covered and non-overlapping;
       decision labels.
     - MissionsPage.test.tsx — a director sees an "Awaiting approval" heading
       and a lead does not;
       the section queries status: ['PENDING_APPROVAL'].
     - MissionDetailPage.test.tsx — button visibility per role and status;
       Submit disabled with no
       requirements; reject dialog blocks an empty comment; a 409 renders
       through messageForProblem.
     - A test for ApprovalHistory rendering a two-cycle history newest-first.

     Documentation

     - docs/features/05-mission-approval.md — tick the acceptance criteria, and
       add the
       "Notes from the build" section feature 04 established: the CANCELLED
       decision code and why;
       the owner-only replan narrowing against M6; the unpaginated approvals
       list; the 403-vs-404
       reading; pessimistic locking for NFR-2; and the seed-data consequence
       (decision 4) that
       approve must tolerate a mission with no open cycle.
     - docs/data-model.md — add 4 CANCELLED to the ApprovalDecision enum-code
       row with a
       note that it settles the open cycle when a mission awaiting a decision is
       closed; resolve the
       "M11 cannot be satisfied until 07" open question's dependency on 05; note
       that M12 is now
       enforced.
     - docs/architecture.md — mission now owns MissionApproval as the ownership
       table
       already predicts; add the concurrency decision to the decisions table.
     - README.md — an approval row block in the Missions table, a short "Mission
       approval"
       section, and update "Current limitations", which currently says missions
       cannot be approved.
     - backend/.../mission/package-info.java — drop "and arrives with feature
       05"; it has
       arrived.

     Verification

     # 1. Unit, slice and module-boundary tests (no Docker socket needed inside 
     the container)
     docker compose run --rm backend ./mvnw test

     # 2. Integration tests, on the host - Testcontainers needs a Docker daemon
     cd backend && JAVA_HOME="C:/Users/61449/.jdks/temurin-21.0.12" ./mvnw
     verify

     # 3. Bring the stack up and regenerate the committed client
     docker compose up --build --watch
     docker compose exec -e OPENAPI_URL=http://backend:8080/v3/api-docs frontend
     npm run generate:api
     docker compose cp frontend:/app/src/api/generated
     ./frontend/src/api/generated

     # 4. Frontend
     docker compose exec frontend npm run typecheck
     docker compose exec frontend npm run lint
     docker compose exec frontend npm test

     ModularityTests runs inside step 1 and is what proves nothing leaked out of
     mission.internal.

     End to end by hand, with the seeded accounts (password Password123!):

     1. As marcus.reyes@orbitaldynamics.example (lead), open Aurora Survey
        (PLAN). Submit is
        visible but disabled until a requirement exists — add one, then submit.
        Status becomes
        Pending approval.
     2. As vera.lindholm@orbitaldynamics.example (director), the board shows an
        Awaiting approval
        section containing it. Open it and Reject with a comment.
     3. Back as Marcus: the mission reads Rejected, the history accordion is
        expanded and shows the
        reason. Return to plan, edit, resubmit.
     4. As Vera, Approve. Status becomes Approved and the history holds two
        cycles, newest first.
        Start mission is visible and disabled — correct; staffing arrives in
        feature 07.
     5. As tomas.eriksen@heliosaero.example (director, other organisation), GET 
        /api/missions/{id}
        for that mission returns 404, not 403.
     6. Open the seeded Tethys Relay, which has no approval row. A director can
        still approve it,
        and the history then shows the one synthesised cycle.

     Step ordering

     1. Liquibase changeset, ApprovalDecision + converter,
        MissionApprovalEntity, repository.
        Run ./mvnw verify — ddl-auto: validate proves the mapping matches the
        schema before any
        behaviour is written on top of it.
     2. Extract MissionDetailAssembler and confirm the existing suite is still
        green. A pure
        refactor, landed on its own so nothing later gets confused with it.
     3. MissionEntity mutators, MissionAccess.requireIsDirector,
        MissionApprovalService, the new
        exception, with unit tests alongside.
     4. Controller methods, request/response records, springdoc annotations.
        Slice tests.
     5. CANCELLED-on-close in MissionService.
     6. MissionApprovalIT, including the concurrency test; revisit
        MissionLifecycleIT.
     7. Regenerate the API client — both commands.
     8. Frontend, in dependency order: permissions.ts → missionLabels.ts →
        MissionsPage →
        the two dialogs and ApprovalHistory → MissionDetailPage →
        CloseMissionDialog →
        problemDetail.ts, with tests.
     9. Documentation.

❯ For unpaginated approval list, assume there will be a small size for now, but 
  document that. For the seeded PENDING_APPROVAL tasks with no approval rows,   
  add seeded data to make them valid.                                           

● Agent "Design backend approval implementation" finished · 11m 21s

● The design agent came back with substantive corrections, and you've changed
  two decisions. Let me fold all of it in.

  Key correction from the review — my locking plan had a real hole:
  MissionService.close doesn't lock, and its flush issues UPDATE mission SET 
  status=6 WHERE id=? with no status predicate. So a close racing an approve
  blocks on the lock and then overwrites it, leaving an APPROVED decision row
  next to a CLOSED mission. The lock has to be taken by every command that
  changes mission status, not just the four new ones.

  Let me update the plan, then build.

  Ran 5 shell commands (ctrl+o to expand)

● Plan is updated. Now implementing — starting with the schema. First, let me
  confirm the seed UUID conventions.

  Ran 1 shell command (ctrl+o to expand)

● a6…/b6… are free for approvals. Now the schema changeset:

  Ran 1 shell command (ctrl+o to expand)

● Now the seed changeset:

  Ran 1 shell command (ctrl+o to expand)

● Now the enum and converter:

  Ran 1 shell command (ctrl+o to expand)

● Now the entity and repository:

  Ran 1 shell command (ctrl+o to expand)

● Now the refactor step. Let me look at the two files I need to rewire:

  Ran 2 shell commands (ctrl+o to expand)

● Now the refactor: locking finder, MissionLoader, MissionDetailAssembler, and
  the requireIsOwner overload.

  Ran 5 shell commands (ctrl+o to expand)

● Now rewire MissionService onto the loader and assembler:

  Ran 1 shell command (ctrl+o to expand)

● Now remove the three methods that moved into the loader and assembler:

  Ran 2 shell commands (ctrl+o to expand)

● Now the MissionEntity mutators:

  Ran 2 shell commands (ctrl+o to expand)

● Now the response record, mapper overload, and the approval service:

  Ran 3 shell commands (ctrl+o to expand)

● Now rewire CrewRequirementService onto the loader/assembler, then add the
  controller:

  Ran 3 shell commands (ctrl+o to expand)
● Confirmed pre-existing dead code (I'll flag it, not delete it). Now the
  controller:

  Ran 3 shell commands (ctrl+o to expand)

● Bash's heredoc is choking on the apostrophes in the Javadoc. Falling back to
  the Write tool for this file:

● Write(src\main\java\com\missioncontrol\mission\internal\MissionApprovalControl
       ler.java)
  ⎿  Wrote 160 lines to src\main\java\com\missioncontrol\mission\internal\M
     issionApprovalController.java
       1 package com.missioncontrol.mission.internal;
       2 
       3 import io.swagger.v3.oas.annotations.Operation;
       4 import io.swagger.v3.oas.annotations.media.Content;
       5 import io.swagger.v3.oas.annotations.media.Schema;
       6 import io.swagger.v3.oas.annotations.responses.ApiResponse;
       7 import io.swagger.v3.oas.annotations.tags.Tag;
       8 import jakarta.validation.Valid;
       9 import java.util.List;
      10 import java.util.UUID;
     … +228 lines (ctrl+o to expand)

● Now let me compile to catch problems early.

  Ran 1 shell command (ctrl+o to expand)

● Compiles clean. Now the existing tests whose constructors changed:

  Ran 4 shell commands (ctrl+o to expand)

● The loader now locks before fetching, so both tests need the lock finder
  stubbed:

  Ran 3 shell commands (ctrl+o to expand)

● Refactor is green. Now the new tests — enum first:

  Ran 2 shell commands (ctrl+o to expand)

● Now the approval service unit test:

● Write(src\test\java\com\missioncontrol\mission\internal\MissionApprovalService
       Test.java)
  ⎿  Wrote 495 lines to src\test\java\com\missioncontrol\mission\internal\M
     issionApprovalServiceTest.java
       1 package com.missioncontrol.mission.internal;
       2 
       3 import static org.assertj.core.api.Assertions.assertThat;
       4 import static org.assertj.core.api.Assertions.assertThatThrownBy
         ;
       6 import static org.mockito.ArgumentMatchers.anyCollection;
       7 import static org.mockito.Mockito.lenient;
       8 import static org.mockito.Mockito.never;
       9 import static org.mockito.Mockito.verify;
      10 import static org.mockito.Mockito.when;
     … +605 lines (ctrl+o to expand)

  Ran 1 shell command (ctrl+o to expand)

● Now the CANCELLED-on-close unit coverage in MissionServiceTest:

  Ran 2 shell commands (ctrl+o to expand)

● Green. Now the two controller slice tests — let me check the existing
  patterns:

  Ran 1 shell command (ctrl+o to expand)

● Write(src\test\java\com\missioncontrol\mission\internal\MissionApprovalControl
       lerApiTest.java)
  ⎿  Wrote 252 lines to src\test\java\com\missioncontrol\mission\internal\M
     issionApprovalControllerApiTest.java
       1 package com.missioncontrol.mission.internal;
       3 import static org.mockito.ArgumentMatchers.any;
       4 import static org.mockito.ArgumentMatchers.eq;
       5 import static org.mockito.Mockito.verify;
       7 import static org.springframework.test.web.servlet.request.MockM
         vcRequestBuilders.get;
         vcRequestBuilders.post;
       9 import static org.springframework.test.web.servlet.result.MockMv
         cResultMatchers.content;
         cResultMatchers.jsonPath;
     … +322 lines (ctrl+o to expand)

● Write(src\test\java\com\missioncontrol\mission\internal\MissionApprovalRoleApi
       Test.java)
  ⎿  Wrote 177 lines to src\test\java\com\missioncontrol\mission\internal\M
     issionApprovalRoleApiTest.java
       1 package com.missioncontrol.mission.internal;
       2 
       4 import static org.mockito.ArgumentMatchers.eq;
       5 import static org.mockito.Mockito.verify;
       6 import static org.mockito.Mockito.verifyNoInteractions;
       8 import static org.springframework.test.web.servlet.request.MockM
         vcRequestBuilders.post;
       9 import static org.springframework.test.web.servlet.result.MockMv
      10 import static org.springframework.test.web.servlet.result.MockMv
         cResultMatchers.jsonPath;
     … +204 lines (ctrl+o to expand)

  Ran 4 shell commands (ctrl+o to expand)

● My new test classes never ran — the container image has source baked in at
  build time, so docker compose run executed stale code. Let me check and
  rebuild.

  Ran 1 shell command (ctrl+o to expand)
● Confirmed — source is baked into the image. Rebuilding before running:

  Ran 1 shell command (ctrl+o to expand)

● Those platform types are package-private (which is why RoleEnforcementApiTest
  lives in platform). Rewriting the role test to use method security directly
  instead:

● Write(src\test\java\com\missioncontrol\mission\internal\MissionApprovalRoleApi
       Test.java)
  ⎿  Added 48 lines, removed 72 lines
      11  import static org.springframework.test.web.servlet.result.MockM
          vcResultMatchers.status;
      12  
      13  import com.missioncontrol.platform.GlobalExceptionHandler;
      14 -import com.missioncontrol.platform.JwtClaims;                  
      15 -import com.missioncontrol.platform.JwtSecurityConfig;          
      16 -import com.missioncontrol.platform.ProblemAccessDeniedHandler; 
      17 -import com.missioncontrol.platform.ProblemAuthenticationEntryPo
         -int;                                                           
      18 -import com.missioncontrol.platform.ProblemResponseWriter;      
      19 -import com.missioncontrol.platform.SecurityConfig;             
      20 -import com.missioncontrol.platform.SecurityContextCurrentUser; 
      21 -import com.missioncontrol.shared.UserRole;                     
      14  import java.time.Instant;
      15  import java.util.List;
      16  import java.util.UUID;
      17  import org.junit.jupiter.api.DisplayName;
      18  import org.junit.jupiter.api.Test;
      27 -import org.mockito.Mockito;                                    
      19  import org.springframework.beans.factory.annotation.Autowired;
      20 +import org.springframework.boot.test.autoconfigure.web.servlet.
         +AutoConfigureMockMvc;                                          
      21  import org.springframework.boot.test.autoconfigure.web.servlet.
          WebMvcTest;
      22 +import org.springframework.boot.test.context.TestConfiguration;
      23  import org.springframework.context.annotation.Import;
      24  import org.springframework.http.MediaType;
      32 -import org.springframework.security.oauth2.jwt.Jwt;            
      33 -import org.springframework.security.oauth2.jwt.JwtDecoder;     
      25 +import org.springframework.security.config.annotation.method.co
         +nfiguration.EnableMethodSecurity;                              
      26 +import org.springframework.security.test.context.support.WithMo
         +ckUser;                                                        
      27  import org.springframework.test.context.ActiveProfiles;
      28  import org.springframework.test.context.bean.override.mockito.M
          ockitoBean;
      29  import org.springframework.test.web.servlet.MockMvc;
     ...
      31  /**
      32   * BR-3, at the only layer that can see it.
      33   *
      41 - * <p>{@code MissionApprovalControllerApiTest} runs with {@code
         - addFilters = false}, so                                       
      42 - * {@code PreAuthorize} is simply not evaluated there. This one
         - imports the real filter chain, which                          
      43 - * is what makes 'only a director may approve' a tested rule ra
         -ther than an annotation nobody has                             
      44 - * watched work.                                               
      34 + * <p>{@code MissionApprovalControllerApiTest} runs with the fi
         +lters off, so {@code PreAuthorize} is                          
      35 + * never evaluated there. This one turns method security on, wh
         +ich is what makes 'only a director                             
      36 + * may approve' a tested rule rather than an annotation nobody 
         +has watched work.                                              
      37   *
      46 - * <p>The last two cases are the ones worth having. Submit and 
         -replan deliberately carry <em>no</em>                          
      47 - * role annotation - ownership needs the mission in hand - and 
         -a helpful future addition of                                   
      48 - * {@code hasRole('MISSION_LEAD')} would turn the 404 a non-own
         -ing lead is supposed to get into a                             
      49 - * 403 that confirms the mission exists. Asserting that those t
         -wo reach the service is how that                               
      50 - * stays true.                                                 
      38 + * <p>Roles are set straight onto the security context with {@c
         +ode WithMockUser} rather than by                               
      39 + * minting a token and running the filter chain. Method securit
         +y reads the context, not the request,                          
      40 + * so the token path adds nothing here - and it is already cove
         +red for the application as a whole by                          
      41 + * {@code RoleEnforcementApiTest} (that the {@code role} claim 
         +becomes a {@code ROLE_} authority)                             
      42 + * and {@code SecurityFilterChainApiTest} (that an unauthentica
         +ted call is a 401). Those two live in                          
      43 + * {@code platform} because the beans involved are package-priv
         +ate there.                                                     
      44 + *                                                             
      45 + * <p>The last two cases are the ones most worth having. Submit
         + and replan deliberately carry                                 
      46 + * <em>no</em> role annotation, because ownership needs the mis
         +sion in hand; a helpful future                                 
      47 + * addition of {@code hasRole('MISSION_LEAD')} would turn the 4
         +04 a non-owning lead is supposed to                            
      48 + * get into a 403 that confirms the mission exists. Asserting t
         +hat both reach the service is how                              
      49 + * that stays true.                                            
      50   */
      51  @WebMvcTest(controllers = MissionApprovalController.class)
      53 -@Import({SecurityConfig.class, JwtSecurityConfig.class, Problem
         -AuthenticationEntryPoint.class,                                
      54 -        ProblemAccessDeniedHandler.class, ProblemResponseWriter
         -.class,                                                        
      55 -        SecurityContextCurrentUser.class, GlobalExceptionHandle
         -r.class})                                                      
      52 +@AutoConfigureMockMvc(addFilters = false)                      
      53 +@Import({GlobalExceptionHandler.class, MissionApprovalRoleApiTe
         +st.MethodSecurity.class})                                      
      54  @ActiveProfiles("test")
      55  class MissionApprovalRoleApiTest {
      56  
      57 +    /** {@code SecurityConfig} carries this in the application;
         + the slice has to opt in. */                                   
      58 +    @TestConfiguration                                         
      59 +    @EnableMethodSecurity                                      
      60 +    static class MethodSecurity {                              
      61 +    }                                                          
      62 +                                                               
      63      private static final UUID MISSION = UUID.fromString("a40000
          00-0000-0000-0000-000000000001");
      64      private static final UUID LEAD = UUID.fromString("a1000000-
          0000-0000-0000-000000000002");
      65  
      66 +    private static final String PROBLEM_JSON = "application/pro
         +blem+json";                                                    
      67 +                                                               
      68      @Autowired private MockMvc mockMvc;
      69  
      64 -    @MockitoBean private JwtDecoder jwtDecoder;                
      70      @MockitoBean private MissionApprovalService approvals;
      71  
      67 -    private String tokenFor(UserRole role) {                   
      68 -        String value = role.name().toLowerCase();              
      69 -        Jwt jwt = Jwt.withTokenValue(value)                    
      70 -                .header("alg", "HS256")                        
      71 -                .subject(LEAD.toString())                      
      72 -                .claim(JwtClaims.ORGANISATION_ID, UUID.randomUU
         -ID().toString())                                               
      73 -                .claim(JwtClaims.ROLE, role.name())            
      74 -                .issuedAt(Instant.now())                       
      75 -                .expiresAt(Instant.now().plusSeconds(3600))    
      76 -                .build();                                      
      77 -        Mockito.when(jwtDecoder.decode(value)).thenReturn(jwt);
      78 -        return "Bearer " + value;                              
      79 -    }                                                          
      80 -                                                               
      72      private static MissionResponse mission(MissionStatus status
          ) {
      73          return new MissionResponse(MISSION, "Aurora Survey", nu
          ll, status, null, null,
      74                  Instant.parse("2026-09-01T08:00:00Z"), Instant.
          parse("2026-09-14T17:00:00Z"),
     ...
       76      }
       77  
       78      @Test
       79 +    @WithMockUser(roles = "DIRECTOR")                         
       80      @DisplayName("A director may approve and reject")
       81      void directorMayDecide() throws Exception {
       90 -        String director = tokenFor(UserRole.DIRECTOR);        
       82          when(approvals.approve(eq(MISSION), any())).thenReturn
           (mission(MissionStatus.APPROVED));
       83          when(approvals.reject(eq(MISSION), any())).thenReturn(
           mission(MissionStatus.REJECTED));
       84  
       94 -        mockMvc.perform(post("/api/missions/{id}/approve", MIS
          -SION)                                                         
       95 -                        .header("Authorization", director))   
       85 +        mockMvc.perform(post("/api/missions/{id}/approve", MIS
          +SION))                                                        
       86                  .andExpect(status().isOk());
       87  
       88          mockMvc.perform(post("/api/missions/{id}/reject", MISS
           ION)
       99 -                        .header("Authorization", director)    
       89                          .contentType(MediaType.APPLICATION_JSO
           N)
       90                          .content("{\"comment\":\"Not yet.\"}")
           )
       91                  .andExpect(status().isOk());
       92      }
       93  
       94      @Test
      106 -    @DisplayName("A mission lead attempting to approve is refu
          -sed before the service is reached")                           
      107 -    void missionLeadMayNotApprove() throws Exception {        
      108 -        String lead = tokenFor(UserRole.MISSION_LEAD);        
      109 -                                                              
      110 -        mockMvc.perform(post("/api/missions/{id}/approve", MIS
          -SION)                                                         
      111 -                        .header("Authorization", lead))       
       95 +    @WithMockUser(roles = "MISSION_LEAD")                     
       96 +    @DisplayName("A mission lead attempting to decide is refus
          +ed before the service is reached")                            
       97 +    void missionLeadMayNotDecide() throws Exception {         
       98 +        mockMvc.perform(post("/api/missions/{id}/approve", MIS
          +SION))                                                        
       99                  .andExpect(status().isForbidden())
      113 -                .andExpect(content().contentTypeCompatibleWith
          -("application/problem+json"))                                 
      100 +                .andExpect(content().contentTypeCompatibleWith
          +(PROBLEM_JSON))                                               
      101                  .andExpect(jsonPath("$.type").value("urn:missi
           on-control:forbidden"));
      102  
      103          mockMvc.perform(post("/api/missions/{id}/reject", MISS
           ION)
      117 -                        .header("Authorization", lead)        
      104                          .contentType(MediaType.APPLICATION_JSO
           N)
      105                          .content("{\"comment\":\"Not yet.\"}")
           )
      120 -                .andExpect(status().isForbidden());           
      106 +                .andExpect(status().isForbidden())            
      107 +                .andExpect(jsonPath("$.type").value("urn:missi
          +on-control:forbidden"));                                      
      108  
      109          verifyNoInteractions(approvals);
      110      }
      111  
      112      @Test
      113 +    @WithMockUser(roles = "CREW_MEMBER")                      
      114      @DisplayName("A crew member may not decide either")
      127 -    void crewMemberMayNotApprove() throws Exception {         
      128 -        mockMvc.perform(post("/api/missions/{id}/approve", MIS
          -SION)                                                         
      129 -                        .header("Authorization", tokenFor(User
          -Role.CREW_MEMBER)))                                           
      115 +    void crewMemberMayNotDecide() throws Exception {          
      116 +        mockMvc.perform(post("/api/missions/{id}/approve", MIS
          +SION))                                                        
      117                  .andExpect(status().isForbidden())
      118                  .andExpect(jsonPath("$.type").value("urn:missi
           on-control:forbidden"));
      119  
     ...
      121      }
      122  
      123      @Test
      124 +    @WithMockUser(roles = "MISSION_LEAD")                     
      125      @DisplayName("Submit and replan carry no role check - owne
           rship is the service's to decide")
      126      void submitAndReplanReachTheService() throws Exception {
      139 -        String lead = tokenFor(UserRole.MISSION_LEAD);        
      127          when(approvals.submit(MISSION)).thenReturn(mission(Mis
           sionStatus.PENDING_APPROVAL));
      128          when(approvals.replan(MISSION)).thenReturn(mission(Mis
           sionStatus.PLAN));
      129  
      143 -        mockMvc.perform(post("/api/missions/{id}/submit", MISS
          -ION)                                                          
      144 -                        .header("Authorization", lead))       
      145 -                .andExpect(status().isOk());                  
      146 -        mockMvc.perform(post("/api/missions/{id}/replan", MISS
          -ION)                                                          
      147 -                        .header("Authorization", lead))       
      148 -                .andExpect(status().isOk());                  
      130 +        mockMvc.perform(post("/api/missions/{id}/submit", MISS
          +ION)).andExpect(status().isOk());                             
      131 +        mockMvc.perform(post("/api/missions/{id}/replan", MISS
          +ION)).andExpect(status().isOk());                             
      132  
      133          verify(approvals).submit(MISSION);
      134          verify(approvals).replan(MISSION);
      135      }
      136  
      137      @Test
      138 +    @WithMockUser(roles = "DIRECTOR")                         
      139      @DisplayName("A director is not blocked from submit either
            - the service answers 403 there")
      140      void directorReachesSubmit() throws Exception {
      141          when(approvals.submit(MISSION)).thenThrow(
      142                  new MissionForbiddenException("Only the missio
           n lead who owns this mission can "
      143                          + "submit this mission for approval.")
           );
      144  
      161 -        mockMvc.perform(post("/api/missions/{id}/submit", MISS
          -ION)                                                          
      162 -                        .header("Authorization", tokenFor(User
          -Role.DIRECTOR)))                                              
      145 +        mockMvc.perform(post("/api/missions/{id}/submit", MISS
          +ION))                                                         
      146                  .andExpect(status().isForbidden())
      147                  .andExpect(jsonPath("$.type").value("urn:missi
           on-control:forbidden"));
      148  
      166 -        // The point: the refusal came from the service, havin
          -g loaded the mission, not from an                             
      167 -        // annotation that never looked at it.                
      149 +        // The point: the refusal came from the service, havin
          +g loaded the mission, rather than from                        
      150 +        // an annotation that never looked at it.             
      151          verify(approvals).submit(MISSION);
      152      }
      170 -                                                              
      171 -    @Test                                                     
      172 -    @DisplayName("No token is a 401, not a 403")              
      173 -    void unauthenticated() throws Exception {                 
      174 -        mockMvc.perform(post("/api/missions/{id}/approve", MIS
          -SION))                                                        
      175 -                .andExpect(status().isUnauthorized());        
      176 -    }                                                         
      153  }

  Ran 1 shell command (ctrl+o to expand)

● Progress — 273 tests now run. Let me see the actual failures:

  Ran 1 shell command (ctrl+o to expand)

● My test caught a real bug: canTransitionTo(PLAN) is also true from APPROVED
  and ACTIVE — those are M5's edit arrows. So replan would have silently
  discarded an approval. Fixing:

  Ran 5 shell commands (ctrl+o to expand)

● All 277 green, ModularityTests included. Now the integration tests — let me
  check the harness:

  Ran 2 shell commands (ctrl+o to expand)

● Write(src\test\java\com\missioncontrol\mission\MissionApprovalIT.java)
  ⎿  Wrote 399 lines to
     src\test\java\com\missioncontrol\mission\MissionApprovalIT.java
       1 package com.missioncontrol.mission;
       3 import static org.assertj.core.api.Assertions.assertThat;
       4 import static org.springframework.test.web.servlet.request.MockM
       5 import static org.springframework.test.web.servlet.request.MockM
         vcRequestBuilders.patch;
         vcRequestBuilders.post;
       7 import static org.springframework.test.web.servlet.result.MockMv
       8 import static org.springframework.test.web.servlet.result.MockMv
         cResultMatchers.status;
      10 import com.fasterxml.jackson.databind.JsonNode;
     … +531 lines (ctrl+o to expand)

● Now the repository and concurrency ITs:

● Write(src\test\java\com\missioncontrol\mission\internal\MissionApprovalReposit
       oryIT.java)
  ⎿  Wrote 162 lines to src\test\java\com\missioncontrol\mission\internal\M
     issionApprovalRepositoryIT.java
       1 package com.missioncontrol.mission.internal;
       2 
       3 import static org.assertj.core.api.Assertions.assertThat;
       4 import static org.assertj.core.api.Assertions.assertThatThrownBy
         ;
       6 import com.missioncontrol.support.AbstractIntegrationTest;
       7 import java.time.Instant;
       8 import java.util.List;
       9 import java.util.UUID;
      10 import org.junit.jupiter.api.DisplayName;
     … +206 lines (ctrl+o to expand)

● Write(src\test\java\com\missioncontrol\mission\MissionApprovalConcurrencyIT.ja
       va)
  ⎿  Wrote 245 lines to src\test\java\com\missioncontrol\mission\MissionApp
     rovalConcurrencyIT.java
       1 package com.missioncontrol.mission;
       2 
       3 import static org.assertj.core.api.Assertions.assertThat;
         vcRequestBuilders.post;
       5 import static org.springframework.test.web.servlet.result.MockMv
         cResultMatchers.status;
       6 
       8 import com.missioncontrol.support.AbstractIntegrationTest;
       9 import java.util.ArrayList;
      10 import java.util.List;
     … +283 lines (ctrl+o to expand)

● Now update MissionLifecycleIT's stale header and run the integration suite on
  the host:

  Ran 2 shell commands (ctrl+o to expand)

● Backend complete: 277 unit/slice + 120 integration tests green, including the
  concurrency race against real Postgres. Now regenerating the API client:

  Ran 2 shell commands (ctrl+o to expand)
  ⎿  Invalid tool parameters

  Ran 1 shell command (ctrl+o to expand)

● Backend is up with the new endpoints. Regenerating the committed client:

  Ran 2 shell commands (ctrl+o to expand)

● There's the nesting trap — cp created generated/generated/. Fixing:

  Ran 3 shell commands (ctrl+o to expand)
● Generated types are exactly right. Now the frontend — permissions and labels
  first:

  Ran 3 shell commands (ctrl+o to expand)

● Now the new components. The two decision dialogs:

● Write(C:\Projects\Mission 
       Control\frontend\src\components\missions\ApproveMissionDialog.tsx)
  ⎿  Wrote 100 lines to
     ..\frontend\src\components\missions\ApproveMissionDialog.tsx
       1 import Alert from '@mui/material/Alert';
       2 import Button from '@mui/material/Button';
       3 import Dialog from '@mui/material/Dialog';
       4 import DialogActions from '@mui/material/DialogActions';
       5 import DialogContent from '@mui/material/DialogContent';
       7 import DialogTitle from '@mui/material/DialogTitle';
       8 import Stack from '@mui/material/Stack';
       9 import TextField from '@mui/material/TextField';
      10 import { useMutation, useQueryClient } from '@tanstack/react-que
         ry';
     … +105 lines (ctrl+o to expand)

● Write(C:\Projects\Mission 
       Control\frontend\src\components\missions\RejectMissionDialog.tsx)
  ⎿  Wrote 105 lines to
     ..\frontend\src\components\missions\RejectMissionDialog.tsx
       1 import Alert from '@mui/material/Alert';
       2 import Button from '@mui/material/Button';
       3 import Dialog from '@mui/material/Dialog';
       4 import DialogActions from '@mui/material/DialogActions';
       5 import DialogContent from '@mui/material/DialogContent';
       7 import DialogTitle from '@mui/material/DialogTitle';
       8 import Stack from '@mui/material/Stack';
       9 import TextField from '@mui/material/TextField';
      10 import { useMutation, useQueryClient } from '@tanstack/react-que
         ry';
     … +112 lines (ctrl+o to expand)

● Now the approval history component — the first styled() in the codebase, per
  your decision:

● Write(C:\Projects\Mission 
       Control\frontend\src\components\missions\ApprovalHistory.tsx)
  ⎿  Wrote 142 lines to
     ..\frontend\src\components\missions\ApprovalHistory.tsx
       1 import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
       2 import Accordion from '@mui/material/Accordion';
       3 import AccordionDetails from '@mui/material/AccordionDetails';
       4 import AccordionSummary from '@mui/material/AccordionSummary';
       5 import Chip from '@mui/material/Chip';
       7 import Typography from '@mui/material/Typography';
       8 import { styled } from '@mui/material/styles';
       9 import { useQuery } from '@tanstack/react-query';
      10 import { listMissionApprovalsOptions } from '../../api/generated
         /@tanstack/react-query.gen';
     … +158 lines (ctrl+o to expand)

● Now wire the buttons into MissionDetailPage and extend CloseMissionDialog:

  Ran 2 shell commands (ctrl+o to expand)

● Typecheck passed — but I must verify the container actually has my new files
  (no --watch running):

  Ran 1 shell command (ctrl+o to expand)

● Confirmed stale — that typecheck was meaningless. Rebuilding the frontend
  image (same source-baking issue as the backend):

  Ran 3 shell commands (ctrl+o to expand)

● Lint clean. One expected failure — a director now has four sections, not
  three:

  Ran 2 shell commands (ctrl+o to expand)

● Now the new frontend tests — permissions, labels, and the approval UI:

  Ran 5 shell commands (ctrl+o to expand)

● Now the detail-page tests. Let me check the existing harness there:

  Ran 2 shell commands (ctrl+o to expand)

● The heredoc broke on that large block. Let me do it in two safer steps — small
  patch, then a dedicated new test file:

  Ran 1 shell command (ctrl+o to expand)

● Write(C:\Projects\Mission 
       Control\frontend\src\pages\__tests__\MissionApprovalActions.test.tsx)
  ⎿  Wrote 416 lines to
     ..\frontend\src\pages\__tests__\MissionApprovalActions.test.tsx
       1 import { ThemeProvider } from '@mui/material/styles';
       2 import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
         zationProvider';
       4 import { QueryClient, QueryClientProvider } from '@tanstack/reac
         t-query';
         rary/react';
       6 import { MemoryRouter, Route, Routes } from 'react-router';
       7 import { beforeEach, describe, expect, it, vi } from 'vitest';
       9   approveMission,
      10   getMission,
     … +503 lines (ctrl+o to expand)

  Ran 1 shell command (ctrl+o to expand)

● One assertion issue — MUI keeps collapsed content mounted, so let me assert on
  the accordion's expanded state instead (which is also a stronger test):

  Ran 2 shell commands (ctrl+o to expand)

● The MUI warning reveals a real bug in my component, not just the test:
  defaultExpanded is evaluated on first render, while the query is still pending
  — so it would never auto-open in the real app either. Making it controlled:

  Ran 1 shell command (ctrl+o to expand)

● Component is right now; the test resolved the element before the query
  settled. Using waitFor:
  Ran 1 shell command (ctrl+o to expand)

● All 135 frontend tests pass and the MUI warning is gone. Let me confirm
  typecheck and lint are still clean:

  Ran 1 shell command (ctrl+o to expand)

● Everything green. Now the documentation. Feature spec first:

  Ran 2 shell commands (ctrl+o to expand)

● Update(C:\Projects\Mission Control\docs\features\05-mission-approval.md)
  ⎿  Added 15 lines, removed 15 lines
       98  
       99  ## Acceptance criteria
      100  
      101 -- [ ] An owning mission lead can submit a `PLAN` mission with 
          -at least one requirement.                                     
      102 -- [ ] Submitting a mission with no requirements is rejected wi
          -th 409.                                                       
      103 -- [ ] A non-owning mission lead cannot submit.                
      104 -- [ ] A director cannot submit a mission.                     
      105 -- [ ] Submitting a mission that is not in `PLAN` is rejected. 
      106 -- [ ] A director can approve a `PENDING_APPROVAL` mission.    
      107 -- [ ] A mission lead attempting to approve receives 403.      
      108 -- [ ] A director in another organisation receives 404.        
      109 -- [ ] Rejection without a comment is rejected with 400.       
      110 -- [ ] A rejected mission can be returned to `PLAN`, edited, an
          -d resubmitted.                                                
      111 -- [ ] A rejected mission can instead be closed with `closeReas
          -on` of `REJECTED`.                                            
      112 -- [ ] After submit → reject → plan → submit → approve, the his
          -tory holds two cycles in order.                               
      113 -- [ ] At no point does a mission have two `PENDING` approvals.
      114 -- [ ] Approving an already-approved mission is rejected with 4
          -09.                                                           
      115 -- [ ] Two concurrent approvals on one mission result in exactl
          -y one success and one 409.                                    
      101 +- [x] An owning mission lead can submit a `PLAN` mission with 
          +at least one requirement.                                     
      102 +- [x] Submitting a mission with no requirements is rejected wi
          +th 409.                                                       
      103 +- [x] A non-owning mission lead cannot submit. They receive **
          +404, not 403** — see the notes below.                         
      104 +- [x] A director cannot submit a mission.                     
      105 +- [x] Submitting a mission that is not in `PLAN` is rejected. 
      106 +- [x] A director can approve a `PENDING_APPROVAL` mission.    
      107 +- [x] A mission lead attempting to approve receives 403.      
      108 +- [x] A director in another organisation receives 404.        
      109 +- [x] Rejection without a comment is rejected with 400.       
      110 +- [x] A rejected mission can be returned to `PLAN`, edited, an
          +d resubmitted.                                                
      111 +- [x] A rejected mission can instead be closed with `closeReas
          +on` of `REJECTED`.                                            
      112 +- [x] After submit → reject → plan → submit → approve, the his
          +tory holds two cycles in order.                               
      113 +- [x] At no point does a mission have two `PENDING` approvals.
      114 +- [x] Approving an already-approved mission is rejected with 4
          +09.                                                           
      115 +- [x] Two concurrent approvals on one mission result in exactl
          +y one success and one 409.                                    
      116  
      117  ## Error handling
      118  

● Update(C:\Projects\Mission Control\docs\features\05-mission-approval.md)
  ⎿  Added 98 lines
      128  
      129  `urn:mission-control:invalid-transition` carries `currentStatu
           s` and `attemptedTransition`, so a
      130  client can tell a stale view from a genuine mistake.
      131 +                                                              
      132 +## Notes from the build                                       
      133 +                                                              
      134 +- **`POST /replan` cannot rely on the transition table.** `Mis
          +sionStatus.canTransitionTo(PLAN)` is                          
      135 +  true from `REJECTED`, `APPROVED` **and** `ACTIVE` — the last
          + two are M5, an edit discarding an                            
      136 +  approval. Three states share one arrow for two different rea
          +sons, so a `replan` guarded only by                           
      137 +  M3 would happily throw away a live approval with nobody havi
          +ng edited anything. The endpoint                              
      138 +  names `REJECTED` explicitly. A unit test over every other st
          +atus is what caught it.                                       
      139 +                                                              
      140 +- **Concurrency is a pessimistic row lock, and every command h
          +as to take it.** NFR-2 says the                               
      141 +  second caller "sees the status has already moved", which is 
          +exactly what `select ... for update`                          
      142 +  gives: the loser blocks, re-reads the committed status, and 
          +gets a 409 carrying it. `@Version`                            
      143 +  would instead surface a generic lock failure knowing nothing
          + about the new status, and would                              
      144 +  change every existing mission write for a guarantee only the
          +se endpoints need. The subtlety is                            
      145 +  that **a lock one side skips is not a lock** — dirty checkin
          +g writes `set status = ?` with no                             
      146 +  status predicate, so an unlocked `close` would block on the 
          +winner's lock and then overwrite it,                          
      147 +  leaving an approval record beside a mission that contradicts
          + it. So `update`, `start`, `close`                            
      148 +  and the requirement endpoints all load through the same lock
          +ing path.                                                     
      149 +                                                              
      150 +- **`ApprovalDecision` gained a fourth code, `CANCELLED`.** A 
          +mission can be closed while it is                             
      151 +  still awaiting a decision, which leaves a cycle nobody will 
          +ever decide. Left `PENDING` it reads                          
      152 +  on screen as still waiting for someone, and it holds M8's pa
          +rtial unique index against a                                  
      153 +  resubmission that can never come. `data-model.md` is updated
          +; the codes are append-only, so 4 is                          
      154 +  the only place it could go. The seam is a `MissionApprovals`
          + component both services use, so                              
      155 +  `MissionService.close` records the cancellation without eith
          +er service depending on the other.                            
      156 +                                                              
      157 +- **404 beats 403 for a non-owning lead**, as feature 04 estab
          +lished at length. The error table                             
      158 +  above says "caller is not the owner → 403", but a lead who c
          +annot *see* another lead's mission                            
      159 +  must not be told it exists. Visibility is checked first and 
          +answers 404; 403 is left for a caller                         
      160 +  who genuinely can see the mission but may not act — a direct
          +or attempting `submit` or `replan`.                           
      161 +                                                              
      162 +- **BR-3 is an annotation, not a service check.** `@PreAuthori
          +ze("hasRole('DIRECTOR')")` needs no                           
      163 +  mission in hand, and it does not weaken the tenant rules: a 
          +director from another organisation                            
      164 +  passes the role check and is then told the mission does not 
          +exist. Submit and replan carry no                             
      165 +  annotation, precisely so the 404 above survives.            
      166 +                                                              
      167 +- **The approvals list is not paged**, unlike every other list
          + in this API. FR-6 asks for every                             
      168 +  cycle, and the screen showing them needs the count before it
          + can render. That rests on an                                 
      169 +  assumption stated in three places rather than left implicit 
          +— a mission gains a cycle only when a                         
      170 +  plan is sent back and resubmitted, so the count stays in sin
          +gle digits. If that ever stops being                          
      171 +  true, the fix is the standard `page`/`size` envelope with th
          +e same newest-first order.                                    
      172 +                                                              
      173 +- **The seed gained approval rows.** Seeded missions already s
          +at in `PENDING_APPROVAL`, `APPROVED`                          
      174 +  and `REJECTED` from feature 04 with no cycles — a state `sub
          +mit` can never produce, and one where                         
      175 +  a director opening *Tethys Relay* would find nothing to deci
          +de. `004-seed-mission-approvals` gives                        
      176 +  every seeded mission past `PLAN` the history it must have ha
          +d. The three with none (two in `PLAN`,                        
      177 +  and *Io Survey*, aborted straight out of `PLAN`) are cases r
          +ather than omissions. Because the seed                        
      178 +  is now complete, `approve` and `reject` *require* an open cy
          +cle and treat its absence as a bug                            
      179 +  rather than reconstructing one.                             
      180 +                                                              
      181 +- **M12 finally has a home.** Feature 04 had to refuse an empt
          +y mission at `POST /start`, because                           
      182 +  the real check — at submission — had nowhere to live yet. Bo
          +th refusals stay: they catch the same                         
      183 +  hole at different depths.                                   
      184 +                                                              
      185 +- **`replan` is owner-only, which is narrower than M6.** The A
          +PI table says so and this follows it:                         
      186 +  having another go at a plan is planning work. The consequenc
          +e worth knowing is that a director has                        
      187 +  no way to unstick a rejected mission whose lead is unavailab
          +le — their lever is closing it as                             
      188 +  `REJECTED`. Note also the asymmetry a caller meets: a *direc
          +tor* calling `replan` gets 403 because                        
      189 +  they can see the mission, a *non-owning lead* gets 404 becau
          +se they cannot.                                               
      190 +                                                              
      191 +- **A missing request body was a 500, everywhere.** `POST /rej
          +ect` with no body is specified as a                           
      192 +  400 and was answering 500 — `HttpMessageNotReadableException
          +` had no handler. Pre-existing and                            
      193 +  reachable since feature 04 (`POST /api/missions` with an emp
          +ty body did the same), just never                             
      194 +  exercised. Fixed in `GlobalExceptionHandler`, which is one h
          +andler for every module.                                      
      195 +                                                              
      196 +- **`CrewRequirementRepository` is unused.** Noticed while rew
          +iring `CrewRequirementService`, which                         
      197 +  injected it and never called it — the service resolves requi
          +rements off the loaded mission                                
      198 +  instead. The dead injection is gone; the interface is left i
          +n place, since removing it is not this                        
      199 +  feature's business.                                         
      200 +                                                              
      201 +### UI                                                        
      202 +                                                              
      203 +The spec says nothing about the screens, so these were decisio
          +ns:                                                           
      204 +                                                              
      205 +- **A director gets a fourth board section, "Awaiting approval
          +"**, holding `PENDING_APPROVAL` and                           
      206 +  rendered only for them. FR-8 is reached through the existing
          + list rather than a new endpoint, but                         
      207 +  two missions awaiting a decision buried among everything els
          +e still in planning is how a queue                            
      208 +  goes unnoticed. The status is *moved* out of Draft rather th
          +an duplicated: a mission in two                               
      209 +  sections would be counted twice and paged independently in e
          +ach. A lead keeps the original three —                        
      210 +  every mission in their Draft section is theirs, and they kno
          +w which they submitted.                                       
      211 +- **The actions live on the mission detail page only**, in the
          + row beside Edit / Start / Close. A                           
      212 +  director has to open a mission — and so see its dates and re
          +quirements — before deciding it,                              
      213 +  which is the point of an approval gate.                     
      214 +- **Submit is shown disabled, with a reason, when a mission ha
          +s no requirements**, rather than                              
      215 +  hidden. The same treatment `Start` already gets on an uncrew
          +ed mission, and for the same reason:                          
      216 +  "why can I not submit this?" is the question the screen exis
          +ts to answer.                                                 
      217 +- **The rejection dialog will not submit without a comment.** 
          +The server enforces BR-6 regardless;                          
      218 +  this only avoids a round trip that was always going to fail,
          + and makes the requirement visible                            
      219 +  before the click rather than after it.                      
      220 +- **The approval history is a collapsed accordion** below the 
          +requirements, which opens itself when                         
      221 +  the newest cycle is a rejection — that comment is the most a
          +ctionable thing on a rejected mission.                        
      222 +  It is a *controlled* accordion: `defaultExpanded` is read on
          + the first render, while the query is                         
      223 +  still pending and there is no rejection to see, so it never 
          +fired. MUI warns about exactly this.                          
      224 +- **Closing now offers `Rejected`** as a reason, but only for 
          +a mission that really was rejected,                           
      225 +  which is the one case the server accepts it in. That is FR-5
          +.                                                             
      226 +- **`styled()` appears for the first time**, for the two multi
          +-override pieces of the history                               
      227 +  timeline, per the rule in `../CLAUDE.md`. The rest of the app u
          +ses `sx`; single-override cases here                          
      228 +  still do.                                                   

● Now data-model.md, architecture.md, and the package Javadoc:

  Ran 2 shell commands (ctrl+o to expand)

● Now a final full verification from a clean database, to prove the new
  migrations and seed apply from scratch:

  Ran 2 shell commands (ctrl+o to expand)

● The down -v also wiped the Maven cache, so it's re-downloading. Waiting:

  Ran 1 shell command (ctrl+o to expand)

● Backend started clean against a fresh database. Let me verify the migrations
  and seed landed correctly:

  Ran 2 shell commands (ctrl+o to expand)

● Seed is exactly right. Now an end-to-end walkthrough against the running app:

  Ran 2 shell commands (ctrl+o to expand)

● Every path behaves as specified. Let me restore the demo data I mutated, then
  check for a hazard I know about in Javadoc:

  Ran 2 shell commands (ctrl+o to expand)

● No bare quotes in Javadoc — that trap is avoided. Final full verification of
  both test tiers:

  Ran 4 shell commands (ctrl+o to expand)

● Feature 05 is implemented and verified end to end.

  What shipped

  Backend — a new mission_approval table plus MissionApprovalController with all
  five endpoints (submit, approve, reject, replan, approvals), a
  MissionApprovalService, and ApprovalDecision with the CANCELLED code you
  chose. Three collaborators came out of MissionService as a pure refactor first
  — MissionLoader, MissionDetailAssembler, MissionApprovals — which is what
  lets both services write to the approval ledger without depending on each
  other.

  Frontend — director-only "Awaiting approval" board section, the four lifecycle
  buttons on the mission detail page, approve/reject dialogs, a collapsible
  approval-history timeline, and Rejected added to the close dialog for FR-5.

  Tests — 277 unit/slice + 120 integration + 135 frontend, all green. The
  concurrency criterion is a real two-connection race against Postgres (3
  scenarios × 3 repeats).

  Three real bugs the work surfaced

  1. replan would have silently discarded a live approval.
     MissionStatus.canTransitionTo(PLAN) is true from REJECTED, APPROVED and
     ACTIVE — the last two are M5's edit arrow. Guarding only on M3 meant POST 
     /replan on an approved mission succeeded. It now names REJECTED explicitly;
     a parameterised unit test over every other status caught it.
  2. The lock had to cover every command, not just the four new ones. Dirty
     checking writes set status = ? with no status predicate, so an unlocked
     close would block on an approve's lock and then overwrite it — leaving an
     APPROVED decision row beside a CLOSED mission. update, start, close and the
     requirement endpoints now load through the same locking path. This came
     from the design review, not from me.
  3. A missing request body returned 500 everywhere. POST /reject with no body
     is specified as 400; HttpMessageNotReadableException had no handler.
     Pre-existing since feature 04 (POST /api/missions with an empty body did
     the same), just never exercised. Fixed in GlobalExceptionHandler.

  Your two changes, applied

  - Pagination — the approvals list stays unpaged, with the small-count
    assumption written down in three places (response record Javadoc, the
    endpoint's @Operation, and the spec's notes), each saying what would change
    if it stopped holding.
  - Seed data — 004-seed-mission-approvals.yaml gives every seeded mission past
    PLAN a consistent history. Because the seed is now complete, approve/reject
    require an open cycle rather than reconstructing a missing one, which
    dropped a branch. Verified from a wiped volume.

  Two things worth flagging

  - CrewRequirementRepository is dead code, and was before this feature —
    CrewRequirementService injected it and never called it. I removed the unused
    injection since I was rewriting that constructor anyway, but left the
    interface in place rather than widen scope. It's now referenced nowhere.
  - The CANCELLED-on-close cases live in MissionApprovalIT, not
    MissionLifecycleIT as planned — the behaviour is reachable over HTTP now, so
    testing it there is more honest. I did rewrite MissionLifecycleIT's header,
    which claimed it existed only because the approval endpoints didn't.

  docs/features/05-mission-approval.md has ticked criteria and a "Notes from the
  build" section covering the UI decisions and the three bugs; data-model.md,
  architecture.md and README.md are updated. Nothing is committed — the working
  tree is yours to review.

✻ Churned for 1h 0m 12s

※ recap: Feature 05 mission approval is fully implemented and verified: backend, frontend, docs, and all tests green (277 unit, 120 integration, 135 frontend). Nothing is committed yet, so the next step is reviewing the working tree and committing. (disable recaps in /config)

❯ You mentioned CrewRequirementRepository is dead code, why is this not used?   

● Good question — let me dig into the history rather than guess.

  Ran 3 shell commands (ctrl+o to expand)
● I traced it through the history and the code. Here's the answer.

  Why it isn't used

  CrewRequirement is not an aggregate root. Every read and write of a
  requirement goes through its Mission, so a repository of its own has nothing
  to do:

  ┌───────────┬──────────────────────────────────────────────────────────────┐
  │ Operation │             How it actually reaches the database             │
  ├───────────┼──────────────────────────────────────────────────────────────┤
  │           │ MissionRepository.findDetailByIdAndOrganisationId            │
  │ Read one  │ fetch-joins m.requirements and r.requiredSkills, so          │
  │           │ requireRequirement just filters the in-memory set            │
  │           │ (CrewRequirementService.java:132)                            │
  ├───────────┼──────────────────────────────────────────────────────────────┤
  │ Add       │ mission.addRequirement(...) + missions.save(mission),        │
  │           │ persisted by cascade = CascadeType.ALL                       │
  ├───────────┼──────────────────────────────────────────────────────────────┤
  │ Update    │ requirement.replaceWith(...) + mission.touch(...) — pure     │
  │           │ dirty checking, no save call at all                          │
  ├───────────┼──────────────────────────────────────────────────────────────┤
  │ Delete    │ mission.removeRequirement(...), persisted by orphanRemoval = │
  │           │  true (MissionEntity.java:94)                                │
  └───────────┴──────────────────────────────────────────────────────────────┘

  The decisive part is that loading the mission is unavoidable anyway. Before it
  can touch a requirement, requireEditableMission needs the mission entity for
  three separate checks — requireVisible (which decides 404 vs 403),
  requireIsOwner (BR-10), and status == PLAN. So the mission is always in hand,
  with its requirements already attached, by the time a requirement id needs
  resolving. A findOnMission call would be a second round trip for rows sitting
  in the persistence context.

  Why it exists at all

  Its Javadoc gives the original intent away:

  ▎ "The organisation is checked too, so this is tenant-scoped in its own right 
  ▎ rather than relying on the mission having been fetched first."

  That describes the alternative design — resolve the requirement directly by
  (id, missionId, organisationId) and skip loading the mission. The service then
  needed the mission regardless, for the authorisation and status checks, which
  made that lookup redundant. The interface and its constructor injection both
  survived the change.

  Nothing caught it because an injected-but-uncalled dependency compiles, wires
  and passes every test. Spring Data still builds the proxy and validates the
  JPQL against the entity model at startup, so findOnMission is parsed and
  verified on every boot — it is simply never invoked. There's no unused-bean
  warning to trip over.

  Will anything want it later?

  Very unlikely. It lives in mission.internal, so no other module can reach it —
  ModularityTests enforces that, and assignment stores crewRequirementId as a
  bare UUID with no FK. Within the module, the one query that spans requirements
  across missions (findRequirementTotals) already sits on MissionRepository as
  a JPQL projection.

● User answered Claude's questions:
  ⎿  · The interface is now referenced nowhere (I removed the unused injection 
     while rewiring the service). What would you like done with it? → Delete it 
     (Recommended)

● Deleting it. Writing the plan.

● Updated plan
  ⎿  Error writing file

● Plan mode exited and you've chosen deletion — executing.

  Ran 1 shell command (ctrl+o to expand)

● Update(C:\Projects\Mission Control\docs\features\05-mission-approval.md)
  ⎿  Added 10 lines, removed 4 lines
      193    reachable since feature 04 (`POST /api/missions` with an emp
           ty body did the same), just never
      194    exercised. Fixed in `GlobalExceptionHandler`, which is one h
           andler for every module.
      195  
      196 -- **`CrewRequirementRepository` is unused.** Noticed while rew
          -iring `CrewRequirementService`, which                         
      197 -  injected it and never called it — the service resolves requi
          -rements off the loaded mission                                
      198 -  instead. The dead injection is gone; the interface is left i
          -n place, since removing it is not this                        
      199 -  feature's business.                                         
      196 +- **`CrewRequirementRepository` is gone.** Noticed while rewir
          +ing `CrewRequirementService`, which                           
      197 +  injected it and never called it. The reason is structural ra
          +ther than an oversight in one method:                         
      198 +  `CrewRequirement` is not an aggregate root. Reads come off t
          +he mission that                                               
      199 +  `findDetailByIdAndOrganisationId` already fetch-joined; adds
          + cascade from `mission.save`; updates                         
      200 +  are dirty checking; deletes are `orphanRemoval`. And loading
          + the mission is unavoidable anyway,                           
      201 +  because visibility, ownership and the `PLAN` check all need 
          +it before a requirement id is                                 
      202 +  resolved — so the requirement is always in memory before any
          +thing could look it up. Its Javadoc                           
      203 +  described the alternative design it was written for, *tenant
          +-scoped in its own right rather than                          
      204 +  relying on the mission having been fetched first*, which is 
          +exactly the path the service does not                         
      205 +  take. Leaving it would have kept a false signal in the modul
          +e.                                                            
      206  
      207  ### UI
      208  

  Ran 2 shell commands (ctrl+o to expand)

● Deleted, and both tiers are unchanged: 277 unit/slice and 120 integration 
  tests, same counts as before — removing it took no coverage with it, because
  it had none.

  Why it was unused

  CrewRequirement is not an aggregate root. Every operation on one goes through
  its Mission:

  - Read — findDetailByIdAndOrganisationId fetch-joins m.requirements and
    r.requiredSkills, so requireRequirement filters an in-memory set
    (CrewRequirementService.java:132)
  - Add — mission.addRequirement(...) + missions.save(mission), via cascade = 
    ALL
  - Update — requirement.replaceWith(...) + mission.touch(...); pure dirty
    checking, no save call
  - Delete — mission.removeRequirement(...), via orphanRemoval = true

  The decisive part is that loading the mission is unavoidable anyway —
  requireEditableMission needs the entity for requireVisible (404 vs 403),
  requireIsOwner (BR-10) and the status == PLAN check, all before a requirement
  id is resolved. So the requirement is always in memory before anything could
  look it up; findOnMission would have been a second round trip for rows already
  in the persistence context.

  Its Javadoc named the design it was written for — "tenant-scoped in its own 
  right rather than relying on the mission having been fetched first" — which is
  exactly the path the service doesn't take. That reads like the original
  intent: resolve the requirement directly and skip the mission load. The
  service then needed the mission regardless, and the interface plus its
  injection both survived the change.

  Nothing caught it because an injected-but-uncalled dependency compiles, wires
  and passes every test. Spring Data still builds the proxy and validates the
  JPQL against the entity model at startup, so findOnMission was parsed and
  verified on every boot — just never invoked.

  What changed

  - Deleted backend/.../mission/internal/CrewRequirementRepository.java (via git
    rm, so it's staged as a deletion)
  - Corrected the note in docs/features/05-mission-approval.md, which said the
    interface was being left in place — it now records why it could go

  One caveat worth stating: the seam this closes is real but small. It was dead
  from the day it was written, so nothing about feature 05 caused it — the
  rewiring just made it visible.

✻ Cooked for 4m 40s
