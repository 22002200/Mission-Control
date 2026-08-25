import Chip from '@mui/material/Chip';
import type { AssignmentStatus } from '../../lib/assignmentLabels';
import { assignmentStatusColour, assignmentStatusLabel } from '../../lib/assignmentLabels';

/**
 * One assignment's status, as a chip.
 *
 * Its own component rather than an inline `Chip` at each of the three call sites, so the label and
 * the colour cannot drift apart between the mission page, the matching board and a crew member's
 * own list. `MissionStatusChip` exists for the same reason.
 */
export default function AssignmentStatusChip({ status }: { status: AssignmentStatus }) {
  return (
    <Chip
      label={assignmentStatusLabel(status)}
      color={assignmentStatusColour(status)}
      size="small"
      variant={status === 'OFFERED' ? 'outlined' : 'filled'}
    />
  );
}
