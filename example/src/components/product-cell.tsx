import { Image } from 'expo-image';
import { FC, useMemo } from 'react';
import { View, Text } from 'react-native';
import IonIcons from '@expo/vector-icons/Ionicons';

import Pressable from 'src/components/pressable';
import { Product } from 'src/types';
import Colors from 'src/utils/colors';
import { useCurrencyFormatter } from 'src/utils/formatter';

interface Props {
  product: Product;
  onPress: () => void;
}

const ProductCell: FC<Props> = ({ product, onPress }) => {
  const currencyFormatter = useCurrencyFormatter();
  const price = useMemo(() => currencyFormatter.format(product.price), [currencyFormatter, product.price]);

  return (
    <View
      style={{
        flex: 1,
        justifyContent: 'flex-end',
        margin: 15,
        height: 260,
        overflow: 'hidden',
      }}
    >
      <Image
        source={product.asset}
        style={{
          width: '100%',
          height: '100%',
          position: 'absolute',
        }}
      />
      <View
        style={{
          padding: 15,
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'flex-end',
          backgroundColor: 'rgba(0,0,0,0.7)',
        }}
      >
        <View
          style={{
            flex: 1,
            marginRight: 12,
          }}
        >
          <Text
            style={{
              fontSize: 24,
              fontWeight: '600',
              color: '#FFFFFF',
              marginBottom: 4,
            }}
          >
            {product.name}
          </Text>
          <Text
            style={{
              fontSize: 24,
              fontWeight: '900',
              color: Colors.white,
            }}
          >
            {price}
          </Text>
        </View>
        <Pressable onPress={onPress} style={{ backgroundColor: Colors.primary, borderRadius: 32, padding: 10 }}>
          <IonIcons size={32} name="add" color={Colors.white} />
        </Pressable>
      </View>
    </View>
  );
};
export default ProductCell;
