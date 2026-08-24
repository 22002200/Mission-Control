import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import GroupAddIcon from '@mui/icons-material/GroupAdd';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { getMissionOptions } from '../api/generated/@tanstack/react-query.gen';
import { matchAll, matchRequirement } from '../api/generated/sdk.gen';
import type {
  CandidateResponse,
  RequirementMatchResponse,
} from '../api/generated/types.gen';
import { canMatchCrew } from '../auth/permissions';
import { useAuth } from '../auth/useAuth';
import RequirementDraftCard from '../components/matching/RequirementDraftCard';
import MissionStatusChip from '../components/missions/MissionStatusChip';
import { messageForProblem } from '../lib/problemDetail';

/**
 * Drafting a crew for one mission.
 *
 * Its own route rather than another section on the mission page: a draft board with a breakdown per
 * candidate is a workspace, and the mission page is already a summary of everything else. A route
 * is also linkable and survives a refresh, which is the reason this application has a router.
 *
 * **Nothing here is saved.** Feature 06 suggests and does not assign, so the draft is client state
 * and the Offer action arrives with feature 07. The banner says so rather than leaving a lead to
 * discover it by reloading.
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
  const [actionError, setActionError] = useState<string | null>(null);

  const {
    data: mission,
    isPending,
    error,
  } = useQuery(getMissionOptions({ path: { id: missionId } }));

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
          ? current.map((r) => (r.requirementId === requirementId ? { ...r, ...summary(result) } : r))
          : [...current, result],
      );
      setSuggestions((current) => ({ ...current, [requirementId]: result.candidates }));
      setSeen((current) => ({
        ...current,
        [requirementId]: Array.from(
          new Set([...(current[requirementId] ?? []), ...result.candidates.map((c) => c.crewMemberId)]),
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

  const lines = requirements.length > 0 ? requirements : mission.requirements.map(placeholder);

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

      <Alert severity="info" sx={{ mb: 3 }}>
        A draft is not saved and nobody is offered anything. Suggestions are worked out fresh on
        every request; offering crew arrives in feature 07.
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
              drafted={drafted[requirement.requirementId] ?? []}
              suggestions={suggestions[requirement.requirementId] ?? []}
              remaining={remaining[requirement.requirementId]}
              matching={busyRequirement === requirement.requirementId}
              onMatch={() => handleMatchOne(requirement.requirementId)}
              onPin={(candidate) => handlePin(requirement.requirementId, candidate)}
              onRemove={(candidate) => handleRemove(requirement.requirementId, candidate)}
            />
          ))}
        </Stack>
      )}
    </Box>
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
 * The mission detail response knows the seats but not the offers, so `offeredCount` is assumed zero
 * until a match response says otherwise. Feature 07 is what makes that assumption ever wrong, and
 * the first match on the line corrects it.
 */
function placeholder(requirement: {
  id: string;
  title: string;
  requiredCount: number;
  acceptedCount: number;
}): RequirementMatchResponse {
  return {
    requirementId: requirement.id,
    title: requirement.title,
    requiredCount: requirement.requiredCount,
    acceptedCount: requirement.acceptedCount,
    offeredCount: 0,
    openSeats: Math.max(0, requirement.requiredCount - requirement.acceptedCount),
    remainingCount: 0,
    candidates: [],
  };
}
