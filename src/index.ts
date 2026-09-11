import type { EventSubscription } from 'expo-modules-core';
import { Platform } from 'react-native';

// Import the native module. On web, it will be resolved to RnPosAndroidIntegration.web.ts
// and on native platforms to RnPosAndroidIntegration.ts
import type { ChangeEventPayload, PosMode, RnPosAndroidIntegrationViewProps } from './RnPosAndroidIntegration.types';
import RnPosAndroidIntegrationModule from './RnPosAndroidIntegrationModule';
import RnPosAndroidIntegrationView from './RnPosAndroidIntegrationView';

export function canInitiatePayment(): Promise<boolean> {
  return Platform.OS === 'android' ? RnPosAndroidIntegrationModule.canInitiatePayment() : Promise.resolve(false);
}

const currency = 'EUR';

export interface OrderItem {
  name: string; // "Product 1"
  unit_price: string; // "0.10"
  quantity: string; // "1"
  merchant_item_id?: string; // "749857"
  tax?: string; // "3.90"
}

interface InitiateManualPaymentRequest {
  amount: number; // cents
  items: OrderItem[];
  orderId: string;
  description: string;
  /**
   * Mirrors the native callback trace onto the screen as toasts, on top of the usual
   * Logcat output. For diagnosing terminals that cannot take a debugger -- attaching one
   * stops Tap to Pay working -- so the trace has to be filmable. Leave off in production:
   * it puts transaction details on a customer-facing screen.
   */
  debug?: boolean;
}

interface InitiateRemotePaymentRequest extends InitiateManualPaymentRequest {
  sessionId: string;
}

interface InitiatePaymentRequest extends InitiateManualPaymentRequest {
  sessionId?: string;
}

const initiatePayment = ({ items, amount, orderId, description, sessionId, debug }: InitiatePaymentRequest) => {
  if (Platform.OS === 'android') {
    const validItems = items.map((item) => {
      return {
        ...item,
        ...{
          merchant_item_id: item.merchant_item_id ?? `merchant-id-${item.name}`,
          tax: item.tax ?? '0',
        },
      };
    });
    RnPosAndroidIntegrationModule.initiatePayment(
      currency,
      amount,
      JSON.stringify(validItems),
      orderId,
      description,
      sessionId,
      debug ?? false
    );
  }
};

export function initiateManualPayment({
  amount,
  items,
  orderId,
  description,
  debug,
}: InitiateManualPaymentRequest): void {
  initiatePayment({ amount, items, orderId, description, debug });
}

export function initiateRemotePayment({
  amount,
  orderId,
  items,
  description,
  sessionId,
  debug,
}: InitiateRemotePaymentRequest): void {
  initiatePayment({ amount, items, orderId, description, sessionId, debug });
}

/**
 * Tells the native trace that JS received something. Native cannot otherwise tell a status
 * that never reached JS from one that reached it and was lost on the way to the screen --
 * and those two have opposite fixes. No-op unless `debug` was passed to initiatePayment.
 */
export function ackDiagnostics(stage: string): void {
  if (Platform.OS === 'android') {
    RnPosAndroidIntegrationModule.ackDiagnostics(stage);
  }
}

export async function setValueAsync(value: string) {
  return await RnPosAndroidIntegrationModule.setValueAsync(value);
}

export function setPosMode(mode: PosMode): void {
  if (Platform.OS === 'android') {
    RnPosAndroidIntegrationModule.setPosMode(mode);
  }
}

// const emitter = new EventEmitter(RnPosAndroidIntegrationModule);

export function addTransactionListener(listener: (event: ChangeEventPayload) => void): EventSubscription {
  return RnPosAndroidIntegrationModule.addListener('onTransactionChanged', listener);
}

export { RnPosAndroidIntegrationView };
export type { ChangeEventPayload, PosMode, RnPosAndroidIntegrationViewProps };
