import { Image as ExpoImage } from 'expo-image';
import Animated from 'react-native-reanimated';
import { withUniwind } from 'uniwind';

// Core react-native components accept className directly in Uniwind, but
// third-party / Animated components must be wrapped with withUniwind (Uniwind's
// equivalent of NativeWind's cssInterop) to map className -> style.
export const AnimatedView = withUniwind(Animated.View);
export const Image = withUniwind(ExpoImage);
