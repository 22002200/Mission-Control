import PersonOutlinedIcon from '@mui/icons-material/PersonOutlined';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Divider from '@mui/material/Divider';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { AssignmentResponse, CandidateResponse } from '../../api/generated/types.gen';
import { staffingSummary } from '../../lib/assignmentLabels';
import AssignmentStatusChip from '../assignments/AssignmentStatusChip';
import CandidateRow from './CandidateRow';

/**
 * One staffing line as a draft board: who is already on it, who is drafted for it, and who else
 * there is.
 *
 * Seats rather than a plain list because `requiredCount` is a quantity - a line needing two people
 * is two seats, and a lead filling one of them should be able to see that the other is still empty.
 *
 * Three kinds of row can occupy a seat now, and the distinction is the whole point of the screen.
 * A **committed** row is real: somebody has been offered the place, or has taken it, and undoing
 * that is a withdrawal on the mission page. A **drafted** row is client state - a candidate the
 * lead has pencilled in and not yet offered. An **empty** seat is neither. Feature 06 had only the
 * middle kind, because nothing could be offered yet.
 */
export default function RequirementDraftCard({
  requirement,
  committed,
  drafted,
  suggestions,
  remaining,
  matching,
  offering,
  canOffer,
  onMatch,
  onPin,
  onRemove,
  onOffer,
  onOfferAll,
}: {
  requirement: {
    requirementId: string;
    title: string;
    requiredCount: number;
    acceptedCount: number;
    offeredCount: number;
    openSeats: number;
  };
  committed: AssignmentResponse[];
  drafted: CandidateResponse[];
  suggestions: CandidateResponse[];
  remaining: number | undefined;
  matching: boolean;
  offering: boolean;
  canOffer: boolean;
  onMatch: () => void;
  onPin: (candidate: CandidateResponse) => void;
  onRemove: (candidate: CandidateResponse) => void;
  onOffer: (candidate: CandidateResponse) => void;
  onOfferAll: () => void;
}) {
  const openSeats = requirement.openSeats;
  const emptySeats = Math.max(0, openSeats - drafted.length);
  const nothingLeft = remaining === 0 && suggestions.length === 0;
  const totalRows = committed.length + drafted.length + emptySeats;

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
            color: openSeats === 0 ? 'success.main' : 'text.secondary',
          }}
        >
          {staffingSummary(
            requirement.requiredCount,
            requirement.acceptedCount,
            requirement.offeredCount,
          )}
        </Typography>
      </Stack>

      {totalRows === 0 ? (
        <Typography variant="body2" color="text.secondary">
          Every place on this line is taken.
        </Typography>
      ) : (
        <List dense disablePadding sx={{ border: 1, borderColor: 'divider', borderRadius: 1 }}>
          {committed.map((assignment, index) => (
            <ListItem key={assignment.id} divider={index < totalRows - 1}>
              <ListItemText
                primary={assignment.crewMember.fullName}
                secondary="Already offered — withdraw from the mission page"
              />
              <AssignmentStatusChip status={assignment.status} />
            </ListItem>
          ))}

          {drafted.map((candidate, index) => (
            <ListItem
              key={candidate.crewMemberId}
              divider={committed.length + index < totalRows - 1}
              secondaryAction={
                <Stack direction="row" spacing={1}>
                  <Button size="small" color="error" onClick={() => onRemove(candidate)}>
                    Remove
                  </Button>
                  {canOffer && (
                    <Button
                      size="small"
                      variant="contained"
                      disabled={offering}
                      onClick={() => onOffer(candidate)}
                    >
                      Offer
                    </Button>
                  )}
                </Stack>
              }
            >
              <ListItemText
                primary={candidate.fullName}
                secondary={`Drafted · score ${candidate.score.toFixed(3)}`}
              />
            </ListItem>
          ))}

          {Array.from({ length: emptySeats }, (_, offset) => (
            <ListItem
              key={`empty-${offset}`}
              divider={committed.length + drafted.length + offset < totalRows - 1}
            >
              <PersonOutlinedIcon fontSize="small" sx={{ mr: 1, color: 'text.disabled' }} />
              <ListItemText
                primary="Empty seat"
                secondary="Nobody drafted or offered"
                slotProps={{ primary: { color: 'text.secondary' } }}
              />
            </ListItem>
          ))}
        </List>
      )}

      {drafted.length > 0 && canOffer && (
        <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: 1.5 }}>
          <Button variant="contained" disabled={offering} onClick={onOfferAll}>
            {offering
              ? 'Offering…'
              : `Offer ${drafted.length === 1 ? 'this place' : `all ${drafted.length}`}`}
          </Button>
        </Stack>
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
          {nothingLeft ? 'Nobody else is eligible for this requirement.' : 'No suggestions yet.'}
        </Typography>
      ) : (
        <Stack spacing={1}>
          {suggestions.map((candidate) => (
            <CandidateRow
              key={candidate.crewMemberId}
              candidate={candidate}
              onPin={() => onPin(candidate)}
              pinDisabledReason={
                emptySeats === 0
                  ? openSeats === 0
                    ? 'Every place on this line is already offered or filled.'
                    : 'Every open seat is drafted. Remove someone first.'
                  : undefined
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
