import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { rejectMissionMutation } from '../../api/generated/@tanstack/react-query.gen';
import type { MissionResponse } from '../../api/generated/types.gen';
import { messageForProblem } from '../../lib/problemDetail';

const COMMENT_MAX = 1000;

interface Props {
  open: boolean;
  mission: MissionResponse;
  onClose: () => void;
}

/**
 * A director sending a plan back, with the reason it has to carry.
 *
 * The comment is required - BR-6 - and the submit button stays disabled until there is one, so the
 * requirement is visible before the request rather than arriving as a 400 afterwards. The server
 * enforces it regardless; this only avoids a round trip that was always going to fail.
 *
 * Trimmed before the emptiness test, because whitespace satisfies the letter of "a reason was
 * given" and none of its purpose. The server takes the same view.
 */
export default function RejectMissionDialog({ open, mission, onClose }: Props) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      {open && <RejectMissionForm mission={mission} onClose={onClose} />}
    </Dialog>
  );
}

function RejectMissionForm({ mission, onClose }: Omit<Props, 'open'>) {
  const queryClient = useQueryClient();

  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);

  const reject = useMutation(rejectMissionMutation());

  const trimmed = comment.trim();
  const canSubmit = trimmed.length > 0 && !reject.isPending;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!trimmed) return;

    try {
      await reject.mutateAsync({ path: { id: mission.id }, body: { comment: trimmed } });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'getMission' }] });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissions' }] });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissionApprovals' }] });
      onClose();
    } catch (caught) {
      setError(messageForProblem(caught, 'Could not reject the mission.'));
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <DialogTitle>Reject mission</DialogTitle>

      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {mission.name} goes back to its mission lead, who can revise and resubmit it, or close it.
        </DialogContentText>

        <Stack spacing={2.5}>
          <TextField
            label="Reason"
            required
            autoFocus
            multiline
            minRows={3}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            slotProps={{ htmlInput: { maxLength: COMMENT_MAX } }}
            helperText="Required. This is what the mission lead has to work from."
          />

          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={reject.isPending}>
          Cancel
        </Button>
        <Button type="submit" variant="contained" color="error" disabled={!canSubmit}>
          {reject.isPending ? 'Rejecting…' : 'Reject'}
        </Button>
      </DialogActions>
    </form>
  );
}
