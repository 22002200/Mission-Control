import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import LogoutIcon from '@mui/icons-material/Logout';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useAuth } from '../auth/useAuth';

const MENU_ID = 'account-menu';
const BUTTON_ID = 'account-menu-button';

/**
 * Who you are signed in as, and the way out.
 *
 * Pinned to the top-right corner of the viewport rather than sitting in the page flow, so it stays
 * put as the content below it grows.
 *
 * Built on MUI's `Menu` rather than a hand-rolled popover: it already handles focus trapping,
 * arrow-key navigation, Escape, click-away and returning focus to the trigger. Those are the parts
 * of a dropdown that are easy to leave out and awkward to retrofit.
 */
export default function AccountMenu() {
  const { user, logout } = useAuth();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [signingOut, setSigningOut] = useState(false);

  const open = Boolean(anchorEl);

  if (!user) return null;

  async function handleLogout() {
    setAnchorEl(null);
    setSigningOut(true);
    // logout() clears local state even when the request fails, so there is no failure branch:
    // this component unmounts either way.
    await logout();
  }

  return (
    <Box
      sx={{
        position: 'fixed',
        top: 16,
        right: 24,
        zIndex: (muiTheme) => muiTheme.zIndex.appBar,
      }}
    >
      <Button
        id={BUTTON_ID}
        aria-controls={open ? MENU_ID : undefined}
        aria-haspopup="true"
        aria-expanded={open ? 'true' : undefined}
        onClick={(event) => setAnchorEl(event.currentTarget)}
        disabled={signingOut}
        color="inherit"
        startIcon={
          <Avatar sx={{ width: 28, height: 28, fontSize: 13, bgcolor: 'primary.main',
                        color: 'background.default' }}>
            {initials(user.fullName)}
          </Avatar>
        }
        endIcon={<KeyboardArrowDownIcon />}
        sx={{
          textTransform: 'none',
          borderRadius: 999,
          pl: 0.75,
          pr: 1.5,
          py: 0.5,
          gap: 0.5,
          border: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
          '&:hover': { bgcolor: 'background.paper', borderColor: 'primary.main' },
        }}
      >
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {user.fullName}
        </Typography>
      </Button>

      <Menu
        id={MENU_ID}
        anchorEl={anchorEl}
        open={open}
        onClose={() => setAnchorEl(null)}
        slotProps={{
          list: { 'aria-labelledby': BUTTON_ID, dense: true },
          paper: { sx: { minWidth: 240, mt: 1 } },
        }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        {/* Not a MenuItem: it is a label, so it must not be focusable or selectable. */}
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {user.fullName}
          </Typography>
          <Typography variant="caption" color="text.secondary" component="p">
            {formatRole(user.role)} · {user.organisationName}
          </Typography>
          <Typography variant="caption" color="text.secondary" component="p">
            {user.email}
          </Typography>
        </Box>

        <Divider />

        <MenuItem onClick={handleLogout} disabled={signingOut}>
          <ListItemIcon>
            <LogoutIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{signingOut ? 'Signing out…' : 'Sign out'}</ListItemText>
        </MenuItem>
      </Menu>
    </Box>
  );
}

/** `MISSION_LEAD` reads badly in a UI; the wire format is not the display format. */
function formatRole(role: string): string {
  return role
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0]!.charAt(0);
  const last = parts.length > 1 ? parts[parts.length - 1]!.charAt(0) : '';
  return (first + last).toUpperCase();
}
