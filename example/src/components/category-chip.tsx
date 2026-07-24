import { Text } from 'react-native';

import Pressable from 'src/components/pressable';

interface Props {
  label: string;
  selected: boolean;
  onPress: () => void;
}

export default function CategoryChip({ label, selected, onPress }: Props) {
  return (
    <Pressable
      onPress={onPress}
      className={`rounded-full border px-4 py-2 ${
        selected ? 'border-primary bg-primary' : 'border-hairline bg-surface'
      }`}
      style={{ borderCurve: 'continuous' }}
    >
      <Text className={`font-semibold ${selected ? 'text-on-primary' : 'text-muted'}`}>{label}</Text>
    </Pressable>
  );
}
