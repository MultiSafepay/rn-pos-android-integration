import * as RnPosAndroidIntegration from "rn-pos-android-integration";
import { PretransactionData, PretransactionResponse, Product } from "src/types";
import { v4 as uuidv4 } from "uuid";
import "react-native-get-random-values";

import Storage from "./storage";

interface StartOrderRequest {
  apiKey: string;
  products: Product[];
  orderId: string;
  description: string;
}
export const startOrder = async ({
  apiKey,
  products,
  orderId,
  description,
}: StartOrderRequest) => {
  const amount = products.reduce(
    (acc, product) => acc + product.price * 100,
    0
  );

  // Call the payment API
  const url = `https://api.multisafepay.com/v1/json/orders?api_key=${apiKey}`;
  const body = JSON.stringify({
    type: "redirect",
    order_id: orderId,
    gateway: "",
    currency: "EUR",
    amount,
    description,
    //   gateway_info: { terminal_id: "TERMINAL_ID" },
  });

  if (__DEV__) {
    console.log("Create pretransaction request: ", {
      url,
      method: "POST",
      body,
    });
  }

  const response = await fetch(url, {
    method: "POST",
    body,
  });
  const data = (await response.json()) as PretransactionResponse;
  if (__DEV__) {
    console.log("Create pretransaction response: ", data);
  }

  if (response.status === 200 && data.success === true) {
    return data.data as PretransactionData;
  } else {
    throw new Error(
      data.error_info ?? "An error occurred while creating the order"
    );
  }
};

export const payOrder = async (products: Product[]) => {
  const orderId = uuidv4();
  const description = "React Native POS " + orderId;

  const apiKey = await Storage.getApiKey();
  if (!apiKey) {
    throw new Error("No API key found");
  }

  const data = await startOrder({ apiKey, products, orderId, description });

  const canInitiatePayment = await RnPosAndroidIntegration.canInitiatePayment();
  if (!canInitiatePayment) {
    throw new Error("Can't initiate payment: device not supported");
  }

  const items: RnPosAndroidIntegration.OrderItem[] = products.map(
    (product) => ({
      name: product.name,
      unit_price: product.price.toString(),
      quantity: "1",
    })
  );

  RnPosAndroidIntegration.initiateRemotePayment({
    items,
    orderId,
    description,
    sessionId: data.session_id,
  });

  return Promise.resolve();
};
