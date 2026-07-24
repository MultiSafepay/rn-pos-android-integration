import { useColorScheme } from 'react-native';

// MultiSafepay brand blues (from the official logo). Kept in sync with the
// design tokens in src/global.css. Use these ONLY where raw hex is required in
// JS — the navigation theme, Reanimated interpolations and icon `color` props.
// Everything visual in the UI is styled with Uniwind className + those tokens.
export const brand = '#00abee';
export const ink = '#004a71';

export interface Palette {
  background: string;
  surface: string;
  surface2: string;
  content: string;
  muted: string;
  hairline: string;
  primary: string;
  onPrimary: string;
  brand: string;
  success: string;
  danger: string;
}

export const palettes: Record<'light' | 'dark', Palette> = {
  light: {
    background: '#eef2f6',
    surface: '#ffffff',
    surface2: '#f7fafc',
    content: '#0b2233',
    muted: '#5b6b7b',
    hairline: '#e3e9ef',
    primary: '#004a71',
    onPrimary: '#ffffff',
    brand,
    success: '#0a8f5b',
    danger: '#d64545',
  },
  dark: {
    background: '#061019',
    surface: '#0f2231',
    surface2: '#13293b',
    content: '#eaf2f8',
    muted: '#8ba0b2',
    hairline: '#1d3547',
    primary: '#00abee',
    onPrimary: '#04202f',
    brand,
    success: '#34d399',
    danger: '#f87171',
  },
};

export function useColors(): Palette {
  return palettes[useColorScheme() === 'dark' ? 'dark' : 'light'];
}

const Colors = palettes;
export default Colors;
