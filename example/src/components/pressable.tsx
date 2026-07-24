import { forwardRef } from 'react';
import { Pressable as RNPressable, type PressableProps } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { withUniwind } from 'uniwind';

const AnimatedPressable = withUniwind(Animated.createAnimatedComponent(RNPressable));

export const hitSlop = { bottom: 10, left: 10, right: 10, top: 10 };

export interface Props extends PressableProps {
  className?: string;
  /** How far the press-in scale shrinks. Defaults to a subtle 0.96. */
  activeScale?: number;
}

/**
 * A Pressable that gently scales down while pressed (Reanimated) for a tactile,
 * native-feeling touch response. Accepts Uniwind className.
 */
const Pressable = forwardRef<React.ComponentRef<typeof RNPressable>, Props>(
  ({ activeScale = 0.96, disabled, onPressIn, onPressOut, style, children, ...props }, ref) => {
    const scale = useSharedValue(1);
    const animatedStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));

    return (
      <AnimatedPressable
        ref={ref}
        disabled={disabled}
        style={[animatedStyle, style as object]}
        onPressIn={(e) => {
          scale.value = withTiming(activeScale, { duration: 90 });
          onPressIn?.(e);
        }}
        onPressOut={(e) => {
          scale.value = withTiming(1, { duration: 160 });
          onPressOut?.(e);
        }}
        {...props}
      >
        {children as React.ReactNode}
      </AnimatedPressable>
    );
  }
);

Pressable.displayName = 'Pressable';

export default Pressable;
