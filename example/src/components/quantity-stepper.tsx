import Ionicons from '@expo/vector-icons/Ionicons';
import { Text, View } from 'react-native';

import Pressable, { hitSlop } from 'src/components/pressable';
import { useColors } from 'src/utils/colors';

interface Props {
  quantity: number;
  onIncrease: () => void;
  onDecrease: () => void;
}

export default function QuantityStepper({ quantity, onIncrease, onDecrease }: Props) {
  const colors = useColors();

  return (
    <View className="flex-row items-center gap-1 rounded-full bg-surface-2 p-1" style={{ borderCurve: 'continuous' }}>
      <Pressable
        onPress={onDecrease}
        hitSlop={hitSlop}
        className="h-8 w-8 items-center justify-center rounded-full bg-surface"
      >
        <Ionicons name="remove" size={20} color={colors.content} />
      </Pressable>
      <Text className="w-7 text-center font-bold text-content" style={{ fontVariant: ['tabular-nums'] }}>
        {quantity}
      </Text>
      <Pressable
        onPress={onIncrease}
        hitSlop={hitSlop}
        className="h-8 w-8 items-center justify-center rounded-full bg-brand"
      >
        <Ionicons name="add" size={20} color="#ffffff" />
      </Pressable>
    </View>
  );
}
