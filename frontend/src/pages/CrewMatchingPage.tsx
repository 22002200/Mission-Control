import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import GroupAddIcon from '@mui/icons-material/GroupAdd';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  getMissionOptions,
  listMissionAssignmentsOptions,
  offerAssignmentMutation,
} from '../api/generated/@tanstack/react-query.gen';
import { matchAll, matchRequirement } from '../api/generated/sdk.gen';
import type {
  AssignmentResponse,
  CandidateResponse,
  MissionResponse,
  RequirementMatchResponse,
} from '../api/generated/types.gen';
import { canMatchCrew, canOfferCrew } from '../auth/permissions';
import { useAuth } from '../auth/useAuth';
import RequirementDraftCard from '../components/matching/RequirementDraftCard';
import MissionStatusChip from '../components/missions/MissionStatusChip';
import { STATUS_LABELS } from '../lib/missionLabels';
import { messageForProblem } from '../lib/problemDetail';

/**
 * Drafting a crew for one mission.
 *
 * Its own route rather than another section on the mission page: a draft board with a breakdown per
 * candidate is a workspace, and the mission page is already a summary of everything else. A route
 * is also linkable and survives a refresh, which is the reason this application has a router.
 *
 * **A draft is still client state; an offer is not.** Feature 06 could only suggest, and the page
 * said so. Feature 07 gives each drafted candidate an Offer button, and offering is the point at
 * which the crew member finds out: it creates a real assignment they can accept or decline. What is
 * pencilled in and what has been sent are drawn differently for that reason - a draft vanishes on
 * a refresh and an offer does not.
 *
 * Offering is the owning lead's alone - BR-9 - so a director sees the whole board, can run matches
 * on it, and gets no Offer buttons. Withdrawing somebody already offered is on the mission page,
 * not here: this screen is about choosing between candidates, and a person already committed is no
 * longer a candidate.
 *
 * The state worth understanding is `seen`. It accumulates every candidate shown for a requirement
 * across rematches, and it has to: without it a rematch would exclude only the shortlist currently
 * on screen, so the second rematch would hand back the first batch again and the list would cycle
 * between two pages forever. What goes to the server as `exclude` is that set plus everyone drafted
 * anywhere on the mission - both already held here in order to render them, so the client keeps no
 * separate rejection log.
 */
export default function CrewMatchingPage() {
  const { missionId = '' } = useParams();
  const { user } = useAuth();

  const [requirements, setRequirements] = useState<RequirementMatchResponse[]>([]);
  const [drafted, setDrafted] = useState<Record<string, CandidateResponse[]>>({});
  const [suggestions, setSuggestions] = useState<Record<string, CandidateResponse[]>>({});
  const [seen, setSeen] = useState<Record<string, string[]>>({});
  const [remaining, setRemaining] = useState<Record<string, number>>({});
  const [busyRequirement, setBusyRequirement] = useState<string | null>(null);
  const [offeringRequirement, setOfferingRequirement] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const queryClient = useQueryClient();

  const {
    data: mission,
    isPending,
    error,
  } = useQuery(getMissionOptions({ path: { id: missionId } }));

  // Who is already offered or accepted. Feature 06 assumed nobody, which was true then; now it has
  // to be asked, or the open-seat arithmetic on every line would be wrong the moment anyone offers.
  const { data: staffing } = useQuery(listMissionAssignmentsOptions({ path: { missionId } }));

  const offerPlace = useMutation(offerAssignmentMutation());

  const draftWholeMission = useMutation({
    mutationFn: async () => {
      const { data } = await matchAll({ path: { missionId }, throwOnError: true });
      return data;
    },
  });

  const draftOneRequirement = useMutation({
    mutationFn: async (input: { requirementId: string; exclude: string[] }) => {
      const { data } = await matchRequirement({
        path: { missionId, requirementId: input.requirementId },
        query: { limit: 3, exclude: input.exclude },
        throwOnError: true,
      });
      return data;
    },
  });

  if (isPending) {
    return <Typography color="text.secondary">Loading mission…</Typography>;
  }

  if (error || !mission) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Typography color="error.main" role="alert">
          {messageForProblem(error, 'Could not load this mission.')}
        </Typography>
        <Button component={Link} to="/missions" startIcon={<ArrowBackIcon />}>
          Back to missions
        </Button>
      </Stack>
    );
  }

  // The server refuses this too. Checking here means a lead who arrives by URL sees a sentence
  // rather than a raw 403.
  if (!canMatchCrew(user, mission)) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Typography color="error.main" role="alert">
          Only the mission lead who owns this mission, or a director, can suggest crew for it.
        </Typography>
        <Button component={Link} to={`/missions/${missionId}`} startIcon={<ArrowBackIcon />}>
          Back to the mission
        </Button>
      </Stack>
    );
  }

  /** Everyone the server should leave out: the whole draft, plus what this line has already shown. */
  function excludeFor(requirementId: string): string[] {
    const draftedAnywhere = Object.values(drafted)
      .flat()
      .map((candidate) => candidate.crewMemberId);
    return Array.from(new Set([...draftedAnywhere, ...(seen[requirementId] ?? [])]));
  }

  async function handleMatchAll() {
    setActionError(null);
    try {
      const result = await draftWholeMission.mutateAsync();
      setRequirements(result.requirements);
      // Match all fills seats directly - that is what drafting a crew means. Anything it returns
      // counts as seen, so a later rematch on that line offers alternatives rather than repeats.
      setDrafted(
        Object.fromEntries(result.requirements.map((r) => [r.requirementId, r.candidates])),
      );
      setSeen(
        Object.fromEntries(
          result.requirements.map((r) => [
            r.requirementId,
            r.candidates.map((c) => c.crewMemberId),
          ]),
        ),
      );
      setSuggestions({});
      setRemaining(
        Object.fromEntries(result.requirements.map((r) => [r.requirementId, r.remainingCount])),
      );
    } catch (caught) {
      setActionError(messageForProblem(caught, 'Could not draft a crew for this mission.'));
    }
  }

  async function handleMatchOne(requirementId: string) {
    setActionError(null);
    setBusyRequirement(requirementId);
    try {
      const result = await draftOneRequirement.mutateAsync({
        requirementId,
        exclude: excludeFor(requirementId),
      });

      setRequirements((current) =>
        current.some((r) => r.requirementId === requirementId)
          ? current.map((r) =>
              r.requirementId === requirementId ? { ...r, ...summary(result) } : r,
            )
          : [...current, result],
      );
      setSuggestions((current) => ({ ...current, [requirementId]: result.candidates }));
      setSeen((current) => ({
        ...current,
        [requirementId]: Array.from(
          new Set([
            ...(current[requirementId] ?? []),
            ...result.candidates.map((c) => c.crewMemberId),
          ]),
        ),
      }));
      setRemaining((current) => ({ ...current, [requirementId]: result.remainingCount }));
    } catch (caught) {
      setActionError(messageForProblem(caught, 'Could not find candidates for this requirement.'));
    } finally {
      setBusyRequirement(null);
    }
  }

  function handlePin(requirementId: string, candidate: CandidateResponse) {
    setDrafted((current) => ({
      ...current,
      [requirementId]: [...(current[requirementId] ?? []), candidate],
    }));
    setSuggestions((current) => ({
      ...current,
      [requirementId]: (current[requirementId] ?? []).filter(
        (other) => other.crewMemberId !== candidate.crewMemberId,
      ),
    }));
  }

  /**
   * Turns drafted candidates into real offers, one request each.
   *
   * Sequential rather than parallel, and that is deliberate. Invariant A2 caps a requirement at its
   * seat count, and the server counts under a row lock - so firing four offers at three seats
   * concurrently would give three arbitrary winners and one 409 with no way to say which. In order,
   * the lead can see exactly where it stopped.
   *
   * A failure stops the run rather than pressing on. The usual cause is the line filling up, and
   * every later offer would fail the same way.
   */
  async function offerEach(requirementId: string, candidates: CandidateResponse[]) {
    setActionError(null);
    setOfferingRequirement(requirementId);
    try {
      for (const candidate of candidates) {
        await offerPlace.mutateAsync({
          path: { missionId },
          body: { crewRequirementId: requirementId, crewMemberId: candidate.crewMemberId },
        });
        // Dropped from the draft as it lands, so a partial failure leaves the board showing
        // exactly who still has not been offered.
        setDrafted((current) => ({
          ...current,
          [requirementId]: (current[requirementId] ?? []).filter(
            (other) => other.crewMemberId !== candidate.crewMemberId,
          ),
        }));
      }
    } catch (caught) {
      setActionError(messageForProblem(caught, 'Could not offer that place.'));
    } finally {
      setOfferingRequirement(null);
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissionAssignments' }] });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'getMission' }] });
    }
  }

  function handleRemove(requirementId: string, candidate: CandidateResponse) {
    setDrafted((current) => ({
      ...current,
      [requirementId]: (current[requirementId] ?? []).filter(
        (other) => other.crewMemberId !== candidate.crewMemberId,
      ),
    }));
    // Back to the top of the suggestions rather than gone, so removing somebody is undoable. They
    // stay in `seen`, so a rematch will not offer them a second time.
    setSuggestions((current) => ({
      ...current,
      [requirementId]: [candidate, ...(current[requirementId] ?? [])],
    }));
  }

  const mayOffer = canOfferCrew(user, mission);

  /** Everyone already offered or accepted against one line, from the server rather than the draft. */
  function committedFor(requirementId: string): AssignmentResponse[] {
    return (
      staffing?.requirements
        .find((line) => line.requirementId === requirementId)
        ?.assignments.filter(
          (assignment) => assignment.status === 'OFFERED' || assignment.status === 'ACCEPTED',
        ) ?? []
    );
  }

  // Match responses carry their own counts and win where they exist, because they were computed
  // after any offer this page made. Everything else falls back to the staffing view, and only then
  // to the mission's own figures.
  const lines = mission.requirements.map((requirement) => {
    const matched = requirements.find((line) => line.requirementId === requirement.id);
    return (
      matched ??
      placeholder(
        requirement,
        staffing?.requirements.find((line) => line.requirementId === requirement.id),
      )
    );
  });

  return (
    <Box>
      <Button
        component={Link}
        to={`/missions/${missionId}`}
        startIcon={<ArrowBackIcon />}
        sx={{ mb: 2 }}
      >
        {mission.name}
      </Button>

      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, mb: 3 }}
      >
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
          <Typography variant="h1">Crew matching</Typography>
          <MissionStatusChip mission={mission} />
        </Stack>

        <Button
          variant="contained"
          startIcon={<GroupAddIcon />}
          onClick={handleMatchAll}
          disabled={draftWholeMission.isPending || mission.requirements.length === 0}
          title={
            mission.requirements.length === 0
              ? 'Add a crew requirement before matching.'
              : undefined
          }
        >
          {draftWholeMission.isPending ? 'Drafting…' : 'Match all'}
        </Button>
      </Stack>

      <Alert severity={mayOffer ? 'info' : 'warning'} sx={{ mb: 3 }}>
        {draftingNote(
          mayOffer,
          canMatchCrew(user, mission) && mission.missionLead.id === user?.id,
          mission.status,
        )}
      </Alert>

      {actionError && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      {mission.requirements.length === 0 ? (
        <Typography color="text.secondary">
          This mission has no crew requirements yet, so there is nobody to look for. Add one on the
          mission page first.
        </Typography>
      ) : (
        <Stack spacing={2}>
          {lines.map((requirement) => (
            <RequirementDraftCard
              key={requirement.requirementId}
              requirement={requirement}
              committed={committedFor(requirement.requirementId)}
              drafted={drafted[requirement.requirementId] ?? []}
              suggestions={suggestions[requirement.requirementId] ?? []}
              remaining={remaining[requirement.requirementId]}
              matching={busyRequirement === requirement.requirementId}
              offering={offeringRequirement === requirement.requirementId}
              canOffer={mayOffer}
              onMatch={() => handleMatchOne(requirement.requirementId)}
              onPin={(candidate) => handlePin(requirement.requirementId, candidate)}
              onRemove={(candidate) => handleRemove(requirement.requirementId, candidate)}
              onOffer={(candidate) => offerEach(requirement.requirementId, [candidate])}
              onOfferAll={() =>
                offerEach(requirement.requirementId, drafted[requirement.requirementId] ?? [])
              }
            />
          ))}
        </Stack>
      )}
    </Box>
  );
}

/**
 * What the lead can actually do from here, said once at the top.
 *
 * Three cases rather than two, because 'you cannot offer anybody' has two quite different causes
 * and only one of them is about the person reading it. An owner looking at a mission still in
 * planning can draft all they like and simply cannot send anything yet; a director never can. A
 * single message covering both would be wrong for whichever one was reading it.
 */
function draftingNote(mayOffer: boolean, isOwner: boolean, status: MissionResponse['status']) {
  if (mayOffer) {
    return (
      'Drafting is yours alone until you press Offer. Offering tells the crew member, who can ' +
      'then accept or decline - and an offer holds the seat but not the person, so somebody may ' +
      'be offered two clashing missions and take only one.'
    );
  }
  if (isOwner) {
    return (
      `Draft a crew now to see whether this plan is staffable. Places can only be offered once ` +
      `the mission is approved, and this one is ${STATUS_LABELS[status].toLowerCase()}.`
    );
  }
  return (
    'Suggestions are worked out fresh on every request. Only the mission lead who owns this ' +
    'mission can offer anybody a place.'
  );
}

/**
 * The counts a match response carries, without its candidates.
 *
 * A per-requirement match refreshes the staffing figures but only speaks for its own line, so
 * merging just these keeps the other requirements' seats as Match All left them.
 */
function summary(result: RequirementMatchResponse) {
  return {
    requiredCount: result.requiredCount,
    acceptedCount: result.acceptedCount,
    offeredCount: result.offeredCount,
    openSeats: result.openSeats,
  };
}

/**
 * What a requirement looks like before anything has been matched for it.
 *
 * The mission detail response knows the seats and the acceptances but not the outstanding offers,
 * so the staffing view supplies those. Feature 06 assumed zero here, which was true only because
 * nothing could offer anybody anything; now an unasked line and a line waiting on three replies
 * would otherwise look identical.
 */
function placeholder(
  requirement: { id: string; title: string; requiredCount: number; acceptedCount: number },
  staffing?: { acceptedCount: number; offeredCount: number },
): RequirementMatchResponse {
  const accepted = staffing?.acceptedCount ?? requirement.acceptedCount;
  const offered = staffing?.offeredCount ?? 0;

  return {
    requirementId: requirement.id,
    title: requirement.title,
    requiredCount: requirement.requiredCount,
    acceptedCount: accepted,
    offeredCount: offered,
    openSeats: Math.max(0, requirement.requiredCount - accepted - offered),
    remainingCount: 0,
    candidates: [],
  };
}
