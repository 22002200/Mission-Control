import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type {
  AssignmentResponse,
  RequirementAssignmentsResponse,
} from '../../api/generated/types.gen';
import { formatDateTime } from '../../lib/datetime';
import AssignmentStatusChip from './AssignmentStatusChip';

/**
 * The crew on one staffing line, under the skills it asks for.
 *
 * On the mission page rather than the matching board on purpose. This is a list of people already
 * committed or asked, which is what a director opens the mission to see; the matching board is
 * about choosing between candidates, and the two questions want different screens.
 *
 * Terminal rows stay visible and greyed rather than disappearing. A lead looking at a half-filled
 * line needs to know whether the gap is because nobody was asked or because two people said no,
 * and a list that quietly drops refusals answers neither.
 */
export default function RequirementCrewList({
  requirement,
  canWithdraw,
  withdrawing,
  onWithdraw,
}: {
  requirement: RequirementAssignmentsResponse;
  canWithdraw: (assignment: AssignmentResponse) => boolean;
  withdrawing: string | null;
  onWithdraw: (assignment: AssignmentResponse) => void;
}) {
  if (requirement.assignments.length === 0) {
    return (
      <>
        <Divider sx={{ my: 1.5 }} />
        <Typography variant="body2" color="text.secondary">
          Nobody has been offered a place on this line yet.
        </Typography>
      </>
    );
  }

  return (
    <>
      <Divider sx={{ my: 1.5 }} />
      <Typography variant="overline" color="text.secondary" component="div">
        Crew
      </Typography>

      <List dense disablePadding>
        {requirement.assignments.map((assignment) => {
          const settled = assignment.status === 'DECLINED' || assignment.status === 'WITHDRAWN';

          return (
            <ListItem
              key={assignment.id}
              disableGutters
              secondaryAction={
                canWithdraw(assignment) && (
                  <Button
                    size="small"
                    color="error"
                    disabled={withdrawing === assignment.id}
                    onClick={() => onWithdraw(assignment)}
                  >
                    {withdrawing === assignment.id ? 'Withdrawing…' : 'Withdraw'}
                  </Button>
                )
              }
            >
              <ListItemText
                primary={
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Box component="span" sx={{ color: settled ? 'text.disabled' : undefined }}>
                      {assignment.crewMember.fullName}
                    </Box>
                    <AssignmentStatusChip status={assignment.status} />
                  </Stack>
                }
                secondary={timeline(assignment)}
                slotProps={{ secondary: { variant: 'caption' } }}
              />
            </ListItem>
          );
        })}
      </List>
    </>
  );
}

/**
 * When it was offered, and when it was settled.
 *
 * Both, because the gap between them is the useful part - an offer made three weeks ago and still
 * unanswered is a different situation from one made this morning.
 */
function timeline(assignment: AssignmentResponse): string {
  const offered = `Offered ${formatDateTime(assignment.offeredAt)}`;
  if (!assignment.respondedAt) {
    return `${offered} · awaiting a reply`;
  }
  return `${offered} · settled ${formatDateTime(assignment.respondedAt)}`;
}
