import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link } from 'react-router';
import type { MyAssignmentResponse } from '../../api/generated/types.gen';
import { formatDateTime } from '../../lib/datetime';
import MissionStatusChip from '../missions/MissionStatusChip';
import AssignmentStatusChip from './AssignmentStatusChip';

/**
 * One of the caller's own assignments, with the two buttons that are theirs to press.
 *
 * Mission-first, because a crew member reading their own list knows who they are and wants to know
 * which mission - the inverse of the mission page, where the mission is the given and the names are
 * the question.
 *
 * Two status chips rather than one, and they mean different things: the mission's own lifecycle,
 * and where this person's place on it has got to. An offer on an approved mission and an
 * acceptance on a closed one are both ordinary states, and one chip could not say either.
 *
 * Accept and Decline appear only while the offer is open. Once accepted the crew member is
 * assigned, and being let off is the mission lead's decision - so there is no button here for it
 * rather than a button that would be refused.
 */
export default function MyAssignmentCard({
  assignment,
  actionable,
  busy,
  onAccept,
  onDecline,
}: {
  assignment: MyAssignmentResponse;
  actionable: boolean;
  busy: boolean;
  onAccept: () => void;
  onDecline: () => void;
}) {
  return (
    <Card sx={{ p: 2 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-start' } }}
      >
        <Stack spacing={0.5} sx={{ flexGrow: 1 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography
              component={Link}
              to={`/missions/${assignment.mission.id}`}
              variant="subtitle1"
              sx={{ fontWeight: 600, color: 'text.primary' }}
            >
              {assignment.mission.name}
            </Typography>
            <AssignmentStatusChip status={assignment.status} />
            <MissionStatusChip mission={assignment.mission} />
          </Stack>

          <Typography variant="body2" color="text.secondary">
            {assignment.requirementTitle}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {formatDateTime(assignment.mission.startsAt)} –{' '}
            {formatDateTime(assignment.mission.endsAt)}
          </Typography>
        </Stack>

        {actionable && (
          <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
            <Button color="error" disabled={busy} onClick={onDecline}>
              Decline
            </Button>
            <Button variant="contained" disabled={busy} onClick={onAccept}>
              {busy ? 'Working…' : 'Accept'}
            </Button>
          </Stack>
        )}
      </Stack>
    </Card>
  );
}
