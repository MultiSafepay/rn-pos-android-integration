import { NativeModulesProxy, EventEmitter, Subscription } from 'expo-modules-core';

// Import the native module. On web, it will be resolved to RnPosAndroidIntegration.web.ts
// and on native platforms to RnPosAndroidIntegration.ts
import RnPosAndroidIntegrationModule from './RnPosAndroidIntegrationModule';
import RnPosAndroidIntegrationView from './RnPosAndroidIntegrationView';
import { ChangeEventPayload, RnPosAndroidIntegrationViewProps } from './RnPosAndroidIntegration.types';

// Get the native constant value.
export const PI = RnPosAndroidIntegrationModule.PI;

export function hello(): string {
  return RnPosAndroidIntegrationModule.hello();
}

export async function setValueAsync(value: string) {
  return await RnPosAndroidIntegrationModule.setValueAsync(value);
}

const emitter = new EventEmitter(RnPosAndroidIntegrationModule ?? NativeModulesProxy.RnPosAndroidIntegration);

export function addChangeListener(listener: (event: ChangeEventPayload) => void): Subscription {
  return emitter.addListener<ChangeEventPayload>('onChange', listener);
}

export { RnPosAndroidIntegrationView, RnPosAndroidIntegrationViewProps, ChangeEventPayload };
