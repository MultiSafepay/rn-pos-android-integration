import Ionicons from '@expo/vector-icons/Ionicons';
import { Platform, Text, View, type ViewStyle } from 'react-native';
import { FadeInDown } from 'react-native-reanimated';

import Pressable from 'src/components/pressable';
import ProductImage from 'src/components/product-image';
import { AnimatedView } from 'src/components/ui';
import type { Product } from 'src/types';
import { useCurrencyFormatter } from 'src/utils/formatter';

// Blurred box-shadows are rasterized per-frame on Android and tank scroll perf on
// low-end devices. Keep the soft shadow on iOS; use hardware-accelerated
// `elevation` on Android (composited from the view outline — cheap while scrolling).
const cardShadow = Platform.select<ViewStyle>({
  ios: { boxShadow: '0 8px 22px rgba(11,34,51,0.08)' },
  android: { elevation: 3, shadowColor: '#0b2233' },
  default: {},
});

const buttonShadow = Platform.select<ViewStyle>({
  ios: { boxShadow: '0 4px 12px rgba(0,171,238,0.45)' },
  android: { elevation: 4, shadowColor: '#00abee' },
  default: {},
});

interface Props {
  product: Product;
  index: number;
  onAdd: () => void;
}

export default function ProductCard({ product, index, onAdd }: Props) {
  const currencyFormatter = useCurrencyFormatter();

  return (
    <AnimatedView
      entering={FadeInDown.duration(360).delay(Math.min(index, 12) * 45)}
      className="m-2 flex-1 rounded-3xl bg-surface"
      style={[{ borderCurve: 'continuous' }, cardShadow]}
    >
      <View className="overflow-hidden rounded-t-3xl bg-surface-2" style={{ aspectRatio: 4 / 3 }}>
        <ProductImage source={product.asset} recyclingKey={String(product.id)} />
      </View>
      <View className="gap-1 p-3.5">
        <Text className="text-base font-semibold text-content" numberOfLines={1}>
          {product.name}
        </Text>
        {product.description ? (
          <Text className="text-xs text-muted" numberOfLines={1}>
            {product.description}
          </Text>
        ) : null}
        <View className="mt-2 flex-row items-center justify-between">
          <Text className="text-lg font-extrabold text-content" style={{ fontVariant: ['tabular-nums'] }}>
            {currencyFormatter.format(product.price)}
          </Text>
          <Pressable
            onPress={onAdd}
            className="h-10 w-10 items-center justify-center rounded-full bg-brand"
            style={buttonShadow}
          >
            <Ionicons name="add" size={24} color="#ffffff" />
          </Pressable>
        </View>
      </View>
    </AnimatedView>
  );
}
