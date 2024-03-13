import { Image } from "expo-image";
import { FC } from "react";
import { View, Text } from "react-native";
import Pressable from "src/components/pressable";
import { Product } from "src/types";
import Colors from "src/utils/colors";
import { useCurrencyFormatter } from "src/utils/formatter";

interface Props {
  product: Product;
  onPress: () => void;
}

const ProductCell: FC<Props> = ({ product, onPress }) => {
  const currencyFormatter = useCurrencyFormatter();
  return (
    <View
      style={{
        padding: 15,
        margin: 15,
        borderRadius: 5,
        backgroundColor: Colors.white,
      }}
    >
      <Image
        source={{ uri: product.image }}
        style={{ width: 120, height: 120 }}
      />
      <View style={{ marginVertical: 5 }}>
        <Text style={{ fontSize: 16 }}>{product.name}</Text>
        <Text style={{ fontSize: 16, fontWeight: "bold" }}>
          {currencyFormatter.format(product.price)}
        </Text>
      </View>
      <Pressable
        style={{ backgroundColor: Colors.primary }}
        activeBackground={Colors.primaryLight}
        onPress={onPress}
      >
        <View
          style={{
            padding: 10,
            justifyContent: "center",
            alignItems: "center",
          }}
        >
          <Text style={{ color: Colors.white }}>Add to cart</Text>
        </View>
      </Pressable>
    </View>
  );
};
export default ProductCell;
