import type { EventSubscription } from 'expo-modules-core';
import { Alert, Platform } from 'react-native';

// Import the native module. On web, it will be resolved to RnPosAndroidIntegration.web.ts
// and on native platforms to RnPosAndroidIntegration.ts
import type { ChangeEventPayload, PosMode, RnPosAndroidIntegrationViewProps } from './RnPosAndroidIntegration.types';
import RnPosAndroidIntegrationModule from './RnPosAndroidIntegrationModule';
import RnPosAndroidIntegrationView from './RnPosAndroidIntegrationView';

export function canInitiatePayment(): Promise<boolean> {
  return Platform.OS === 'android' ? RnPosAndroidIntegrationModule.canInitiatePayment() : Promise.resolve(false);
}

const currency = 'EUR';

let debugMode = false;
let debugPackageName: string | undefined;

/**
 * Mirrors the native module's own default (`PosMode.SUNMI`) so the debug preview
 * can report the mode the Intent is actually built for. Kept in sync by
 * `setPosMode`; only drifts if something calls the native module directly.
 */
let currentPosMode: PosMode = 'sunmi-pos';

export interface DebugOptions {
  /**
   * The host app's `applicationId`, which the module sends as `package_name`
   * (and, in soft-pos mode, `callback_package`). Only the native side can read
   * it -- `context.packageName` -- so pass it in to have the preview show the
   * real value instead of a placeholder. Expo apps have it as
   * `Application.applicationId` from `expo-application`.
   */
  packageName?: string;
}

/**
 * When enabled, `initiateManualPayment` / `initiateRemotePayment` show the exact
 * Intent the module is about to send in an Alert, and only launch the payment app
 * once it is dismissed with OK. The same text is written to the console, which is
 * the copyable version -- an Alert cannot be selected, and a long basket may
 * out-scroll the dialog.
 *
 * Off by default; meant for debugging only — never enable it in production apps.
 */
export function setDebugMode(enabled: boolean, options?: DebugOptions): void {
  // Strict compare, not truthiness: this flag is the only thing standing between a
  // merchant and a dialog full of payment internals, so a stray non-boolean must
  // read as off rather than on.
  debugMode = enabled === true;
  // Dropped along with the flag, so turning debugging off leaves nothing behind.
  debugPackageName = debugMode ? options?.packageName : undefined;
}

// Mirrors of the constants in RnPosAndroidIntegrationModule.kt / SoftPosProxyActivity.kt.
// They exist only so the debug preview can name what the native side resolves; nothing
// here is sent to the Intent. Keep them in step with the Kotlin if that ever changes.
const SUNMI_PACKAGE = 'com.multisafepay.pos.sunmi';
const SUNMI_FALLBACK_PACKAGE = 'com.multisafepay.pos.nokernels';
const SUNMI_COMPONENT = 'com.multisafepay.pos.middleware.IntentActivity';
const SOFT_POS_PACKAGE = 'com.phonepos.mspsoftposapp';
const SOFT_POS_COMPONENT = 'com.phonepos.mspsoftposapp.ManualPayInputActivity';
const SOFT_POS_ACTION = 'com.phonepos.mspsoftposapp.ACTION_MANUAL_PAYMENT';
const SOFT_POS_CALLBACK_ACTIVITY = 'expo.modules.rnposandroidintegration.SoftPosProxyActivity';
const SOFT_POS_REQUEST_CODE = 6017;

const UNKNOWN_PACKAGE_NAME = '<host applicationId, resolved natively>';

/** One Intent extra, with the Android type the native side puts it in as. */
interface IntentExtra {
  key: string;
  /** The Java type of the value in the Bundle, not the TypeScript type. */
  type: 'String' | 'long' | 'boolean';
  value: string;
  /** Rendered raw on its own line instead of inline, for long values. */
  block?: boolean;
}

/**
 * Format cents the way the native side does for soft-pos:
 * `String.format(Locale.US, "%.2f", amount / 100.0)`.
 *
 * Done with integer arithmetic rather than `(amount / 100).toFixed(2)` so the
 * preview cannot disagree with the Intent over a binary rounding edge case.
 */
const formatSoftPosAmount = (cents: number): string => {
  const sign = cents < 0 ? '-' : '';
  const absolute = Math.abs(Math.round(cents));

  return `${sign}${Math.floor(absolute / 100)}.${String(absolute % 100).padStart(2, '0')}`;
};

/**
 * Rebuild, in order, the extras that `initiatePayment` puts on the Intent.
 *
 * This is a transcription of the Kotlin, not a reading of it: JS never sees the
 * real Intent. It is accurate as long as both sides are changed together, which
 * is why every constant above is pinned rather than guessed at call time.
 */
const describeIntent = ({
  serializedItems,
  amount,
  orderId,
  description,
  sessionId,
}: {
  serializedItems: string;
  amount: number;
  orderId: string;
  description: string;
  sessionId?: string;
}): string => {
  const isSoftPos = currentPosMode === 'soft-pos';
  const packageName = debugPackageName ?? UNKNOWN_PACKAGE_NAME;

  const extras: IntentExtra[] = [
    { key: 'package_name', type: 'String', value: packageName },
    { key: 'order_id', type: 'String', value: orderId },
    { key: 'order_description', type: 'String', value: description },
    { key: 'items', type: 'String', value: serializedItems, block: true },
    { key: 'currency', type: 'String', value: currency },
  ];

  // Native adds session_id only when it is non-null, so a remote payment has one
  // extra more than a manual one. Absent is not the same as empty here.
  if (sessionId !== undefined) {
    extras.push({ key: 'session_id', type: 'String', value: sessionId });
  }

  if (isSoftPos) {
    extras.push(
      { key: 'amount', type: 'String', value: formatSoftPosAmount(amount) },
      { key: 'skip_manual_input', type: 'boolean', value: 'true' },
      { key: 'callback_package', type: 'String', value: packageName },
      { key: 'callback_activity', type: 'String', value: SOFT_POS_CALLBACK_ACTIVITY }
    );
  } else {
    extras.push({ key: 'amount', type: 'long', value: String(amount) });
  }

  const width = Math.max(...extras.map((extra) => extra.key.length));

  const renderedExtras = extras.flatMap((extra) => {
    const label = `${extra.key.padEnd(width)}  ${`(${extra.type})`.padEnd(9)}`;

    // The basket is the thing under debate, so it is printed raw -- escaping it
    // for display would be exact but unreadable. Its length is given so a value
    // clipped by the dialog is obvious.
    return extra.block
      ? [`${label}  ${extra.value.length} chars:`, extra.value]
      : [`${label}  ${extra.value}`];
  });

  const target = isSoftPos
    ? [
        `action     ${SOFT_POS_ACTION}`,
        `component  ${SOFT_POS_PACKAGE}/${SOFT_POS_COMPONENT}`,
        `launch     activity.startActivityForResult(intent, ${SOFT_POS_REQUEST_CODE})`,
      ]
    : [
        `action     android.intent.action.MAIN`,
        `category   android.intent.category.LAUNCHER`,
        `component  ${SUNMI_PACKAGE}/${SUNMI_COMPONENT}`,
        `           (falls back to ${SUNMI_FALLBACK_PACKAGE} if that package is absent)`,
        `flags      FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`,
        `           (from getLaunchIntentForPackage)`,
        `launch     context.startActivity(intent)`,
      ];

  return [
    `POS MODE: ${currentPosMode}`,
    '',
    'INTENT',
    ...target,
    '',
    `EXTRAS (${extras.length})`,
    ...renderedExtras,
    '',
    'JS → NATIVE CALL',
    `initiatePayment(${[
      JSON.stringify(currency),
      String(amount),
      'serializedItems',
      JSON.stringify(orderId),
      JSON.stringify(description),
      sessionId === undefined ? 'null' : JSON.stringify(sessionId),
    ].join(', ')})`,
  ].join('\n');
};

/**
 * One basket line, as the MSP Pay App middleware and SoftPOS expect it.
 *
 * The wire format is the one the official Android integration sends
 * (`PaymentActivity.buildCartItems`): `name`, `unit_price`, `quantity` and
 * `tax_rate`, with the three latter as JSON *numbers*. Strings are accepted
 * here and coerced on the way out, so callers that already pass
 * `price.toString()` keep working.
 */
export interface OrderItem {
  name: string; // "Product 1"
  /** Price of a single unit in major units (euros), NOT cents. 0.10, not 10. */
  unit_price: number | string;
  quantity: number | string; // 1
  /** Tax as a decimal fraction of the price: 0.21 for 21%. Defaults to 0. */
  tax_rate?: number | string;
  /**
   * @deprecated Not part of the intent contract -- the reference integration
   * never sends it, and an unknown key risks the whole basket being rejected by
   * a strict deserializer. Accepted for source compatibility, but not sent.
   */
  merchant_item_id?: string;
  /** @deprecated Misspelling of `tax_rate`; nothing reads it. Not sent. */
  tax?: number | string;
}

/** Coerce a possibly-stringified number, falling back when it is unusable. */
const toNumber = (value: number | string | undefined, fallback: number): number => {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }

  const parsed = typeof value === 'number' ? value : Number.parseFloat(value);

  return Number.isFinite(parsed) ? parsed : fallback;
};

interface InitiateManualPaymentRequest {
  amount: number; // cents
  items: OrderItem[];
  orderId: string;
  description: string;
}

interface InitiateRemotePaymentRequest extends InitiateManualPaymentRequest {
  sessionId: string;
}

interface InitiatePaymentRequest extends InitiateManualPaymentRequest {
  sessionId?: string;
}

const initiatePayment = ({ items, amount, orderId, description, sessionId }: InitiatePaymentRequest) => {
  if (Platform.OS === 'android') {
    // Build each line explicitly rather than spreading `item`: the receiving app
    // parses this array strictly, so an unknown key can cost the entire basket.
    const validItems = items.map((item) => ({
      name: item.name,
      unit_price: toNumber(item.unit_price, 0),
      quantity: Math.round(toNumber(item.quantity, 1)),
      tax_rate: toNumber(item.tax_rate ?? item.tax, 0),
    }));

    // The exact String the `items` extra carries -- compact, as JSON.stringify
    // produces it. Never pretty-print this for the preview: an indented copy is
    // not the value that crosses the Intent boundary.
    const serializedItems = JSON.stringify(validItems);

    const launchPayment = () =>
      RnPosAndroidIntegrationModule.initiatePayment(
        currency,
        amount,
        serializedItems,
        orderId,
        description,
        sessionId
      );

    // Everything below this line is debug-only. With the flag off the module shows
    // nothing and logs nothing: no Alert, no console output, and the preview is not
    // even built. This is the single gate -- keep it that way.
    if (!debugMode) {
      launchPayment();
      return;
    }

    const preview = describeIntent({ serializedItems, amount, orderId, description, sessionId });
    const title = sessionId ? 'Initiating remote payment' : 'Initiating manual payment';

    // Also logged: the Alert cannot be copied, and a long basket may out-scroll it.
    console.log(`[rn-pos-android-integration] ${title}\n${preview}`);

    // No Cancel button and not cancelable: dismissing without launching would leave
    // the host app waiting for a transaction result that never arrives.
    Alert.alert(title, preview, [{ text: 'OK', onPress: launchPayment }], { cancelable: false });
  }
};

export function initiateManualPayment({ amount, items, orderId, description }: InitiateManualPaymentRequest): void {
  initiatePayment({ amount, items, orderId, description });
}

export function initiateRemotePayment({
  amount,
  orderId,
  items,
  description,
  sessionId,
}: InitiateRemotePaymentRequest): void {
  initiatePayment({ amount, items, orderId, description, sessionId });
}

export async function setValueAsync(value: string) {
  return await RnPosAndroidIntegrationModule.setValueAsync(value);
}

export function setPosMode(mode: PosMode): void {
  // Tracked on this side too, so the debug preview knows which Intent shape the
  // native module will build. Set unconditionally: the native call is Android-only.
  currentPosMode = mode;

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
