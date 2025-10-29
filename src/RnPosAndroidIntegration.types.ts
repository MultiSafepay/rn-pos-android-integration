export type ChangeEventPayload = {
  status: 'COMPLETED' | 'CANCELLED' | 'DECLINED' | 'EXCEPTION';
};

export type RnPosAndroidIntegrationViewProps = {
  name: string;
};

export type PosMode = 'sunmi-pos' | 'soft-pos';
