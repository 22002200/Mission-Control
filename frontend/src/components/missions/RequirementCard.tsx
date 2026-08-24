import MoreVertIcon from '@mui/icons-material/MoreVert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import type { CrewRequirementResponse } from '../../api/generated/types.gen';

/**
 * One staffing line on the mission detail page.
 *
 * The skills are a small table rather than a row of chips: there are four values per skill and a
 * chip that has to carry all four stops being readable at about the second one.
 *
 * Mandatory shows as a filled chip and preferred as an outlined one, because the difference
 * decides whether a candidate is filtered out or merely ranked lower - it is the most consequential
 * field in the row and a word alone is easy to skim past.
 */
export default function RequirementCard({
  requirement,
  editable,
  onEdit,
  onDelete,
}: {
  requirement: CrewRequirementResponse;
  editable: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const filled = requirement.acceptedCount >= requirement.requiredCount;

  return (
    <Card sx={{ p: 2 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            {requirement.title}
          </Typography>
          {requirement.description && (
            <Typography variant="body2" color="text.secondary">
              {requirement.description}
            </Typography>
          )}
        </Box>

        <Typography
          variant="body2"
          sx={{
            fontWeight: 600,
            whiteSpace: 'nowrap',
            color: filled ? 'success.main' : 'text.secondary',
          }}
        >
          {requirement.acceptedCount} of {requirement.requiredCount} accepted
        </Typography>

        {editable && (
          <>
            <IconButton
              size="small"
              aria-label={`Actions for ${requirement.title}`}
              onClick={(event) => setAnchorEl(event.currentTarget)}
            >
              <MoreVertIcon fontSize="small" />
            </IconButton>
            <Menu
              anchorEl={anchorEl}
              open={Boolean(anchorEl)}
              onClose={() => setAnchorEl(null)}
              slotProps={{ paper: { sx: { border: 1, borderColor: 'divider' } } }}
            >
              <MenuItem
                onClick={() => {
                  setAnchorEl(null);
                  onEdit();
                }}
              >
                Edit
              </MenuItem>
              <MenuItem
                onClick={() => {
                  setAnchorEl(null);
                  onDelete();
                }}
              >
                Remove
              </MenuItem>
            </Menu>
          </>
        )}
      </Stack>

      {requirement.skills.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>
          No skills required, so anyone can fill this.
        </Typography>
      ) : (
        <Table size="small" sx={{ mt: 1.5 }}>
          <TableHead>
            <TableRow>
              <TableCell>Skill</TableCell>
              <TableCell align="right">Minimum</TableCell>
              <TableCell>Requirement</TableCell>
              <TableCell align="right">Weight</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {requirement.skills.map((skill) => (
              <TableRow key={skill.skillId}>
                <TableCell>{skill.skillName}</TableCell>
                <TableCell align="right">{skill.minimumProficiency}</TableCell>
                <TableCell>
                  <Chip
                    label={skill.mandatory ? 'Mandatory' : 'Preferred'}
                    size="small"
                    variant={skill.mandatory ? 'filled' : 'outlined'}
                    color={skill.mandatory ? 'primary' : 'default'}
                  />
                </TableCell>
                <TableCell align="right">{skill.weight}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Card>
  );
}
