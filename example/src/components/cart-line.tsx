import { Text, View } from 'react-native';
import { FadeIn, FadeOutLeft, LinearTransition } from 'react-native-reanimated';

import ProductImage from 'src/components/product-image';
import QuantityStepper from 'src/components/quantity-stepper';
import { AnimatedView } from 'src/components/ui';
import type { CartItem } from 'src/types';
import { useCurrencyFormatter } from 'src/utils/formatter';

interface Props {
  item: CartItem;
  index: number;
  onIncrease: () => void;
  onDecrease: () => void;
}

export default function CartLine({ item, index, onIncrease, onDecrease }: Props) {
  const currencyFormatter = useCurrencyFormatter();
  const lineTotal = item.product.price * item.quantity;

  return (
    <AnimatedView
      entering={FadeIn.duration(260).delay(index * 40)}
      exiting={FadeOutLeft.duration(200)}
      layout={LinearTransition.springify().damping(18)}
      className="flex-row items-center gap-3 rounded-2xl bg-surface p-3"
      style={{ borderCurve: 'continuous', boxShadow: '0 4px 14px rgba(11,34,51,0.06)' }}
    >
      <View className="overflow-hidden rounded-xl bg-surface-2" style={{ width: 64, height: 64 }}>
        <ProductImage source={item.product.asset} recyclingKey={String(item.product.id)} transition={0} />
      </View>
      <View className="flex-1">
        <Text className="font-semibold text-content" numberOfLines={1}>
          {item.product.name}
        </Text>
        <Text className="mt-0.5 text-sm text-muted" style={{ fontVariant: ['tabular-nums'] }}>
          {currencyFormatter.format(lineTotal)}
        </Text>
      </View>
      <QuantityStepper quantity={item.quantity} onIncrease={onIncrease} onDecrease={onDecrease} />
    </AnimatedView>
  );
}
