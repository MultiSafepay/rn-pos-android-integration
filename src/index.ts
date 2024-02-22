import { EventEmitter, Subscription } from "expo-modules-core";
import { Platform } from "react-native";

// Import the native module. On web, it will be resolved to RnPosAndroidIntegration.web.ts
// and on native platforms to RnPosAndroidIntegration.ts
import {
  ChangeEventPayload,
  RnPosAndroidIntegrationViewProps,
} from "./RnPosAndroidIntegration.types";
import RnPosAndroidIntegrationModule from "./RnPosAndroidIntegrationModule";
import RnPosAndroidIntegrationView from "./RnPosAndroidIntegrationView";

export function canInitiatePayment(): Promise<boolean> {
  return Platform.OS === "android"
    ? RnPosAndroidIntegrationModule.canInitiatePayment()
    : Promise.resolve(false);
}

const currency = "EUR";

export interface OrderItem {
  name: string; // "Product 1"
  unit_price: string; // "0.10"
  quantity: string; // "1"
  merchant_item_id?: string; // "749857"
  tax?: string; // "3.90"
}

interface InitiateManualPaymentRequest {
  // amount: number; // cents
  items: OrderItem[];
  orderId: string;
  description: string;
}

interface InitiateRemotePaymentRequest extends InitiateManualPaymentRequest {
  sessionId: string;
}

interface InitiatePaymentRequest extends InitiateManualPaymentRequest {
  sessionId?: string;
}

const initiatePayment = ({
  items,
  orderId,
  description,
  sessionId,
}: InitiatePaymentRequest) => {
  if (Platform.OS === "android") {
    const validItems = items.map((item) => {
      return {
        ...item,
        ...{
          merchant_item_id: item.merchant_item_id ?? `merchant-id-${item.name}`,
          tax: item.tax ?? "0",
        },
      };
    });
    RnPosAndroidIntegrationModule.initiatePayment(
      currency,
      JSON.stringify(validItems),
      orderId,
      description,
      sessionId
    );
  }
};

export function initiateManualPayment({
  // amount,
  items,
  orderId,
  description,
}: InitiateManualPaymentRequest): void {
  initiatePayment({ items, orderId, description });
}

export function initiateRemotePayment({
  // amount,
  items,
  orderId,
  description,
  sessionId,
}: InitiateRemotePaymentRequest): void {
  initiatePayment({ items, orderId, description, sessionId });
}

export async function setValueAsync(value: string) {
  return await RnPosAndroidIntegrationModule.setValueAsync(value);
}

const emitter = new EventEmitter(RnPosAndroidIntegrationModule);

export function addTransactionListener(
  listener: (event: ChangeEventPayload) => void
): Subscription {
  return emitter.addListener<ChangeEventPayload>(
    "onTransactionChanged",
    listener
  );
}

export {
  RnPosAndroidIntegrationView,
  RnPosAndroidIntegrationViewProps,
  ChangeEventPayload,
};
