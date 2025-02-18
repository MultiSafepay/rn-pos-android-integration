import { FC, useMemo } from 'react';
import { Pressable as PresableNative, PressableProps, ViewStyle, StyleProp, Platform } from 'react-native';

export interface ITextInputProps extends PressableProps {
  style?: StyleProp<ViewStyle>;
  activeStyle?: StyleProp<ViewStyle>; // general purpose
  opacity?: number; // overriten by activeStyle
  activeBackground?: string; // overriten by activeStyle
}

export const hitSlop = { bottom: 10, left: 10, right: 10, top: 10 };
/**
 * children should be
 */
const Pressable: FC<ITextInputProps> = ({
  disabled,
  style,
  children,
  opacity,
  activeBackground,
  activeStyle,
  ...props
}) => {
  const activeStyleMemo: StyleProp<ViewStyle> = useMemo(() => {
    if (activeStyle) {
      return activeStyle;
    }
    if (activeBackground) {
      // add transparency to match android's android_ripple color
      return Platform.OS === 'ios' ? { backgroundColor: `${activeBackground}99` } : {};
    }
    return { opacity: opacity ?? 0.4 };
  }, [activeStyle, opacity, activeBackground]);

  return (
    <PresableNative
      disabled={disabled}
      hitSlop={hitSlop}
      android_ripple={activeBackground ? { color: activeBackground } : undefined}
      style={({ pressed }) => [{ opacity: disabled ? 0.3 : 1 }, style, pressed && activeStyleMemo]}
      {...props}
    >
      {children}
    </PresableNative>
  );
};
export default Pressable;
