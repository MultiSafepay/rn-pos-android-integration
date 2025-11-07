import { FlashList } from '@shopify/flash-list';
import { Stack, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Text, View } from 'react-native';
import * as RnPosAndroidIntegration from 'rn-pos-android-integration';
import Pressable from 'src/components/pressable';
import { useCart } from 'src/providers/cart';
import type { PaymentStatus, Product } from 'src/types';
import Colors from 'src/utils/colors';
import { payOrder } from 'src/utils/pay';

import { useCurrencyFormatter } from 'src/utils/formatter';
import CartCell from 'src/components/cart-cell';

const Checkout = () => {
  const { items, addToCart, removeFromCart } = useCart();
  const currencyFormatter = useCurrencyFormatter();
  const router = useRouter();
  const [inProgress, setInProgress] = useState(false);

  const onAddToCart = useCallback(
    (product: Product) => {
      addToCart(product);
    },
    [addToCart]
  );

  const onRemoveFromCart = useCallback(
    (product: Product) => {
      removeFromCart(product);
    },
    [removeFromCart]
  );

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
    const suscription = RnPosAndroidIntegration.addTransactionListener(({ status }) => {
      const paymentStatus = ((): PaymentStatus => {
        switch (status) {
          case 'CANCELLED':
            return 'cancelled';
          case 'COMPLETED':
            return 'completed';
          case 'DECLINED':
            return 'declined';
          case 'EXCEPTION':
          default:
            return 'cancelled';
        }
      })();

      if (__DEV__) {
        console.log('🚀 Did receive transaction callback', { status, paymentStatus });
      }

      router.push({
        pathname: '/(modals)/confirmation',
        params: { status: paymentStatus },
      });
    });
    return () => {
      suscription.remove();
    };
  }, [router]);

  const totalAmount = useMemo(() => {
    return items.reduce((total, item) => total + item.product.price * item.quantity, 0);
  }, [items]);

  const totalLabel = useMemo(() => {
    return currencyFormatter.format(totalAmount);
  }, [currencyFormatter, totalAmount]);

  return (
    <View style={{ flex: 1, backgroundColor: Colors.white }}>
      <Stack.Screen />
      <FlashList
        numColumns={1}
        data={items}
        renderItem={({ item }) => {
          return (
            <CartCell
              product={item.product}
              quantity={item.quantity}
              onIncrease={() => {
                onAddToCart(item.product);
              }}
              onDecrease={() => {
                onRemoveFromCart(item.product);
              }}
            />
          );
        }}
      />
      <View
        style={{
          elevation: 1,
          shadowColor: '#000',
          shadowOffset: { width: 0, height: -1 },
          shadowOpacity: 0.1,
          shadowRadius: 5,
          borderTopLeftRadius: 10,
          borderTopRightRadius: 10,
        }}
      >
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', padding: 15 }}>
          <Text style={{ fontWeight: 'bold', fontSize: 20 }}>{'Total'}</Text>
          <Text style={{ fontWeight: 'bold', fontSize: 20 }}>{totalLabel}</Text>
        </View>
        <Pressable
          onPress={onPay}
          disabled={items.length === 0 || inProgress}
          style={{
            borderRadius: 5,
            margin: 10,
            padding: 15,
            justifyContent: 'center',
            alignItems: 'center',
            backgroundColor: Colors.primary,
          }}
        >
          <Text style={{ color: Colors.white, fontWeight: 'bold', fontSize: 16 }}>{'Place order'}</Text>
        </Pressable>
      </View>
    </View>
  );
};
export default Checkout;
