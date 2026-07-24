import Ionicons from '@expo/vector-icons/Ionicons';
import type { ComponentProps } from 'react';
import { Text, View } from 'react-native';

import { useColors } from 'src/utils/colors';

interface Props {
  icon: ComponentProps<typeof Ionicons>['name'];
  title: string;
  message: string;
}

export default function EmptyState({ icon, title, message }: Props) {
  const colors = useColors();

  return (
    <View className="flex-1 items-center justify-center px-10">
      <View
        className="mb-5 h-20 w-20 items-center justify-center rounded-full bg-surface-2"
        style={{ borderCurve: 'continuous' }}
      >
        <Ionicons name={icon} size={38} color={colors.muted} />
      </View>
      <Text className="text-center text-xl font-bold text-content">{title}</Text>
      <Text className="mt-2 text-center text-base text-muted">{message}</Text>
    </View>
  );
}
