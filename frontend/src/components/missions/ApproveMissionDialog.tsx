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
import { approveMissionMutation } from '../../api/generated/@tanstack/react-query.gen';
import type { MissionResponse } from '../../api/generated/types.gen';
import { messageForProblem } from '../../lib/problemDetail';

interface Props {
  open: boolean;
  mission: MissionResponse;
  onClose: () => void;
}

/**
 * A director clearing a plan to go ahead.
 *
 * A dialog rather than a bare button, even though the note is optional and approval is reversible
 * in the sense that the mission can still be closed. Approving is the step that lets crew be
 * offered places, and a confirmation is cheap next to a misplaced click on a board of cards.
 *
 * The note is optional here and required on rejection. That asymmetry is the point: an approval
 * needs no justification, a rejection is useless without one.
 */
export default function ApproveMissionDialog({ open, mission, onClose }: Props) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      {open && <ApproveMissionForm mission={mission} onClose={onClose} />}
    </Dialog>
  );
}

function ApproveMissionForm({ mission, onClose }: Omit<Props, 'open'>) {
  const queryClient = useQueryClient();

  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);

  const approve = useMutation(approveMissionMutation());

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    try {
      await approve.mutateAsync({
        path: { id: mission.id },
        body: { comment: comment.trim() || undefined },
      });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'getMission' }] });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissions' }] });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissionApprovals' }] });
      onClose();
    } catch (caught) {
      setError(messageForProblem(caught, 'Could not approve the mission.'));
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <DialogTitle>Approve mission</DialogTitle>

      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Approving {mission.name} lets its mission lead start assigning crew.
        </DialogContentText>

        <Stack spacing={2.5}>
          <TextField
            label="Note"
            multiline
            minRows={2}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
            helperText="Optional. Recorded against this approval."
          />

          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={approve.isPending}>
          Cancel
        </Button>
        <Button type="submit" variant="contained" disabled={approve.isPending}>
          {approve.isPending ? 'Approving…' : 'Approve'}
        </Button>
      </DialogActions>
    </form>
  );
}
