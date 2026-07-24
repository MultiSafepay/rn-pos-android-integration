import Ionicons from '@expo/vector-icons/Ionicons';
import { Text, View } from 'react-native';
import { FadeInDown, FadeOutDown } from 'react-native-reanimated';

import Pressable from 'src/components/pressable';
import { AnimatedView } from 'src/components/ui';
import { useColors } from 'src/utils/colors';
import { useCurrencyFormatter } from 'src/utils/formatter';

interface Props {
  count: number;
  total: number;
  onPress: () => void;
}

export default function FloatingCartBar({ count, total, onPress }: Props) {
  const currencyFormatter = useCurrencyFormatter();
  const colors = useColors();

  if (count <= 0) {
    return null;
  }

  return (
    <AnimatedView
      entering={FadeInDown.duration(280)}
      exiting={FadeOutDown.duration(200)}
      className="absolute bottom-4 left-4 right-4"
    >
      <Pressable
        onPress={onPress}
        className="flex-row items-center justify-between rounded-2xl bg-primary px-5 py-3.5"
        style={{ borderCurve: 'continuous', boxShadow: '0 10px 26px rgba(0,74,113,0.4)' }}
      >
        <View className="flex-row items-center gap-3">
          <View
            className="h-8 w-8 items-center justify-center rounded-full"
            style={{ backgroundColor: 'rgba(255,255,255,0.18)' }}
          >
            <Ionicons name="bag-handle" size={17} color={colors.onPrimary} />
          </View>
          <Text className="font-semibold text-on-primary">
            {count} {count === 1 ? 'item' : 'items'}
          </Text>
        </View>
        <View className="flex-row items-center gap-2">
          <Text className="text-base font-extrabold text-on-primary" style={{ fontVariant: ['tabular-nums'] }}>
            {currencyFormatter.format(total)}
          </Text>
          <Ionicons name="chevron-forward" size={18} color={colors.onPrimary} />
        </View>
      </Pressable>
    </AnimatedView>
  );
}
