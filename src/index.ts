import {
  NativeModulesProxy,
  EventEmitter,
  Subscription,
} from "expo-modules-core";
import { Platform } from "react-native";

// Import the native module. On web, it will be resolved to RnPosAndroidIntegration.web.ts
// and on native platforms to RnPosAndroidIntegration.ts
import {
  ChangeEventPayload,
  RnPosAndroidIntegrationViewProps,
} from "./RnPosAndroidIntegration.types";
import RnPosAndroidIntegrationModule from "./RnPosAndroidIntegrationModule";
import RnPosAndroidIntegrationView from "./RnPosAndroidIntegrationView";

// Get the native constant value.
export const PI = RnPosAndroidIntegrationModule.PI;

export function hello(): string {
  return RnPosAndroidIntegrationModule.hello();
}

interface InitiatePaymentRequest {
  orderId: string;
  description: string;
  serializedItems: string;
}
export function initiatePayment({
  orderId,
  description,
  serializedItems,
}: InitiatePaymentRequest): void {
  if (Platform.OS === "android") {
    RnPosAndroidIntegrationModule.initiatePayment(
      orderId,
      description,
      serializedItems
    );
  }
}

export async function setValueAsync(value: string) {
  return await RnPosAndroidIntegrationModule.setValueAsync(value);
}

const emitter = new EventEmitter(
  RnPosAndroidIntegrationModule ?? NativeModulesProxy.RnPosAndroidIntegration
);

export function addChangeListener(
  listener: (event: ChangeEventPayload) => void
): Subscription {
  return emitter.addListener<ChangeEventPayload>("onChange", listener);
}

export {
  RnPosAndroidIntegrationView,
  RnPosAndroidIntegrationViewProps,
  ChangeEventPayload,
};
