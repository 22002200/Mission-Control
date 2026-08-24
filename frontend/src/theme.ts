import { createTheme } from '@mui/material/styles';

/**
 * The single source of styling for the application.
 *
 * The hand-rolled `mc-` classes this replaced are gone: with the mission screens the app went from
 * one authenticated view to several, and keeping two styling systems in step across them was a
 * cost with nothing on the other side. `index.css` now holds only the palette custom properties
 * and the page background.
 *
 * The colours are duplicated from those custom properties rather than read from them, because
 * `getComputedStyle` at module scope is both fragile and untestable in jsdom. If `index.css`
 * changes, change these too - they are the same values.
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
    // Added for mission status chips. Tuned to sit on the dark surface above rather than taken
    // from MUI's defaults, which are pitched for a light background and glare against it.
    success: { main: '#5bd6a4' },
    warning: { main: '#e8b567' },
    error: { main: '#ff8f8f' },
    info: { main: '#7fb8ff' },
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
    h1: { fontSize: '1.75rem', fontWeight: 600, letterSpacing: '-0.01em' },
    h2: { fontSize: '1.35rem', fontWeight: 600 },
    // The small uppercase heading the section titles and card headers use.
    overline: { fontSize: '0.75rem', fontWeight: 600, letterSpacing: '0.08em' },
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
    // The border lives on Card rather than on Paper. Putting it on Paper, as an earlier version
    // did, also outlined every Menu, Dialog and Select popover, which then needed undoing in three
    // places.
    MuiCard: {
      styleOverrides: {
        root: {
          border: '1px solid #26304d',
        },
      },
    },
    MuiTextField: {
      defaultProps: { size: 'small', fullWidth: true },
    },
    MuiSelect: {
      defaultProps: { size: 'small' },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { textTransform: 'none', fontWeight: 600 },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600 },
      },
    },
    MuiAppBar: {
      defaultProps: { elevation: 0, color: 'transparent' },
      styleOverrides: {
        root: {
          backgroundColor: '#0b1020',
          borderBottom: '1px solid #26304d',
        },
      },
    },
  },
});
