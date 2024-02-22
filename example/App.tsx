import "react-native-get-random-values";
import { useCallback, useEffect, useState } from "react";
import { Button, StyleSheet, Text, View } from "react-native";
import * as RnPosAndroidIntegration from "rn-pos-android-integration";
import { v4 as uuidv4 } from "uuid";

export default function App() {
  const [canInitiate, setCanInitiate] = useState(false);
  const onInitiatePayment = useCallback(() => {
    // # Manual payment #
    const orderId = uuidv4();
    const description = "React Native POS " + orderId;
    const amount = 30; // cents
    const items: RnPosAndroidIntegration.OrderItem[] = [
      {
        name: "Item 1",
        unit_price: "0.10",
        quantity: "2",
      },
      {
        name: "Item 2",
        unit_price: "0.25",
        quantity: "1",
      },
      {
        name: "Item 3",
        unit_price: "0.03",
        quantity: "3",
      },
    ];
    // RnPosAndroidIntegration.initiateManualPayment({
    //   amount,
    //   orderId,
    //   description,
    // });

    // # Remote payment #
    const sessionId = "session-id"; // This value should be generated by the server
    RnPosAndroidIntegration.initiateRemotePayment({
      items,
      orderId,
      description,
      sessionId,
    });
  }, []);

  useEffect(() => {
    RnPosAndroidIntegration.canInitiatePayment()
      .then(setCanInitiate)
      .catch((e) => {
        if (__DEV__) {
          console.error(e);
        }
      });
  }, []);

  return (
    <View style={styles.container}>
      <Text style={{ padding: 5, fontWeight: "bold" }}>
        Pay using MultiSafepay Pay App
      </Text>
      <Button disabled={!canInitiate} title="Pay" onPress={onInitiatePayment} />
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
