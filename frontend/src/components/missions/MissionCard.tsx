import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link } from 'react-router';
import type { MissionSummaryResponse } from '../../api/generated/types.gen';
import { formatDateRange } from '../../lib/datetime';
import MissionStatusChip from './MissionStatusChip';

/** Keeps a nearly-empty section from stretching two cards across an ultrawide monitor. */
const MAX_CARD_WIDTH = '22rem';

/**
 * One mission on the board.
 *
 * Fixed height and a bottom-pinned crew line, so the figures sit on the same baseline across a row
 * however long the names above them are. Without that the eye has to hunt for each one, which is
 * the whole reason for showing them together.
 *
 * The entire card is the link rather than the title alone: a card that looks clickable everywhere
 * but only works in one corner is worse than one that is plainly not clickable.
 */
export default function MissionCard({ mission }: { mission: MissionSummaryResponse }) {
  const crewLabel =
    mission.requiredCount === 0
      ? 'No crew required yet'
      : `${mission.acceptedCount} / ${mission.requiredCount}`;

  return (
    <Card sx={{ maxWidth: MAX_CARD_WIDTH, height: '100%' }}>
      <CardActionArea
        component={Link}
        to={`/missions/${mission.id}`}
        sx={{ height: '100%', p: 2, display: 'flex', alignItems: 'stretch' }}
      >
        <Stack spacing={1.5} sx={{ width: '100%' }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
            <Typography
              variant="subtitle1"
              sx={{
                fontWeight: 600,
                lineHeight: 1.3,
                flexGrow: 1,
                // Two lines, then ellipsis. A long mission name must not push the crew figures
                // out of line with the cards beside it.
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {mission.name}
            </Typography>
          </Stack>

          {/* Wrapped, because a Chip placed straight into a column Stack stretches to the full
              card width and stops reading as a chip. */}
          <Box>
            <MissionStatusChip mission={mission} />
          </Box>

          {/* Pushes everything below it to the bottom of the card. */}
          <Box sx={{ flexGrow: 1 }} />

          <Stack spacing={0.5}>
            <Typography variant="body2" color="text.secondary">
              {formatDateRange(mission.startsAt, mission.endsAt)}
            </Typography>
            <Typography variant="body2" color="text.secondary" noWrap>
              {mission.missionLead.fullName}
            </Typography>
            <Stack direction="row" sx={{ justifyContent: 'space-between', pt: 0.5 }}>
              <Typography variant="body2" color="text.secondary">
                Crew
              </Typography>
              <Typography
                variant="body2"
                sx={{
                  fontWeight: 600,
                  fontVariantNumeric: 'tabular-nums',
                  color: mission.fullyStaffed ? 'success.main' : 'text.primary',
                }}
              >
                {crewLabel}
              </Typography>
            </Stack>
          </Stack>
        </Stack>
      </CardActionArea>
    </Card>
  );
}
