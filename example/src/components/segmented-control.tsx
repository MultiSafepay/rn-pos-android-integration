import { useState } from 'react';
import { Text, View } from 'react-native';
import { useAnimatedStyle, useDerivedValue, withSpring } from 'react-native-reanimated';

import Pressable from 'src/components/pressable';
import { AnimatedView } from 'src/components/ui';

export interface Option<T extends string> {
  label: string;
  value: T;
}

interface Props<T extends string> {
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
}

const PADDING = 4;

export default function SegmentedControl<T extends string>({ options, value, onChange }: Props<T>) {
  const [width, setWidth] = useState(0);
  const count = options.length;
  const segmentWidth = width > 0 ? (width - PADDING * 2) / count : 0;
  const selectedIndex = Math.max(
    options.findIndex((o) => o.value === value),
    0
  );

  const translateX = useDerivedValue(() =>
    withSpring(segmentWidth * selectedIndex, { damping: 18, stiffness: 220 })
  );
  const indicatorStyle = useAnimatedStyle(() => ({ transform: [{ translateX: translateX.value }] }));

  return (
    <View
      onLayout={(e) => setWidth(e.nativeEvent.layout.width)}
      className="flex-row rounded-full bg-surface-2"
      style={{ padding: PADDING, borderCurve: 'continuous' }}
    >
      {segmentWidth > 0 ? (
        <AnimatedView
          className="absolute rounded-full bg-primary"
          style={[
            { top: PADDING, bottom: PADDING, left: PADDING, width: segmentWidth, borderCurve: 'continuous' },
            indicatorStyle,
          ]}
        />
      ) : null}
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            onPress={() => onChange(option.value)}
            activeScale={0.98}
            className="flex-1 items-center justify-center py-2.5"
          >
            <Text className={`font-semibold ${selected ? 'text-on-primary' : 'text-muted'}`}>{option.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}
