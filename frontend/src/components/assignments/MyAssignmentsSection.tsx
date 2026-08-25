import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import MenuItem from '@mui/material/MenuItem';
import Pagination from '@mui/material/Pagination';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  acceptAssignmentMutation,
  declineAssignmentMutation,
  listMyAssignmentsOptions,
} from '../../api/generated/@tanstack/react-query.gen';
import type { MyAssignmentResponse } from '../../api/generated/types.gen';
import { canRespondToOffer } from '../../auth/permissions';
import { useAuth } from '../../auth/useAuth';
import {
  ASSIGNMENT_STATUS_LABELS,
  TIMEFRAME_LABELS,
  type AssignmentStatus,
  type Timeframe,
} from '../../lib/assignmentLabels';
import { messageForProblem } from '../../lib/problemDetail';
import MyAssignmentCard from './MyAssignmentCard';

/** Enough to see a crew member's whole commitment without paging in the ordinary case. */
const PAGE_SIZE = 10;

const ALL = 'ALL';

/**
 * A crew member's own assignments, above the mission board.
 *
 * A section rather than a route, deliberately. Feature 08 gives crew members a real dashboard and
 * this will fold into it; adding a navigation item now that exists for one role in three would be
 * something to unpick then. Until then the board is where a crew member lands, and an offer they
 * cannot see is an offer they cannot answer.
 *
 * **Its filters live in here, not in the board's filter card.** FR-9 asks for status and timeframe
 * over assignments; the card above filters missions by their status. Two status dropdowns on one
 * screen meaning different things would be worse than either, so these sit inside the section they
 * apply to and are labelled for what they filter.
 *
 * Pending offers are pinned to the top whatever the sort, because they are the only rows that need
 * anything from the person reading them.
 */
export default function MyAssignmentsSection() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [status, setStatus] = useState<string>(ALL);
  const [timeframe, setTimeframe] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);

  const { data, isPending, error } = useQuery(
    listMyAssignmentsOptions({
      query: {
        status: status === ALL ? undefined : (status as AssignmentStatus),
        timeframe: timeframe === ALL ? undefined : (timeframe as Timeframe),
        page,
        size: PAGE_SIZE,
      },
    }),
  );

  const accept = useMutation(acceptAssignmentMutation());
  const decline = useMutation(declineAssignmentMutation());

  async function respond(assignment: MyAssignmentResponse, action: 'accept' | 'decline') {
    setActionError(null);
    try {
      const mutation = action === 'accept' ? accept : decline;
      await mutation.mutateAsync({ path: { assignmentId: assignment.id } });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMyAssignments' }] });
      // Accepting makes the mission visible in a new light on the board, and the mission's own
      // staffing counts move with it.
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissions' }] });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'getMission' }] });
    } catch (caught) {
      setActionError(
        messageForProblem(
          caught,
          action === 'accept' ? 'Could not accept this place.' : 'Could not decline this place.',
        ),
      );
    }
  }

  function changeFilter(next: string, apply: (value: string) => void) {
    apply(next);
    setPage(0);
  }

  const assignments = [...(data?.content ?? [])].sort(offersFirst);
  const busyOn = accept.isPending || decline.isPending;

  return (
    <Box sx={{ mb: 4 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 2 }}
      >
        <Typography variant="h2">Your assignments</Typography>

        <Stack direction="row" spacing={2}>
          <TextField
            select
            size="small"
            label="Assignment status"
            value={status}
            onChange={(event) => changeFilter(event.target.value, setStatus)}
            sx={{ minWidth: '11rem' }}
          >
            <MenuItem value={ALL}>All</MenuItem>
            {Object.entries(ASSIGNMENT_STATUS_LABELS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            select
            size="small"
            label="When"
            value={timeframe}
            onChange={(event) => changeFilter(event.target.value, setTimeframe)}
            sx={{ minWidth: '11rem' }}
          >
            <MenuItem value={ALL}>Any time</MenuItem>
            {Object.entries(TIMEFRAME_LABELS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </Stack>

      {actionError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      {isPending && <Typography color="text.secondary">Loading your assignments…</Typography>}

      {error && (
        <Typography color="error.main" role="alert">
          {messageForProblem(error, 'Could not load your assignments.')}
        </Typography>
      )}

      {!isPending && !error && assignments.length === 0 && (
        <Typography color="text.secondary">
          {status === ALL && timeframe === ALL
            ? 'You have not been offered a place on any mission yet.'
            : 'Nothing matches those filters.'}
        </Typography>
      )}

      <Stack spacing={2}>
        {assignments.map((assignment) => (
          <MyAssignmentCard
            key={assignment.id}
            assignment={assignment}
            actionable={canRespondToOffer(user, assignment)}
            busy={busyOn}
            onAccept={() => respond(assignment, 'accept')}
            onDecline={() => respond(assignment, 'decline')}
          />
        ))}
      </Stack>

      {(data?.totalPages ?? 0) > 1 && (
        <Pagination
          sx={{ mt: 2 }}
          count={data?.totalPages ?? 1}
          page={page + 1}
          onChange={(_, next) => setPage(next - 1)}
        />
      )}
    </Box>
  );
}

/**
 * Open offers first, then everything else in the order the server sent.
 *
 * The server sorts by when the offer was made, which is the right default for a history but buries
 * the one row that needs an answer if somebody has been on a few missions. Sorting here rather than
 * asking the server for it keeps the endpoint's contract simple - the ordering is a property of
 * this screen, not of the data.
 */
function offersFirst(left: MyAssignmentResponse, right: MyAssignmentResponse): number {
  const weight = (assignment: MyAssignmentResponse) => (assignment.status === 'OFFERED' ? 0 : 1);
  return weight(left) - weight(right);
}
