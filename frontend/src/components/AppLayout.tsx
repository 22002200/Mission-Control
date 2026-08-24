import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { NavLink, Outlet, useMatch } from 'react-router';
import AccountMenu from './AccountMenu';

/**
 * The frame every authenticated screen sits in.
 *
 * A top bar rather than a sidebar: there is one destination today and a handful after features 05
 * to 08, which is not enough to justify a permanent column of navigation taking width away from
 * the mission board.
 *
 * The account menu moved in here from its old fixed position in the viewport corner. Once there is
 * a real bar to put it in, pinning it over the content is just a way to make it overlap something
 * eventually.
 */
export default function AppLayout() {
  return (
    <Box sx={{ minHeight: '100vh' }}>
      <AppBar position="sticky">
        <Toolbar sx={{ gap: 3 }}>
          <Typography
            component={NavLink}
            to="/missions"
            variant="h6"
            sx={{
              fontWeight: 700,
              letterSpacing: '-0.01em',
              color: 'text.primary',
              textDecoration: 'none',
            }}
          >
            Mission Control
          </Typography>

          <Box component="nav" sx={{ display: 'flex', gap: 1, flexGrow: 1 }}>
            <NavItem to="/missions">Missions</NavItem>
          </Box>

          <AccountMenu />
        </Toolbar>
      </AppBar>

      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  );
}

/**
 * A navigation link that knows whether it is the current page.
 *
 * The active state comes from `useMatch` rather than from `NavLink`'s `isActive` render prop,
 * because that prop only exists on `style` and `className` - neither of which composes with `sx`,
 * and typing a render function into `style` does not satisfy MUI's props. Asking the router
 * directly keeps the colour in the theme where the rest of the styling lives.
 *
 * The wildcard makes `/missions/{id}` count as being on Missions, which is what someone looking at
 * one mission expects the bar to say.
 */
function NavItem({ to, children }: { to: string; children: string }) {
  const isActive = useMatch({ path: to, end: false }) !== null;

  return (
    <Typography
      component={NavLink}
      to={to}
      variant="body2"
      sx={{
        fontWeight: 600,
        px: 1,
        py: 0.5,
        borderRadius: 1,
        textDecoration: 'none',
        color: isActive ? 'primary.main' : 'text.secondary',
      }}
    >
      {children}
    </Typography>
  );
}
