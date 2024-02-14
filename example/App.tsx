import "react-native-get-random-values";
import { useCallback } from "react";
import { Button, StyleSheet, Text, View } from "react-native";
import * as RnPosAndroidIntegration from "rn-pos-android-integration";
import { v4 as uuidv4 } from "uuid";

interface CartItem {
  name: string;
  unit_price: string;
  quantity: string;
  merchant_item_id?: string;
  tax?: string;
}

export default function App() {
  const onInitiatePayment = useCallback(() => {
    const orderId = uuidv4();
    const description = "React Native POS " + orderId;
    const items: CartItem[] = [];
    const serializedItems = JSON.stringify(items);
    RnPosAndroidIntegration.initiatePayment({
      orderId,
      description,
      serializedItems,
    });
  }, []);

  return (
    <View style={styles.container}>
      <Text style={{ padding: 5, fontWeight: "bold" }}>
        Pay using MultiSafepay Pay App
      </Text>
      <Button title="Pay" onPress={onInitiatePayment} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
    alignItems: "center",
    justifyContent: "center",
  },
});
