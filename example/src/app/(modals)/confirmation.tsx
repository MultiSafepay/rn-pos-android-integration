import Ionicons from '@expo/vector-icons/Ionicons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import type { ComponentProps, FC } from 'react';
import { Text, View } from 'react-native';
import { useCallback, useMemo } from 'react';
import { SafeAreaView } from 'react-native-safe-area-context';

import Pressable from 'src/components/pressable';
import Colors from 'src/utils/colors';
import type { PaymentStatus } from 'src/types';
import { useCart } from 'src/providers/cart';

const SUCCESS_TINT = Colors.primary;
const ERROR_TINT = '#E53935';
const CANCEL_TINT = Colors.secondary;
const HEADING_COLOR = '#182134';
const BODY_COLOR = '#4A4E69';
const ERROR_ACTIVE_TINT = '#f26d6d';

type IoniconName = ComponentProps<typeof Ionicons>['name'];

type ConfirmationVariant = 'success' | 'error' | 'cancelled';

interface StatusDetail {
  title: string;
  description: string;
  icon: IoniconName;
  tint: string;
}

const STATUS_DETAILS: Record<ConfirmationVariant, StatusDetail> = {
  success: {
    title: 'Payment Complete',
    description: 'The order was processed successfully.',
    icon: 'checkmark-circle',
    tint: SUCCESS_TINT,
  },
  error: {
    title: 'Payment Failed',
    description: 'Something went wrong while finalizing the order.',
    icon: 'close-circle',
    tint: ERROR_TINT,
  },
  cancelled: {
    title: 'Payment Cancelled',
    description: 'The customer cancelled the payment.',
    icon: 'warning',
    tint: CANCEL_TINT,
  },
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

const resolvePaymentStatus = (value: string | undefined): PaymentStatus => {
  if (value && (PAYMENT_STATUSES as readonly string[]).includes(value)) {
    return value as PaymentStatus;
  }
  return 'completed';
};

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
  const { status, message } = useLocalSearchParams<{ status?: PaymentStatus; message?: string }>();
  const { clearCart } = useCart();

  const paymentStatusValue = resolvePaymentStatus(typeof status === 'string' ? status : undefined);

  const { detail, activeBackground, description } = useMemo(() => {
    const computedVariant = resolveVariant(paymentStatusValue);
    const modalDetail = STATUS_DETAILS[computedVariant];
    const background =
      computedVariant === 'success'
        ? Colors.primaryLight
        : computedVariant === 'cancelled'
          ? Colors.secondaryLight
          : ERROR_ACTIVE_TINT;
    let computedDescription: string;
    if (typeof message === 'string' && message.length > 0) {
      computedDescription = message;
    } else if (computedVariant === 'error') {
      computedDescription = `The order did fail with reason: ${paymentStatusValue}`;
    } else {
      computedDescription = modalDetail.description;
    }
    return {
      detail: modalDetail,
      activeBackground: background,
      description: computedDescription,
    };
  }, [message, paymentStatusValue]);

  const onClose = useCallback(() => {
    if (paymentStatusValue === 'completed') {
      clearCart();
      router.replace('/(tabs)');
    } else {
      if (router.canGoBack()) {
        router.back();
      }
    }
  }, [clearCart, paymentStatusValue, router]);

  return (
    <SafeAreaView
      style={{
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'transparent',
        padding: 24,
      }}
    >
      <Stack.Screen
        options={{
          presentation: 'transparentModal',
          title: 'Order status',
          contentStyle: { backgroundColor: 'transparent' },
        }}
      />
      <View
        style={{
          backgroundColor: Colors.white,
          borderRadius: 20,
          padding: 24,
          gap: 16,
          alignItems: 'center',
          width: '100%',
          maxWidth: 420,
          shadowColor: '#000',
          shadowOffset: { width: 0, height: 8 },
          shadowOpacity: 0.2,
          shadowRadius: 16,
          elevation: 10,
        }}
      >
        <Ionicons name={detail.icon} size={64} color={detail.tint} />
        <Text style={{ fontSize: 24, fontWeight: '700', color: HEADING_COLOR, textAlign: 'center' }}>
          {detail.title}
        </Text>
        <Text style={{ fontSize: 16, color: BODY_COLOR, textAlign: 'center' }}>{description}</Text>
        <Pressable
          onPress={onClose}
          style={{
            backgroundColor: detail.tint,
            borderRadius: 12,
            paddingVertical: 12,
            paddingHorizontal: 24,
            width: '100%',
          }}
          activeBackground={activeBackground}
        >
          <Text style={{ color: Colors.white, fontWeight: '700', textAlign: 'center' }}>Close</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
};

export default ConfirmationModal;
