import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Pagination from '@mui/material/Pagination';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { listMissionsOptions } from '../../api/generated/@tanstack/react-query.gen';
import { messageForProblem } from '../../lib/problemDetail';
import type { MissionSection as Section } from '../../lib/missionLabels';
import MissionCard from './MissionCard';

/** One page holds a comfortable number of cards at four across without a wall of scrolling. */
const PAGE_SIZE = 12;

/**
 * One lifecycle group on the mission board, with its own query and its own paging.
 *
 * Each section asks the API for just its own statuses rather than the board fetching one page and
 * splitting it up. That distinction is load-bearing: bucketing a single page client-side would
 * silently drop any mission that happened to fall on another page, and a section that is quietly
 * incomplete is worse than one that is obviously empty.
 *
 * The cost is three requests instead of one. They run in parallel and each is a bounded query, so
 * that is a fair trade for a board that is actually correct.
 */
export default function MissionSection({
  section,
  search,
  page,
  onPageChange,
}: {
  section: Section;
  search: string;
  page: number;
  onPageChange: (page: number) => void;
}) {
  const { data, isPending, error } = useQuery(
    listMissionsOptions({
      query: {
        status: [...section.statuses],
        search: search || undefined,
        page: page - 1,
        size: PAGE_SIZE,
      },
    }),
  );

  const missions = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <Box component="section" sx={{ mb: 5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline', mb: 2 }}>
        <Typography variant="h2" component="h2">
          {section.title}
        </Typography>
        {data && (
          <Typography variant="body2" color="text.secondary">
            · {data.totalElements}
          </Typography>
        )}
      </Stack>

      {isPending && <Typography color="text.secondary">Loading…</Typography>}

      {error && (
        <Typography color="error.main" role="alert">
          {messageForProblem(error, 'Could not load these missions.')}
        </Typography>
      )}

      {/* An empty section keeps its heading and says so. Hiding it would leave a mission lead
          wondering whether they have nothing active or whether the page is broken. */}
      {data && missions.length === 0 && (
        <Typography color="text.secondary">{section.emptyMessage}</Typography>
      )}

      {missions.length > 0 && (
        <Grid container spacing={2}>
          {missions.map((mission) => (
            <Grid key={mission.id} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
              <MissionCard mission={mission} />
            </Grid>
          ))}
        </Grid>
      )}

      {totalPages > 1 && (
        <Pagination
          count={totalPages}
          page={page}
          onChange={(_, next) => onPageChange(next)}
          sx={{ mt: 2 }}
          aria-label={`${section.title} pages`}
        />
      )}
    </Box>
  );
}
