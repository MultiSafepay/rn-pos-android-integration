import { NativeModule, requireNativeModule } from 'expo';

import { ChangeEventPayload, PosMode } from './RnPosAndroidIntegration.types';

declare class RnPosAndroidIntegrationModule extends NativeModule {
  canInitiatePayment(): Promise<boolean>;
  initiatePayment(
    currency: string,
    amount: number,
    serializedItems: string,
    orderId: string,
    description: string,
    sessionId?: string
  ): void;
  setPosMode(mode: PosMode): void;
  setValueAsync(value: string): Promise<void>;
  addListener<EventName extends 'onTransactionChanged'>(
    eventName: EventName,
    listener: (event: ChangeEventPayload) => void
  ): { remove: () => void };
}

// This call loads the native module object from the JSI.
export default requireNativeModule<RnPosAndroidIntegrationModule>('RnPosAndroidIntegration');
