import { FlashList } from '@shopify/flash-list';
import { Stack } from 'expo-router';
import { useCallback, useMemo } from 'react';
import { useWindowDimensions, View } from 'react-native';
import { useCart } from 'src/providers/cart';
import { Product } from 'src/types';
import Colors from 'src/utils/colors';

import ProductCell from 'src/components/product-cell';

const imagesFolder = `../../images`;
const products: Product[] = [
  {
    id: 1,
    name: 'Greek Salad',
    price: 9.5,
    asset: require(`${imagesFolder}/greek-salad.jpg`),
  },
  {
    id: 2,
    name: 'Grilled Sea Bass',
    price: 18.9,
    asset: require(`${imagesFolder}/grilled-sea-bass.jpg`),
  },
  {
    id: 3,
    name: 'Paella',
    price: 22.5,
    asset: require(`${imagesFolder}/paella.jpg`),
  },
  {
    id: 4,
    name: 'Fried Fish',
    price: 12,
    asset: require(`${imagesFolder}/fried-fish.jpg`),
  },
  {
    id: 5,
    name: 'Margherita Pizza',
    price: 11.5,
    asset: require(`${imagesFolder}/margherita-pizza.jpg`),
  },
  {
    id: 7,
    name: 'Lasagna Bolognese',
    price: 11.5,
    asset: require(`${imagesFolder}/lasagna-bolognese.jpg`),
  },
  {
    id: 8,
    name: 'Tiramisu',
    price: 6.95,
    asset: require(`${imagesFolder}/tiramisu.jpg`),
  },
  {
    id: 9,
    name: 'Sushi Platter',
    price: 49.95,
    asset: require(`${imagesFolder}/sushi-platter.jpg`),
  },
  {
    id: 10,
    name: 'Tonkotsu Ramen',
    price: 14.5,
    asset: require(`${imagesFolder}/tonkotsu-ramen.jpg`),
  },
  {
    id: 11,
    name: 'Classic Cheeseburger',
    price: 12,
    asset: require(`${imagesFolder}/classic-cheeseburger.jpg`),
  },
  {
    id: 12,
    name: 'BBQ Ribs',
    price: 18.9,
    asset: require(`${imagesFolder}/bbq-ribs.jpg`),
  },
  {
    id: 13,
    name: 'Cheesecake',
    price: 7.5,
    asset: require(`${imagesFolder}/cheesecake.jpg`),
  },
];

const Shop = () => {
  const { addToCart } = useCart();
  const { width } = useWindowDimensions();

  const numberOfColumns = useMemo(() => {
    if (width >= 1280) {
      return 4;
    }
    if (width >= 1024) {
      return 3;
    }
    if (width >= 768) {
      return 2;
    }
    return 1;
  }, [width]);

  const onAddToCart = useCallback(
    (product: Product) => {
      addToCart(product);
    },
    [addToCart]
  );

  return (
    <View style={{ flex: 1, backgroundColor: Colors.white }}>
      <Stack.Screen />
      <FlashList
        numColumns={numberOfColumns}
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
    </View>
  );
};
export default Shop;
