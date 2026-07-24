import { Image } from 'expo-image';
import { useColorScheme } from 'react-native';

const WORDMARK = {
  color: require('../../assets/msp-logo-color.png'),
  white: require('../../assets/msp-logo-white.png'),
};
const GLYPH = {
  color: require('../../assets/msp-glyph-color.png'),
  white: require('../../assets/msp-glyph-white.png'),
};

const WORDMARK_RATIO = 866 / 148;

interface Props {
  height?: number;
  variant?: 'wordmark' | 'glyph';
  /** Override automatic light/dark tone selection. */
  tone?: 'color' | 'white';
}

export default function BrandLogo({ height = 20, variant = 'wordmark', tone }: Props) {
  const scheme = useColorScheme();
  const resolvedTone = tone ?? (scheme === 'dark' ? 'white' : 'color');
  const source = variant === 'wordmark' ? WORDMARK[resolvedTone] : GLYPH[resolvedTone];
  const width = variant === 'wordmark' ? height * WORDMARK_RATIO : height;

  return <Image source={source} style={{ width, height }} contentFit="contain" />;
}
