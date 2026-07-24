import Ionicons from '@expo/vector-icons/Ionicons';
import { FlashList } from '@shopify/flash-list';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as RnPosAndroidIntegration from 'rn-pos-android-integration';

import CartLine from 'src/components/cart-line';
import EmptyState from 'src/components/empty-state';
import Pressable from 'src/components/pressable';
import ScreenHeader from 'src/components/screen-header';
import { useCart } from 'src/providers/cart';
import type { PaymentStatus, Product } from 'src/types';
import { useColors } from 'src/utils/colors';
import { useCurrencyFormatter } from 'src/utils/formatter';
import { payOrder } from 'src/utils/pay';

export default function Checkout() {
  const { items, addToCart, removeFromCart } = useCart();
  const currencyFormatter = useCurrencyFormatter();
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const [inProgress, setInProgress] = useState(false);

  const onAddToCart = useCallback((product: Product) => addToCart(product), [addToCart]);
  const onRemoveFromCart = useCallback((product: Product) => removeFromCart(product), [removeFromCart]);

  const onPay = useCallback(async () => {
    try {
      setInProgress(true);
      await payOrder({ cartItems: items });
    } catch (error) {
      if (__DEV__) {
        console.error(error);
      }
      Alert.alert('Error', (error as Error)?.message);
    } finally {
      setInProgress(false);
    }
  }, [items]);

  useEffect(() => {
    const subscription = RnPosAndroidIntegration.addTransactionListener(({ status }) => {
      const paymentStatus = ((): PaymentStatus => {
        switch (status) {
          case 'CANCELLED':
            return 'cancelled';
          case 'COMPLETED':
            return 'completed';
          case 'DECLINED':
            return 'declined';
          case 'UNDEFINED':
            return 'uncleared';
          case 'EXCEPTION':
          default:
            return 'cancelled';
        }
      })();

      if (__DEV__) {
        console.log('🚀 Did receive transaction callback', { status, paymentStatus });
      }

      router.push({ pathname: '/(modals)/confirmation', params: { status: paymentStatus } });
    });
    return () => subscription.remove();
  }, [router]);

  const { totalAmount, itemCount } = useMemo(
    () => ({
      totalAmount: items.reduce((total, item) => total + item.product.price * item.quantity, 0),
      itemCount: items.reduce((total, item) => total + item.quantity, 0),
    }),
    [items]
  );

  const isEmpty = items.length === 0;

  return (
    <View className="flex-1 bg-background">
      <ScreenHeader
        title="Order"
        subtitle={isEmpty ? 'No items yet' : `${itemCount} ${itemCount === 1 ? 'item' : 'items'} in this order`}
      />

      {isEmpty ? (
        <EmptyState
          icon="cart-outline"
          title="Your cart is empty"
          message="Add dishes from the menu to start a new order."
        />
      ) : (
        <>
          <FlashList
            data={items}
            keyExtractor={(item) => String(item.product.id)}
            contentContainerStyle={{ paddingHorizontal: 16, paddingTop: 4, paddingBottom: 16 }}
            ItemSeparatorComponent={() => <View style={{ height: 12 }} />}
            renderItem={({ item, index }) => (
              <CartLine
                item={item}
                index={index}
                onIncrease={() => onAddToCart(item.product)}
                onDecrease={() => onRemoveFromCart(item.product)}
              />
            )}
          />

          <View
            className="border-t border-hairline bg-surface px-5 pt-4"
            style={{ paddingBottom: insets.bottom + 12, boxShadow: '0 -6px 20px rgba(11,34,51,0.08)' }}
          >
            <View className="mb-1 flex-row items-center justify-between">
              <Text className="text-muted">Subtotal</Text>
              <Text className="text-muted" style={{ fontVariant: ['tabular-nums'] }}>
                {currencyFormatter.format(totalAmount)}
              </Text>
            </View>
            <View className="mb-4 flex-row items-center justify-between">
              <Text className="text-lg font-bold text-content">Total</Text>
              <Text className="text-2xl font-extrabold text-content" style={{ fontVariant: ['tabular-nums'] }}>
                {currencyFormatter.format(totalAmount)}
              </Text>
            </View>

            <Pressable
              onPress={onPay}
              disabled={inProgress}
              className="h-14 flex-row items-center justify-center gap-2 rounded-2xl bg-primary"
              style={{ borderCurve: 'continuous', opacity: inProgress ? 0.7 : 1 }}
            >
              {inProgress ? (
                <ActivityIndicator color={colors.onPrimary} />
              ) : (
                <Ionicons name="card" size={20} color={colors.onPrimary} />
              )}
              <Text className="text-base font-bold text-on-primary">
                {inProgress ? 'Processing…' : `Charge ${currencyFormatter.format(totalAmount)}`}
              </Text>
            </Pressable>
          </View>
        </>
      )}
    </View>
  );
}
