import Ionicons from '@expo/vector-icons/Ionicons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import type { ComponentProps, FC } from 'react';
import { useCallback, useMemo } from 'react';
import { Text } from 'react-native';
import Animated, { FadeIn, ZoomIn } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';

import Pressable from 'src/components/pressable';
import { AnimatedView } from 'src/components/ui';
import { useCart } from 'src/providers/cart';
import type { PaymentStatus } from 'src/types';
import { useColors } from 'src/utils/colors';

type IoniconName = ComponentProps<typeof Ionicons>['name'];
type ConfirmationVariant = 'success' | 'error' | 'cancelled';

interface VariantDetail {
  title: string;
  description: string;
  icon: IoniconName;
}

const VARIANT_DETAILS: Record<ConfirmationVariant, VariantDetail> = {
  success: { title: 'Payment complete', description: 'The order was processed successfully.', icon: 'checkmark-circle' },
  error: { title: 'Payment failed', description: 'Something went wrong while finalizing the order.', icon: 'close-circle' },
  cancelled: { title: 'Payment cancelled', description: 'The customer cancelled the payment.', icon: 'alert-circle' },
};

const PAYMENT_STATUSES: readonly PaymentStatus[] = [
  'initialized',
  'completed',
  'cancelled',
  'void',
  'expired',
  'declined',
  'uncleared',
];

const resolvePaymentStatus = (value: string | undefined): PaymentStatus =>
  value && (PAYMENT_STATUSES as readonly string[]).includes(value) ? (value as PaymentStatus) : 'completed';

const resolveVariant = (status: PaymentStatus): ConfirmationVariant => {
  switch (status) {
    case 'completed':
      return 'success';
    case 'cancelled':
    case 'initialized':
      return 'cancelled';
    default:
      return 'error';
  }
};

const ConfirmationModal: FC = () => {
  const router = useRouter();
  const colors = useColors();
  const { status, message } = useLocalSearchParams<{ status?: PaymentStatus; message?: string }>();
  const { clearCart } = useCart();

  const paymentStatusValue = resolvePaymentStatus(typeof status === 'string' ? status : undefined);
  const variant = useMemo(() => resolveVariant(paymentStatusValue), [paymentStatusValue]);
  const detail = VARIANT_DETAILS[variant];

  const tint = variant === 'success' ? colors.success : variant === 'error' ? colors.danger : colors.muted;

  const description = useMemo(() => {
    if (typeof message === 'string' && message.length > 0) return message;
    if (variant === 'error') return `The order failed with reason: ${paymentStatusValue}`;
    return detail.description;
  }, [detail.description, message, paymentStatusValue, variant]);

  const onClose = useCallback(() => {
    if (paymentStatusValue === 'completed') {
      clearCart();
      router.replace('/(tabs)');
    } else if (router.canGoBack()) {
      router.back();
    }
  }, [clearCart, paymentStatusValue, router]);

  return (
    <Animated.View entering={FadeIn.duration(180)} style={{ flex: 1, backgroundColor: 'rgba(4,18,28,0.55)' }}>
      <SafeAreaView style={{ flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 }}>
        <AnimatedView
          entering={FadeIn.duration(220)}
          className="w-full max-w-sm items-center gap-4 rounded-3xl bg-surface p-7"
          style={{ borderCurve: 'continuous', boxShadow: '0 20px 50px rgba(0,0,0,0.35)' }}
        >
          <AnimatedView
            entering={ZoomIn.springify().damping(9).stiffness(140).delay(120)}
            className="h-20 w-20 items-center justify-center rounded-full"
            style={{ backgroundColor: `${tint}22` }}
          >
            <Ionicons name={detail.icon} size={52} color={tint} />
          </AnimatedView>

          <Text className="text-center text-2xl font-extrabold text-content">{detail.title}</Text>
          <Text className="text-center text-base text-muted" selectable>
            {description}
          </Text>

          <Pressable
            onPress={onClose}
            className="mt-2 h-14 w-full items-center justify-center rounded-2xl bg-primary"
            style={{ borderCurve: 'continuous' }}
          >
            <Text className="text-base font-bold text-on-primary">Done</Text>
          </Pressable>
        </AnimatedView>
      </SafeAreaView>
    </Animated.View>
  );
};

export default ConfirmationModal;
