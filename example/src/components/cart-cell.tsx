import { Image } from 'expo-image';
import IonIcons from '@expo/vector-icons/Ionicons';
import { FC } from 'react';
import { View, Text } from 'react-native';

import Pressable from 'src/components/pressable';
import { Product } from 'src/types';
import Colors from 'src/utils/colors';
import { useCurrencyFormatter } from 'src/utils/formatter';

interface Props {
  product: Product;
  quantity: number;
  onIncrease: () => void;
  onDecrease: () => void;
}

const CartCell: FC<Props> = ({ product, quantity, onIncrease, onDecrease }) => {
  const currencyFormatter = useCurrencyFormatter();
  return (
    <View
      style={{
        padding: 15,
        margin: 15,
        borderRadius: 5,
      }}
    >
      <View style={{ flexDirection: 'row' }}>
        <Image source={product.asset} style={{ width: 80, height: 80 }} />
        <View style={{ marginHorizontal: 5, flex: 1 }}>
          <>
            <Text style={{ fontSize: 16 }}>{product.name}</Text>
            <Text style={{ fontSize: 16, fontWeight: 'bold' }}>{currencyFormatter.format(product.price)}</Text>
          </>
          <View
            style={{
              flexDirection: 'row',
              paddingVertical: 5,
            }}
          >
            <Pressable onPress={onDecrease}>
              <IonIcons size={24} name="remove-circle-outline" color={Colors.primary} />
            </Pressable>
            <Text style={{ alignSelf: 'center', fontSize: 16, fontWeight: 'bold', paddingHorizontal: 10 }}>
              {quantity}
            </Text>
            <Pressable onPress={onIncrease}>
              <IonIcons size={24} name="add-circle-outline" color={Colors.primary} />
            </Pressable>
          </View>
        </View>
      </View>
    </View>
  );
};
export default CartCell;
