export type ChangeEventPayload = {
  status: 'COMPLETED' | 'CANCELLED' | 'DECLINED' | 'EXCEPTION' | 'UNDEFINED';
};

export type RnPosAndroidIntegrationViewProps = {
  name: string;
};

export type PosMode = 'sunmi-pos' | 'soft-pos';
