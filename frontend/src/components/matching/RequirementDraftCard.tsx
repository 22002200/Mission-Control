import PersonOutlinedIcon from '@mui/icons-material/PersonOutlined';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Divider from '@mui/material/Divider';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { CandidateResponse } from '../../api/generated/types.gen';
import CandidateRow from './CandidateRow';

/**
 * One staffing line as a draft board: its seats, and the alternatives on offer for them.
 *
 * Seats rather than a plain list because `requiredCount` is a quantity - a line needing two people
 * is two seats, and a lead filling one of them should be able to see that the other is still empty.
 *
 * Nothing here is saved. The draft is client state that feature 07 will turn into real offers; the
 * page says so once, above all of these.
 */
export default function RequirementDraftCard({
  requirement,
  drafted,
  suggestions,
  remaining,
  matching,
  onMatch,
  onPin,
  onRemove,
}: {
  requirement: {
    requirementId: string;
    title: string;
    requiredCount: number;
    acceptedCount: number;
    offeredCount: number;
    openSeats: number;
  };
  drafted: CandidateResponse[];
  suggestions: CandidateResponse[];
  remaining: number | undefined;
  matching: boolean;
  onMatch: () => void;
  onPin: (candidate: CandidateResponse) => void;
  onRemove: (candidate: CandidateResponse) => void;
}) {
  const seatCount = requirement.openSeats;
  const emptySeats = Math.max(0, seatCount - drafted.length);
  const nothingLeft = remaining === 0 && suggestions.length === 0;

  return (
    <Card sx={{ p: 2 }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'baseline', mb: 1.5 }}>
        <Typography variant="subtitle1" sx={{ flexGrow: 1, fontWeight: 600 }}>
          {requirement.title}
        </Typography>
        <Typography
          variant="body2"
          sx={{
            whiteSpace: 'nowrap',
            fontWeight: 600,
            color: emptySeats === 0 && seatCount > 0 ? 'success.main' : 'text.secondary',
          }}
        >
          {seatCount === 0
            ? `All ${requirement.requiredCount} filled or offered`
            : `${drafted.length} of ${seatCount} seats drafted`}
        </Typography>
      </Stack>

      {seatCount === 0 ? (
        <Typography variant="body2" color="text.secondary">
          Nothing to draft here. {requirement.acceptedCount} accepted and {requirement.offeredCount}{' '}
          offered against {requirement.requiredCount} seats.
        </Typography>
      ) : (
        <List dense disablePadding sx={{ border: 1, borderColor: 'divider', borderRadius: 1 }}>
          {drafted.map((candidate, index) => (
            <ListItem
              key={candidate.crewMemberId}
              divider={index < seatCount - 1}
              secondaryAction={
                <Button size="small" color="error" onClick={() => onRemove(candidate)}>
                  Remove
                </Button>
              }
            >
              <ListItemText
                primary={`Seat ${index + 1} — ${candidate.fullName}`}
                secondary={`Score ${candidate.score.toFixed(3)}`}
              />
            </ListItem>
          ))}

          {Array.from({ length: emptySeats }, (_, offset) => (
            <ListItem key={`empty-${offset}`} divider={drafted.length + offset < seatCount - 1}>
              <PersonOutlinedIcon fontSize="small" sx={{ mr: 1, color: 'text.disabled' }} />
              <ListItemText
                primary={`Seat ${drafted.length + offset + 1}`}
                secondary="Empty"
                slotProps={{ primary: { color: 'text.secondary' } }}
              />
            </ListItem>
          ))}
        </List>
      )}

      <Divider sx={{ my: 2 }} />

      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
        <Typography variant="overline" color="text.secondary" sx={{ flexGrow: 1 }}>
          Suggestions
        </Typography>
        <Button
          size="small"
          onClick={onMatch}
          disabled={matching || nothingLeft}
          // Disabled with a reason rather than hidden. Running out of candidates is a normal
          // outcome and the screen should say which one it is.
          title={nothingLeft ? 'Every eligible crew member has already been shown.' : undefined}
        >
          {matchLabel(matching, suggestions.length > 0 || drafted.length > 0)}
        </Button>
      </Stack>

      {suggestions.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {nothingLeft
            ? 'Nobody else is eligible for this requirement.'
            : 'No suggestions yet.'}
        </Typography>
      ) : (
        <Stack spacing={1}>
          {suggestions.map((candidate) => (
            <CandidateRow
              key={candidate.crewMemberId}
              candidate={candidate}
              onPin={() => onPin(candidate)}
              pinDisabledReason={
                emptySeats === 0 ? 'Every seat is drafted. Remove someone first.' : undefined
              }
            />
          ))}
        </Stack>
      )}

      {remaining !== undefined && remaining > 0 && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          {remaining} other {remaining === 1 ? 'candidate' : 'candidates'} not yet shown.
        </Typography>
      )}
    </Card>
  );
}

function matchLabel(matching: boolean, hasResults: boolean): string {
  if (matching) return 'Matching…';
  return hasResults ? 'Rematch' : 'Match';
}
