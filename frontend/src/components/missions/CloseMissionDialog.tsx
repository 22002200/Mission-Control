import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { closeMissionMutation } from '../../api/generated/@tanstack/react-query.gen';
import type { MissionResponse } from '../../api/generated/types.gen';
import { messageForProblem } from '../../lib/problemDetail';

type CloseReason = 'COMPLETED' | 'ABORTED' | 'REJECTED';

interface Props {
  open: boolean;
  mission: MissionResponse;
  onClose: () => void;
}

/**
 * Ending a mission.
 *
 * The reason is preselected the way the server would default it - COMPLETED for a mission that was
 * running, ABORTED for anything stopped earlier - so the common case is one click and the choice
 * is still visible.
 *
 * REJECTED is offered only for a mission that really was rejected, which is the one case the server
 * accepts it in: a close reason contradicting the mission's own history would make the record
 * actively misleading. It is also preselected there, because abandoning a rejected plan rather than
 * revising it is FR-5, and 'aborted' would lose why it ended.
 *
 * A confirmation step rather than a bare button, because closing is terminal. Nothing reopens a
 * closed mission.
 */
export default function CloseMissionDialog({ open, mission, onClose }: Props) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      {open && <CloseMissionForm mission={mission} onClose={onClose} />}
    </Dialog>
  );
}

function CloseMissionForm({ mission, onClose }: Omit<Props, 'open'>) {
  const queryClient = useQueryClient();

  const rejected = mission.status === 'REJECTED';

  const [reason, setReason] = useState<CloseReason>(() => {
    if (mission.status === 'ACTIVE') return 'COMPLETED';
    return rejected ? 'REJECTED' : 'ABORTED';
  });
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);

  const close = useMutation(closeMissionMutation());

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    try {
      await close.mutateAsync({
        path: { id: mission.id },
        body: { closeReason: reason, comment: comment.trim() || undefined },
      });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'getMission' }] });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissions' }] });
      onClose();
    } catch (caught) {
      setError(messageForProblem(caught, 'Could not close the mission.'));
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <DialogTitle>Close mission</DialogTitle>

      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Closing {mission.name} is permanent. It cannot be reopened or edited afterwards.
        </DialogContentText>

        <Stack spacing={2.5}>
          <TextField
            select
            label="Reason"
            value={reason}
            onChange={(event) => setReason(event.target.value as CloseReason)}
          >
            <MenuItem value="COMPLETED">Completed</MenuItem>
            <MenuItem value="ABORTED">Aborted</MenuItem>
            {rejected && <MenuItem value="REJECTED">Rejected</MenuItem>}
          </TextField>

          <TextField
            label="Comment"
            multiline
            minRows={2}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
            helperText="Optional"
          />

          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={close.isPending}>
          Cancel
        </Button>
        <Button type="submit" variant="contained" color="error" disabled={close.isPending}>
          {close.isPending ? 'Closing…' : 'Close mission'}
        </Button>
      </DialogActions>
    </form>
  );
}
