import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import type { CandidateResponse } from '../../api/generated/types.gen';

/**
 * One suggested crew member, with the reasoning one click away.
 *
 * Collapsed it is a name and a score, because a mission lead comparing three candidates wants to
 * compare them rather than read three tables. Expanded it answers "why is this person ranked
 * here", which FR-4 requires and which the mandatory-skill rule genuinely needs - being ranked
 * below somebody less qualified is surprising until the numbers are on screen.
 *
 * Built from a toggle and a `Collapse` rather than an `Accordion`, which would have been the
 * obvious choice. `AccordionSummary` renders a button, and the Draft action has to sit on the
 * collapsed row, so an accordion puts one button inside another - invalid HTML, and the inner one
 * is unreliable for anybody using a keyboard or a screen reader.
 *
 * The skills table repeats the column shape `RequirementCard` uses, so a requirement and a
 * candidate for it read as two views of the same object.
 */
export default function CandidateRow({
  candidate,
  onPin,
  pinDisabledReason,
}: {
  candidate: CandidateResponse;
  onPin?: () => void;
  pinDisabledReason?: string;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, px: 1.5, py: 1 }}>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
        <IconButton
          size="small"
          onClick={() => setExpanded((open) => !open)}
          aria-expanded={expanded}
          aria-label={`Why ${candidate.fullName} is ranked here`}
        >
          {expanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
        </IconButton>

        <Typography sx={{ flexGrow: 1, fontWeight: 500 }}>{candidate.fullName}</Typography>

        {candidate.shortfalls.length > 0 && (
          <Chip
            label={`Short on ${candidate.shortfalls.length}`}
            size="small"
            color="warning"
            variant="outlined"
          />
        )}

        <Typography sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
          {candidate.score.toFixed(3)}
        </Typography>

        {onPin && (
          <Button
            size="small"
            variant="outlined"
            disabled={Boolean(pinDisabledReason)}
            title={pinDisabledReason}
            onClick={onPin}
          >
            Draft
          </Button>
        )}
      </Stack>

      <Collapse in={expanded} unmountOnExit>
        <Box sx={{ pt: 1.5, pb: 0.5 }}>
          {candidate.skills.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              This requirement asks for no skills, so everyone available fits it exactly.
            </Typography>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Skill</TableCell>
                  <TableCell align="right">Needs</TableCell>
                  <TableCell align="right">Has</TableCell>
                  <TableCell>Requirement</TableCell>
                  <TableCell align="right">Contribution</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {candidate.skills.map((skill) => (
                  <TableRow key={skill.skillId}>
                    <TableCell>{skill.skillName}</TableCell>
                    <TableCell align="right">{skill.required}</TableCell>
                    <TableCell align="right">
                      {skill.actual === 0 ? (
                        <Typography variant="body2" color="text.secondary" component="span">
                          none
                        </Typography>
                      ) : (
                        skill.actual
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={skill.mandatory ? 'Mandatory' : 'Preferred'}
                        size="small"
                        variant={skill.mandatory ? 'filled' : 'outlined'}
                        color={skill.mandatory ? 'primary' : 'default'}
                      />
                    </TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {skill.contribution.toFixed(2)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>
            Skill fit {candidate.breakdown.skillScore.toFixed(3)}
            {' · '}
            Experience +{candidate.breakdown.experienceBonus.toFixed(3)} (
            {candidate.breakdown.completedMissions} completed)
            {' · '}
            Load −{candidate.breakdown.loadPenalty.toFixed(3)} (
            {candidate.breakdown.recentAssignments} recent)
          </Typography>
        </Box>
      </Collapse>
    </Box>
  );
}
