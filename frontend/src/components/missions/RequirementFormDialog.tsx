import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import {
  addRequirementMutation,
  listSkillsOptions,
  updateRequirementMutation,
} from '../../api/generated/@tanstack/react-query.gen';
import type { CrewRequirementResponse } from '../../api/generated/types.gen';
import { messageForProblem } from '../../lib/problemDetail';

/** The proficiency scale crew rate themselves on, so the two are directly comparable. */
const PROFICIENCIES = [1, 2, 3, 4, 5];

/** Matches the check constraint on the column. */
const WEIGHTS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

/** A skill row while it is being edited, before it is necessarily a valid request. */
interface SkillDraft {
  skillId: string;
  minimumProficiency: number;
  mandatory: boolean;
  weight: number;
}

interface Props {
  open: boolean;
  missionId: string;
  /** Absent when adding. */
  requirement?: CrewRequirementResponse;
  onClose: () => void;
}

/**
 * Adding and editing a crew requirement, skills included.
 *
 * The skills are edited here rather than through endpoints of their own, matching FR-8: a
 * requirement and the skills it calls for are one thing, and splitting them would let someone
 * leave a requirement half-described between two saves.
 *
 * Editing sends the whole requirement, so a skill removed here is removed on the server. That is
 * what the API does, and pretending otherwise in the UI would only hide it.
 */
export default function RequirementFormDialog({ open, missionId, requirement, onClose }: Props) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      {/* Mounted only while open, so the initial state is simply the requirement as it stands. */}
      {open && (
        <RequirementForm missionId={missionId} requirement={requirement} onClose={onClose} />
      )}
    </Dialog>
  );
}

function RequirementForm({ missionId, requirement, onClose }: Omit<Props, 'open'>) {
  const queryClient = useQueryClient();
  const editing = requirement !== undefined;

  const [title, setTitle] = useState(requirement?.title ?? '');
  const [description, setDescription] = useState(requirement?.description ?? '');
  const [requiredCount, setRequiredCount] = useState(String(requirement?.requiredCount ?? 1));
  const [skills, setSkills] = useState<SkillDraft[]>(
    requirement?.skills.map((skill) => ({
      skillId: skill.skillId,
      minimumProficiency: skill.minimumProficiency,
      mandatory: skill.mandatory,
      weight: skill.weight,
    })) ?? [],
  );
  const [error, setError] = useState<string | null>(null);

  // Only active skills can be put on a requirement, so only those are offered.
  const { data: catalogue } = useQuery(listSkillsOptions({ query: { active: true, size: 200 } }));
  const available = catalogue?.content ?? [];

  const add = useMutation(addRequirementMutation());
  const update = useMutation(updateRequirementMutation());
  const saving = add.isPending || update.isPending;

  const count = Number(requiredCount);
  const countValid = Number.isInteger(count) && count >= 1;
  const chosen = skills.map((skill) => skill.skillId).filter(Boolean);
  const hasDuplicate = new Set(chosen).size !== chosen.length;
  const hasUnchosen = skills.some((skill) => !skill.skillId);

  const canSave = title.trim().length > 0 && countValid && !hasDuplicate && !hasUnchosen && !saving;

  function updateSkill(index: number, patch: Partial<SkillDraft>) {
    setSkills((current) =>
      current.map((skill, position) => (position === index ? { ...skill, ...patch } : skill)),
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSave) return;
    setError(null);

    const body = {
      title: title.trim(),
      description: description.trim(),
      requiredCount: count,
      skills,
    };

    try {
      if (editing) {
        await update.mutateAsync({ path: { missionId, requirementId: requirement.id }, body });
      } else {
        await add.mutateAsync({ path: { missionId }, body });
      }

      // Requirements only appear inside a mission, so the mission reads are what need refreshing -
      // including the list, whose staffing totals have just changed.
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'getMission' }] });
      await queryClient.invalidateQueries({ queryKey: [{ _id: 'listMissions' }] });
      onClose();
    } catch (caught) {
      setError(messageForProblem(caught, 'Could not save the requirement.'));
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <DialogTitle>{editing ? 'Edit requirement' : 'Add requirement'}</DialogTitle>

      <DialogContent>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              label="Title"
              required
              autoFocus
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              slotProps={{ htmlInput: { maxLength: 200 } }}
            />
            <TextField
              label="Crew needed"
              type="number"
              required
              value={requiredCount}
              onChange={(event) => setRequiredCount(event.target.value)}
              error={requiredCount !== '' && !countValid}
              helperText={requiredCount !== '' && !countValid ? 'At least 1' : ' '}
              slotProps={{ htmlInput: { min: 1 } }}
              sx={{ maxWidth: { sm: '10rem' } }}
            />
          </Stack>

          <TextField
            label="Description"
            multiline
            minRows={2}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
          />

          <Box>
            <Typography variant="overline" color="text.secondary" component="h3">
              Required skills
            </Typography>

            {skills.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                No skills yet. A requirement with none matches anyone.
              </Typography>
            )}

            <Stack spacing={1.5}>
              {skills.map((skill, index) => (
                <Stack
                  // Index as key, deliberately: a row has no id until it is saved, and two blank
                  // rows would otherwise collide.
                  key={index}
                  direction={{ xs: 'column', sm: 'row' }}
                  spacing={1.5}
                  sx={{ alignItems: { sm: 'center' } }}
                >
                  <TextField
                    select
                    label="Skill"
                    required
                    value={skill.skillId}
                    onChange={(event) => updateSkill(index, { skillId: event.target.value })}
                    error={
                      skill.skillId !== '' && chosen.filter((id) => id === skill.skillId).length > 1
                    }
                  >
                    {available.map((option) => (
                      <MenuItem key={option.id} value={option.id}>
                        {option.name}
                      </MenuItem>
                    ))}
                  </TextField>

                  <TextField
                    select
                    label="Minimum"
                    value={skill.minimumProficiency}
                    onChange={(event) =>
                      updateSkill(index, { minimumProficiency: Number(event.target.value) })
                    }
                    sx={{ minWidth: '8rem' }}
                  >
                    {PROFICIENCIES.map((level) => (
                      <MenuItem key={level} value={level}>
                        {level}
                      </MenuItem>
                    ))}
                  </TextField>

                  <TextField
                    select
                    label="Weight"
                    value={skill.weight}
                    onChange={(event) => updateSkill(index, { weight: Number(event.target.value) })}
                    sx={{ minWidth: '7rem' }}
                  >
                    {WEIGHTS.map((weight) => (
                      <MenuItem key={weight} value={weight}>
                        {weight}
                      </MenuItem>
                    ))}
                  </TextField>

                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={skill.mandatory}
                        onChange={(event) =>
                          updateSkill(index, { mandatory: event.target.checked })
                        }
                      />
                    }
                    label="Mandatory"
                    sx={{ whiteSpace: 'nowrap' }}
                  />

                  <IconButton
                    aria-label={`Remove skill ${index + 1}`}
                    onClick={() =>
                      setSkills((current) => current.filter((_, position) => position !== index))
                    }
                  >
                    <DeleteOutlineIcon />
                  </IconButton>
                </Stack>
              ))}
            </Stack>

            <Button
              startIcon={<AddIcon />}
              onClick={() =>
                setSkills((current) => [
                  ...current,
                  { skillId: '', minimumProficiency: 3, mandatory: true, weight: 1 },
                ])
              }
              sx={{ mt: 1 }}
            >
              Add skill
            </Button>
          </Box>

          {hasDuplicate && <Alert severity="warning">Each skill can only be listed once.</Alert>}

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
