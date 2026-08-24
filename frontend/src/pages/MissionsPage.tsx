import AddIcon from '@mui/icons-material/Add';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../auth/useAuth';
import { canCreateMission } from '../auth/permissions';
import MissionFormDialog from '../components/missions/MissionFormDialog';
import MissionSection from '../components/missions/MissionSection';
import {
  MISSION_SECTIONS,
  STATUS_LABELS,
  sectionFor,
  type MissionStatus,
} from '../lib/missionLabels';

/** Long enough that typing a name does not fire a request per keystroke. */
const SEARCH_DEBOUNCE_MS = 300;

const ALL_STATUSES = 'ALL';

/**
 * The mission board.
 *
 * Three lifecycle sections rather than one long list, because the questions people bring to this
 * screen are different for each: what still needs planning, what is flying, and what happened.
 *
 * The filters live in the query string so a filtered view can be shared or bookmarked and survives
 * a refresh. Choosing a specific status narrows the board to the one section that status belongs
 * to, so the filter and the sections compose instead of contradicting each other.
 */
export default function MissionsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const statusParam = searchParams.get('status') ?? ALL_STATUSES;
  const searchParam = searchParams.get('search') ?? '';

  const [searchInput, setSearchInput] = useState(searchParam);
  const [debouncedSearch, setDebouncedSearch] = useState(searchParam);
  const [creating, setCreating] = useState(false);
  const [pages, setPages] = useState<Record<string, number>>({});

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchInput), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // The URL is updated only once the debounce settles, so typing does not fill the history stack
  // with one entry per character.
  useEffect(() => {
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (debouncedSearch) {
          next.set('search', debouncedSearch);
        } else {
          next.delete('search');
        }
        return next;
      },
      { replace: true },
    );
  }, [debouncedSearch, setSearchParams]);

  const visibleSections = useMemo(() => {
    if (statusParam === ALL_STATUSES) return MISSION_SECTIONS;

    const status = statusParam as MissionStatus;
    const owning = sectionFor(status);
    // Keep the section's identity and heading, but narrow it to the one status asked for.
    return [{ ...owning, statuses: [status] as readonly MissionStatus[] }];
  }, [statusParam]);

  function changeStatus(next: string) {
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current);
        if (next === ALL_STATUSES) {
          params.delete('status');
        } else {
          params.set('status', next);
        }
        return params;
      },
      { replace: true },
    );
    setPages({});
  }

  return (
    <Box>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 3 }}
      >
        <Typography variant="h1">Missions</Typography>

        {canCreateMission(user) && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
            New mission
          </Button>
        )}
      </Stack>

      <Card sx={{ p: 2, mb: 4 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField
            select
            label="Status"
            value={statusParam}
            onChange={(event) => changeStatus(event.target.value)}
            sx={{ maxWidth: { sm: '14rem' } }}
          >
            <MenuItem value={ALL_STATUSES}>All statuses</MenuItem>
            {Object.entries(STATUS_LABELS).map(([status, label]) => (
              <MenuItem key={status} value={status}>
                {label}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            label="Search"
            placeholder="Mission name"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            sx={{ maxWidth: { sm: '22rem' } }}
          />
        </Stack>
      </Card>

      {visibleSections.map((section) => (
        <MissionSection
          key={section.key}
          section={section}
          search={debouncedSearch}
          page={pages[section.key] ?? 1}
          onPageChange={(page) => setPages((current) => ({ ...current, [section.key]: page }))}
        />
      ))}

      <MissionFormDialog
        open={creating}
        onClose={() => setCreating(false)}
        onSaved={(mission) => navigate(`/missions/${mission.id}`)}
      />
    </Box>
  );
}
