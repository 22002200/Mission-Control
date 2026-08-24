import Chip from '@mui/material/Chip';
import type { MissionSummaryResponse } from '../../api/generated/types.gen';
import { statusColour, statusLabel } from '../../lib/missionLabels';

/**
 * A mission's state, as a chip.
 *
 * Takes the whole mission rather than just the status because a closed one shows why it closed -
 * Completed and Aborted are the distinction that matters once a mission is over, and the word
 * 'Closed' hides it. The label logic lives in `missionLabels` so the board and the detail page
 * cannot disagree.
 */
export default function MissionStatusChip({
  mission,
  size = 'small',
}: {
  mission: Pick<MissionSummaryResponse, 'status' | 'closeReason'>;
  size?: 'small' | 'medium';
}) {
  return (
    <Chip
      label={statusLabel(mission)}
      color={statusColour(mission.status)}
      size={size}
      variant={mission.status === 'CLOSED' ? 'outlined' : 'filled'}
    />
  );
}
