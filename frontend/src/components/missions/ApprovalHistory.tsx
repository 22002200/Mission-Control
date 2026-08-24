import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import Accordion from '@mui/material/Accordion';
import AccordionDetails from '@mui/material/AccordionDetails';
import AccordionSummary from '@mui/material/AccordionSummary';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { styled } from '@mui/material/styles';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { listMissionApprovalsOptions } from '../../api/generated/@tanstack/react-query.gen';
import type { MissionApprovalResponse, MissionResponse } from '../../api/generated/types.gen';
import { formatDateTime } from '../../lib/datetime';
import { decisionColour, decisionLabel } from '../../lib/missionLabels';
import { messageForProblem } from '../../lib/problemDetail';

/**
 * One entry in the timeline, ruled off down its left edge.
 *
 * A `styled` component rather than a repeated `sx`, per the styling rule in `CLAUDE.md`: four
 * overrides, and it is applied once per cycle.
 */
const ApprovalRow = styled(Stack)(({ theme }) => ({
  paddingLeft: theme.spacing(2),
  paddingBottom: theme.spacing(2),
  borderLeft: `2px solid ${theme.palette.divider}`,
  gap: theme.spacing(0.5),
  '&:last-of-type': {
    paddingBottom: 0,
    // The rule stops at the last entry rather than trailing off into whitespace below it.
    borderLeftColor: 'transparent',
  },
}));

const SubmissionLine = styled(Typography)(({ theme }) => ({
  color: theme.palette.text.secondary,
  fontSize: theme.typography.body2.fontSize,
  marginTop: theme.spacing(0.5),
}));

interface Props {
  missionId: string;
  mission: Pick<MissionResponse, 'status'>;
}

/**
 * Every submit-and-decide cycle on a mission - FR-6.
 *
 * Collapsed by default, because most visits to this page are not about the history. The exception is
 * a rejection: that comment is the single most actionable thing on a rejected mission, and hiding
 * the reason a lead has to act on behind a click is the wrong default. So the accordion opens itself
 * when the newest cycle is a rejection.
 *
 * That "opens itself" is why the accordion is controlled rather than using `defaultExpanded`. The
 * default is read once, on the first render - when this query is still pending and there are no
 * cycles to look at - so by the time the data says "rejected" it has already been ignored. MUI even
 * warns about it. `expanded` starts as null meaning "nobody has chosen", follows the data while that
 * holds, and stops following the moment the reader clicks either way.
 *
 * Its own query rather than part of the mission response. The history is only wanted on this screen,
 * and folding it into `GET /api/missions/{id}` would make every mission read - including the board -
 * pay for it.
 */
export default function ApprovalHistory({ missionId, mission }: Props) {
  const { data, isPending, error } = useQuery(
    listMissionApprovalsOptions({ path: { id: missionId } }),
  );

  const [chosen, setChosen] = useState<boolean | null>(null);

  const cycles = data ?? [];
  const newest = cycles[0];
  const expanded = chosen ?? newest?.decision === 'REJECTED';

  return (
    <Accordion
      expanded={expanded}
      onChange={(_, next) => setChosen(next)}
      disableGutters
      sx={{ mt: 4 }}
    >
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Typography variant="h2" component="h2">
          Approval history
        </Typography>
        {data && cycles.length > 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ alignSelf: 'center', ml: 1 }}>
            · {cycles.length === 1 ? '1 cycle' : `${cycles.length} cycles`}
          </Typography>
        )}
      </AccordionSummary>

      <AccordionDetails>
        {isPending && <Typography color="text.secondary">Loading…</Typography>}

        {error && (
          <Typography color="error.main" role="alert">
            {messageForProblem(error, 'Could not load the approval history.')}
          </Typography>
        )}

        {data && cycles.length === 0 && (
          <Typography color="text.secondary">
            This mission has not been submitted for approval yet.
          </Typography>
        )}

        {cycles.map((cycle) => (
          <Cycle key={cycle.id} cycle={cycle} missionStatus={mission.status} />
        ))}
      </AccordionDetails>
    </Accordion>
  );
}

function Cycle({
  cycle,
  missionStatus,
}: {
  cycle: MissionApprovalResponse;
  missionStatus: MissionResponse['status'];
}) {
  // A pending cycle on a closed mission is not waiting for anyone - the mission was closed before
  // anybody decided, and the cycle was cancelled with it. Saying 'awaiting a decision' there would
  // be untrue. This is belt and braces: the server settles the cycle as CANCELLED on close, so this
  // only covers a row written before that behaviour existed.
  const stale = cycle.decision === 'PENDING' && missionStatus === 'CLOSED';

  return (
    <ApprovalRow>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Chip
          size="small"
          label={stale ? 'Not decided' : decisionLabel(cycle.decision)}
          color={stale ? 'default' : decisionColour(cycle.decision)}
        />
        {cycle.decidedAt && (
          <Typography variant="body2" color="text.secondary">
            {cycle.decidedBy?.fullName} · {formatDateTime(cycle.decidedAt)}
          </Typography>
        )}
        {stale && (
          <Typography variant="body2" color="text.secondary">
            The mission was closed first.
          </Typography>
        )}
      </Stack>

      {cycle.comment && <Typography variant="body2">{cycle.comment}</Typography>}

      <SubmissionLine>
        Submitted by {cycle.submittedBy.fullName} · {formatDateTime(cycle.submittedAt)}
      </SubmissionLine>
    </ApprovalRow>
  );
}
