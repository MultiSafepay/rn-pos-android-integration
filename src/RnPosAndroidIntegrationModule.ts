import { NativeModule, requireNativeModule } from 'expo';

import { ChangeEventPayload } from './RnPosAndroidIntegration.types';

declare class RnPosAndroidIntegrationModule extends NativeModule {
  addListener<EventName extends 'onTransactionChanged'>(
    eventName: EventName,
    listener: (event: ChangeEventPayload) => void
  ): { remove: () => void };
}

// This call loads the native module object from the JSI.
export default requireNativeModule<RnPosAndroidIntegrationModule>('RnPosAndroidIntegration');
