import { FlashList } from '@shopify/flash-list';
import { Link, Stack } from 'expo-router';
import { useCallback, useEffect } from 'react';
import { Alert, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as RnPosAndroidIntegration from 'rn-pos-android-integration';
import Pressable from 'src/components/pressable';
import { useCart } from 'src/providers/cart';
import { Product } from 'src/types';
import Colors from 'src/utils/colors';
import { payOrder } from 'src/utils/pay';

import ProductCell from './components/product-cell';

const products: Product[] = [
  {
    id: 1,
    name: 'Cheeseburger',
    price: 0.1,
    image: 'https://cdn-test.sitedish.nl/javier.testrestaurant.nl/img/gerechten/Cheeseburger550x440TBFEBO2020UE9.png',
  },
  {
    id: 2,
    name: 'Special Hot Dog',
    price: 0.13,
    image: 'https://cdn-test.sitedish.nl/javier.testrestaurant.nl/img/gerechten/HotDog550x440TBFEBO2020UE49.png',
  },
  {
    id: 3,
    name: 'Bucket Fries',
    price: 0.24,
    image: 'https://cdn-test.sitedish.nl/javier.testrestaurant.nl/img/gerechten/FritesBucket550x440TBFEBO2020UE20.png',
  },
  {
    id: 4,
    name: 'Coca-Cola Zero (33 cl)',
    price: 0.2,
    image:
      'https://cdn-test.sitedish.nl/javier.testrestaurant.nl/img/gerechten/60bdf652e74d4_550x440_TB_Dranken_FEBO_2020_UE_SD3.png',
  },
  {
    id: 5,
    name: 'Veal Croquette',
    price: 0.15,
    image:
      'https://cdn-test.sitedish.nl/javier.testrestaurant.nl/img/gerechten/Kalfsvleeskroket550x440TBFEBO2020UE31.png',
  },
];

const Shop = () => {
  const insets = useSafeAreaInsets();
  const { products: cartProducts, addToCart } = useCart();

  const onAddToCart = useCallback(
    (product: Product) => {
      addToCart(product);
    },
    [addToCart]
  );

  const onPay = useCallback(async () => {
    try {
      await payOrder(cartProducts);
    } catch (error) {
      if (__DEV__) {
        console.error(error);
      }
      Alert.alert('Error', (error as Error)?.message);
    }
  }, [cartProducts]);

  useEffect(() => {
    const suscription = RnPosAndroidIntegration.addTransactionListener(({ status }) => {
      Alert.alert('Transaction status', status);
    });
    return () => {
      suscription.remove();
    };
  }, []);

  return (
    <View style={{ flex: 1, backgroundColor: Colors.white }}>
      <Stack.Screen
        options={{
          title: 'Shop',
          headerStyle: { backgroundColor: Colors.primary },
          headerTintColor: Colors.white,
          headerTitleStyle: {
            fontWeight: 'bold',
          },
          headerRight: () => (
            <Link href="/settings" asChild>
              <Pressable style={{ paddingHorizontal: 10 }}>
                <Text
                  style={{
                    color: Colors.white,
                    fontWeight: 'bold',
                    fontSize: 15,
                    padding: 5,
                  }}
                >
                  Settings
                </Text>
              </Pressable>
            </Link>
          ),
        }}
      />
      <FlashList
        numColumns={2}
        data={products}
        renderItem={({ item: product }) => {
          return (
            <ProductCell
              product={product}
              onPress={() => {
                onAddToCart(product);
              }}
            />
          );
        }}
      />
      <View
        style={{
          backgroundColor: Colors.primary,
          paddingBottom: insets.bottom,
        }}
      >
        <Pressable
          onPress={onPay}
          disabled={cartProducts.length === 0}
          activeBackground={Colors.primaryLight}
          style={{
            padding: 15,
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Text style={{ color: Colors.white, fontWeight: 'bold' }}>{`PAY (${cartProducts.length})`}</Text>
        </Pressable>
      </View>
    </View>
  );
};
export default Shop;
