import * as React from "react";

import { RnPosAndroidIntegrationViewProps } from "./RnPosAndroidIntegration.types";

export default function RnPosAndroidIntegrationView(
  props: RnPosAndroidIntegrationViewProps
) {
  return (
    <div>
      <span>{props.name}</span>
    </div>
  );
}
