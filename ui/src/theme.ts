import { alpha, createTheme } from "@mui/material/styles";

export const theme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#0f766e"
    },
    secondary: {
      main: "#f97316"
    },
    background: {
      default: "#f5efe4",
      paper: "#fffaf4"
    },
    text: {
      primary: "#12211d",
      secondary: "#4f5d56"
    }
  },
  shape: {
    borderRadius: 20
  },
  typography: {
    fontFamily: '"Manrope Variable", sans-serif',
    h1: {
      fontSize: "3.4rem",
      fontWeight: 800,
      letterSpacing: "-0.05em"
    },
    h2: {
      fontSize: "1.6rem",
      fontWeight: 700,
      letterSpacing: "-0.03em"
    },
    h3: {
      fontSize: "1.15rem",
      fontWeight: 700
    },
    button: {
      textTransform: "none",
      fontWeight: 700
    }
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: "none",
          boxShadow: `0 24px 60px ${alpha("#12211d", 0.08)}`
        }
      }
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 999,
          paddingInline: 18
        }
      }
    }
  }
});
