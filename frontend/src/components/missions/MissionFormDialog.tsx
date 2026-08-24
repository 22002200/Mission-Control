import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { useState, type FormEvent } from 'react';
import {
  createMissionMutation,
  updateMissionMutation,
} from '../../api/generated/@tanstack/react-query.gen';
import type { MissionResponse } from '../../api/generated/types.gen';
import { fromApi, isValid, toApi } from '../../lib/datetime';
import { messageForProblem } from '../../lib/problemDetail';

interface Props {
  open: boolean;
  /** Absent when creating. */
  mission?: MissionResponse;
  onClose: () => void;
  onSaved?: (mission: MissionResponse) => void;
}

/**
 * Creating a mission, and editing one.
 *
 * One dialog for both, because the fields are identical and the only difference is which request
 * it sends. Two nearly-identical dialogs would drift the first time a field was added.
 *
 * The form is mounted only while the dialog is open. That is what makes its initial state simply
 * be the mission as it stands, rather than something an effect has to copy in - and an effect that
 * calls setState on open is a cascading render the React lint rules rightly object to.
 */
export default function MissionFormDialog({ open, mission, onClose, onSaved }: Props) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      {open && <MissionForm mission={mission} onClose={onClose} onSaved={onSaved} />}
    </Dialog>
  );
}

function MissionForm({ mission, onClose, onSaved }: Omit<Props, 'open'>) {
  const queryClient = useQueryClient();
  const editing = mission !== undefined;

  const [name, setName] = useState(mission?.name ?? '');
  const [description, setDescription] = useState(mission?.description ?? '');
  const [startsAt, setStartsAt] = useState<Dayjs | null>(
    mission ? fromApi(mission.startsAt) : null,
  );
  const [endsAt, setEndsAt] = useState<Dayjs | null>(mission ? fromApi(mission.endsAt) : null);
  const [error, setError] = useState<string | null>(null);

  const create = useMutation(createMissionMutation());
  const update = useMutation(updateMissionMutation());
  const saving = create.isPending || update.isPending;

  // Invariant M1, checked here as well as on the server so the reason shows up next to the field
  // rather than as a sentence at the bottom after a round trip.
  const datesOutOfOrder = isValid(startsAt) && isValid(endsAt) && !endsAt.isAfter(startsAt);

  const canSave =
    name.trim().length > 0 && isValid(startsAt) && isValid(endsAt) && !datesOutOfOrder && !saving;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSave || !isValid(startsAt) || !isValid(endsAt)) return;
    setError(null);

    const body = {
      name: name.trim(),
      description: description.trim(),
      startsAt: toApi(startsAt),
      endsAt: toApi(endsAt),
    };

    try {
      const saved = editing
        ? await update.mutateAsync({ path: { id: mission.id }, body })
        : await create.mutateAsync({ body });

      // Partial key matching: the generated query key embeds the exact filter, and a saved mission
      // could belong to any section, so every mission query is invalidated rather than one.
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissions' }] });
      if (editing) {
        await queryClient.invalidateQueries({ queryKey: [{ _id: 'getMission' }] });
      }

      onSaved?.(saved);
      onClose();
    } catch (caught) {
      setError(messageForProblem(caught, 'Could not save the mission.'));
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <DialogTitle>{editing ? 'Edit mission' : 'New mission'}</DialogTitle>

      <DialogContent>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          {/* Warned about before they commit, not after. Reverting an approved mission to
              planning is invariant M5 and it is not obvious from the Save button. */}
          {editing && (mission.status === 'APPROVED' || mission.status === 'ACTIVE') && (
            <Alert severity="warning">
              Saving returns this mission to planning. It will need approving again.
            </Alert>
          )}

          <TextField
            label="Name"
            required
            autoFocus
            value={name}
            onChange={(event) => setName(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 200 } }}
          />

          <TextField
            label="Description"
            multiline
            minRows={2}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />

          <DateTimePicker
            label="Starts at"
            value={startsAt}
            onChange={setStartsAt}
            slotProps={{ textField: { required: true, helperText: 'Your local time' } }}
          />

          <DateTimePicker
            label="Ends at"
            value={endsAt}
            onChange={setEndsAt}
            minDateTime={startsAt ?? undefined}
            slotProps={{
              textField: {
                required: true,
                error: datesOutOfOrder,
                helperText: datesOutOfOrder ? 'Must be after the start' : 'Your local time',
              },
            }}
          />

          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" variant="contained" disabled={!canSave}>
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </DialogActions>
    </form>
  );
}
