import type { ReactNode } from 'react';
import { Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import BrandLogo from 'src/components/brand-logo';

interface Props {
  title: string;
  subtitle?: string;
  right?: ReactNode;
}

/**
 * Branded in-screen header. Native tabs don't render a JS header, so each tab
 * provides its own — giving us full control and a consistent MultiSafepay look.
 */
export default function ScreenHeader({ title, subtitle, right }: Props) {
  const insets = useSafeAreaInsets();

  return (
    <View className="bg-background px-5 pb-3" style={{ paddingTop: insets.top + 10 }}>
      <View className="mb-3 flex-row items-center justify-between">
        <BrandLogo height={18} />
        {right}
      </View>
      <Text className="text-3xl font-extrabold text-content">{title}</Text>
      {subtitle ? <Text className="mt-1 text-base text-muted">{subtitle}</Text> : null}
    </View>
  );
}
