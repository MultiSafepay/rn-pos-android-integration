import { requireNativeViewManager } from "expo-modules-core";
import * as React from "react";

import { RnPosAndroidIntegrationViewProps } from "./RnPosAndroidIntegration.types";

const NativeView: React.ComponentType<RnPosAndroidIntegrationViewProps> =
  requireNativeViewManager("RnPosAndroidIntegration");

export default function RnPosAndroidIntegrationView(
  props: RnPosAndroidIntegrationViewProps
) {
  return <NativeView {...props} />;
}
