import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  deleteRequirementMutation,
  getMissionOptions,
  startMissionMutation,
} from '../api/generated/@tanstack/react-query.gen';
import type { CrewRequirementResponse } from '../api/generated/types.gen';
import {
  canCloseMission,
  canManageRequirements,
  canModifyMission,
  canStartMission,
} from '../auth/permissions';
import { useAuth } from '../auth/useAuth';
import CloseMissionDialog from '../components/missions/CloseMissionDialog';
import MissionFormDialog from '../components/missions/MissionFormDialog';
import MissionStatusChip from '../components/missions/MissionStatusChip';
import RequirementCard from '../components/missions/RequirementCard';
import RequirementFormDialog from '../components/missions/RequirementFormDialog';
import { formatDateTime } from '../lib/datetime';
import { CLOSE_REASON_LABELS } from '../lib/missionLabels';
import { messageForProblem } from '../lib/problemDetail';

/**
 * One mission, and everything that can be done to it in this feature.
 *
 * The actions on offer are decided by `auth/permissions`, which mirrors the invariants the server
 * enforces. Anything the caller may not do is hidden rather than disabled, with one exception:
 * Start stays visible and disabled on an approved mission that is not yet crewed, because
 * 'why can I not start this' is the question this screen exists to answer.
 */
export default function MissionDetailPage() {
  const { missionId = '' } = useParams();
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState(false);
  const [closing, setClosing] = useState(false);
  const [requirementDialog, setRequirementDialog] = useState<
    { open: false } | { open: true; requirement?: CrewRequirementResponse }
  >({ open: false });
  const [actionError, setActionError] = useState<string | null>(null);

  const {
    data: mission,
    isPending,
    error,
  } = useQuery(getMissionOptions({ path: { id: missionId } }));

  const start = useMutation(startMissionMutation());
  const removeRequirement = useMutation(deleteRequirementMutation());

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

  const mayEdit = canModifyMission(user, mission);
  const mayManageRequirements = canManageRequirements(user, mission);
  const mayStart = canStartMission(user, mission);

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: [{ _id: 'getMission' }] });
    await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissions' }] });
  }

  async function handleStart() {
    setActionError(null);
    try {
      await start.mutateAsync({ path: { id: missionId } });
      await refresh();
    } catch (caught) {
      setActionError(messageForProblem(caught, 'Could not start the mission.'));
    }
  }

  async function handleDeleteRequirement(requirement: CrewRequirementResponse) {
    setActionError(null);
    try {
      await removeRequirement.mutateAsync({
        path: { missionId, requirementId: requirement.id },
      });
      await refresh();
    } catch (caught) {
      setActionError(messageForProblem(caught, 'Could not remove the requirement.'));
    }
  }

  return (
    <Box>
      <Button component={Link} to="/missions" startIcon={<ArrowBackIcon />} sx={{ mb: 2 }}>
        Missions
      </Button>

      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { md: 'flex-start' }, mb: 3 }}
      >
        <Box>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
            <Typography variant="h1">{mission.name}</Typography>
            <MissionStatusChip mission={mission} />
          </Stack>
          {mission.description && (
            <Typography color="text.secondary" sx={{ maxWidth: '48rem' }}>
              {mission.description}
            </Typography>
          )}
        </Box>

        <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
          {mayEdit && <Button onClick={() => setEditing(true)}>Edit</Button>}
          {mayStart && (
            <Button
              variant="contained"
              onClick={handleStart}
              disabled={!mission.fullyStaffed || start.isPending}
              // Disabled rather than hidden, so the reason is visible rather than the button
              // simply being absent with no explanation.
              title={
                mission.fullyStaffed
                  ? undefined
                  : 'Every crew requirement must be filled before a mission can start.'
              }
            >
              {start.isPending ? 'Starting…' : 'Start mission'}
            </Button>
          )}
          {canCloseMission(user, mission) && (
            <Button color="error" onClick={() => setClosing(true)}>
              Close mission
            </Button>
          )}
        </Stack>
      </Stack>

      {actionError && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      <Card sx={{ p: 2, mb: 4 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4}>
          <Fact label="Starts">{formatDateTime(mission.startsAt)}</Fact>
          <Fact label="Ends">{formatDateTime(mission.endsAt)}</Fact>
          <Fact label="Mission lead">{mission.missionLead.fullName}</Fact>
          <Fact label="Crew">{mission.fullyStaffed ? 'Fully crewed' : 'Not fully crewed'}</Fact>
          {mission.closeReason && (
            <Fact label="Outcome">{CLOSE_REASON_LABELS[mission.closeReason]}</Fact>
          )}
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
          Times are shown in your local timezone.
        </Typography>
        {mission.closeComment && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {mission.closeComment}
          </Typography>
        )}
      </Card>

      <Stack
        direction="row"
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}
      >
        <Typography variant="h2">Crew requirements</Typography>
        {mayManageRequirements && (
          <Button startIcon={<AddIcon />} onClick={() => setRequirementDialog({ open: true })}>
            Add requirement
          </Button>
        )}
      </Stack>

      {mission.requirements.length === 0 ? (
        <Typography color="text.secondary">
          {mayManageRequirements
            ? 'No crew requirements yet. Add one to describe who this mission needs.'
            : 'No crew requirements have been defined.'}
        </Typography>
      ) : (
        <Stack spacing={2}>
          {mission.requirements.map((requirement) => (
            <RequirementCard
              key={requirement.id}
              requirement={requirement}
              editable={mayManageRequirements}
              onEdit={() => setRequirementDialog({ open: true, requirement })}
              onDelete={() => handleDeleteRequirement(requirement)}
            />
          ))}
        </Stack>
      )}

      <MissionFormDialog open={editing} mission={mission} onClose={() => setEditing(false)} />

      <CloseMissionDialog open={closing} mission={mission} onClose={() => setClosing(false)} />

      <RequirementFormDialog
        open={requirementDialog.open}
        missionId={missionId}
        requirement={requirementDialog.open ? requirementDialog.requirement : undefined}
        onClose={() => setRequirementDialog({ open: false })}
      />
    </Box>
  );
}

/** A labelled value in the summary strip. */
function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box>
      <Typography variant="overline" color="text.secondary" component="div">
        {label}
      </Typography>
      <Typography variant="body2">{children}</Typography>
    </Box>
  );
}
