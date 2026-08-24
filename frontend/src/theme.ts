import { createTheme } from '@mui/material/styles';

/**
 * MUI theme aligned to the palette already in `index.css`.
 *
 * The two styling systems coexist deliberately. The hand-rolled `mc-` classes still dress the
 * shell and the login form; MUI dresses the account menu, where a bare `<button>` and a
 * hand-written popover would mean reimplementing focus trapping, keyboard navigation and
 * click-away handling that `Menu` already does correctly.
 *
 * The colours are duplicated from the CSS custom properties rather than read from them, because
 * `getComputedStyle` at module scope is both fragile and untestable in jsdom. If `index.css`
 * changes, change these too - they are the same six values.
 *
 * Note there is no `CssBaseline`. It would reset the body styling that `index.css` owns, and the
 * existing pages are built on it.
 */
export const theme = createTheme({
  palette: {
    mode: 'dark',
    background: {
      default: '#0b1020',
      paper: '#151b30',
    },
    primary: {
      main: '#6ea8fe',
    },
    text: {
      primary: '#e6e9f2',
      secondary: '#9aa3bd',
    },
    divider: '#26304d',
  },
  shape: {
    borderRadius: 8,
  },
  typography: {
    fontFamily: "'Segoe UI', system-ui, -apple-system, sans-serif",
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: '1px solid #26304d',
        },
      },
    },
  },
});
