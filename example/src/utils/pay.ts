import * as Application from 'expo-application';
import * as RnPosAndroidIntegration from 'rn-pos-android-integration';
import { CartItem, PretransactionData, PretransactionResponse, TransactionResponse } from 'src/types';
import { v4 as uuidv4 } from 'uuid';
import 'react-native-get-random-values';

import Storage, { AppEnvironment } from './storage';

const getApiDomain = (environment: AppEnvironment) => {
  switch (environment) {
    case 'dev':
      return 'https://api.dev.multisafepay.com';
    case 'test':
      return 'https://testapi.multisafepay.com';
    case 'live':
    default:
      return 'https://api.multisafepay.com';
  }
};

/**
 * A `shopping_cart.items` entry, as the MultiSafepay orders API expects it.
 *
 * `tax_table_selector` and `weight` are part of the API's schema too, but the
 * demo catalog carries neither, so they are left out rather than invented --
 * both are optional server-side.
 */
interface ShoppingCartItem {
  name: string;
  description: string;
  /** Major units (euros), matching `Product.price`. Not cents. */
  unit_price: number;
  quantity: number;
  merchant_item_id: string;
}

const toShoppingCart = (items: CartItem[]): { items: ShoppingCartItem[] } => ({
  items: items.map(({ product, quantity }) => ({
    name: product.name,
    description: product.description ?? '',
    unit_price: product.price,
    quantity,
    merchant_item_id: product.id.toString(),
  })),
});

interface StartOrderRequest {
  apiKey: string;
  amount: number;
  orderId: string;
  description: string;
  items?: CartItem[];
}
export const startOrder = async ({ apiKey, amount, orderId, description, items }: StartOrderRequest) => {
  // Call the payment API

  const environment = await Storage.getEnvironment();
  if (__DEV__) {
    console.log('Using environment:', environment);
  }

  const domain = getApiDomain(environment);
  const url = `${domain}/v1/json/orders?api_key=${apiKey}`;

  const body = JSON.stringify({
    type: 'redirect',
    order_id: orderId,
    gateway: '',
    currency: 'EUR',
    amount,
    description,
    //   gateway_info: { terminal_id: "TERMINAL_ID" },
    // Only sent when there is something to send: the API rejects a
    // `shopping_cart` whose `items` array is empty.
    ...(items && items.length > 0 ? { shopping_cart: toShoppingCart(items) } : {}),
  });

  if (__DEV__) {
    console.log('Create pretransaction request: ', {
      url,
      method: 'POST',
      body,
    });
  }

  const response = await fetch(url, {
    method: 'POST',
    body,
  });
  const data = (await response.json()) as PretransactionResponse;
  if (__DEV__) {
    console.log('Create pretransaction response: ', data);
  }

  if (response.status === 200 && data.success === true) {
    return data.data as PretransactionData;
  } else {
    throw new Error(data.error_info ?? 'An error occurred while creating the order');
  }
};

interface PayOrderRequest {
  cartItems: CartItem[];
}

export const payOrder = async ({ cartItems }: PayOrderRequest) => {
  const orderId = uuidv4();
  const description = 'React Native POS ' + orderId;

  const apiKey = await Storage.getApiKey();
  if (!apiKey) {
    throw new Error('No API key found');
  }

  const posMode = (await Storage.getPosMode()) ?? 'sunmi-pos';
  RnPosAndroidIntegration.setPosMode(posMode);
  // Demo app only, and only when the Settings toggle is on: show the exact Intent in
  // an Alert before launching the payment app. Set on every payment, so switching the
  // toggle off takes effect immediately. `applicationId` is passed because only the
  // native side can read it, and it is what the module sends as `package_name` /
  // `callback_package`.
  const debugPayload = await Storage.getDebugPayload();
  RnPosAndroidIntegration.setDebugMode(debugPayload, {
    packageName: Application.applicationId ?? undefined,
  });

  // Rounded per line: `price * 100` is not exact in binary floating point
  // (0.1 * 100 === 10.000000000000002), and `amount` has to be whole cents --
  // the API checks it against the shopping cart total now that we send one.
  const amount = cartItems.reduce((acc, item) => acc + Math.round(item.product.price * 100) * item.quantity, 0);
  const data = await startOrder({ apiKey, amount, orderId, description, items: cartItems });

  const canInitiatePayment = await RnPosAndroidIntegration.canInitiatePayment();
  if (!canInitiatePayment) {
    throw new Error("Can't initiate payment: device not supported");
  }

  const items: RnPosAndroidIntegration.OrderItem[] = cartItems.map((cartItem) => ({
    name: cartItem.product.name,
    unit_price: cartItem.product.price.toString(),
    quantity: cartItem.quantity.toString(),
  }));

  RnPosAndroidIntegration.initiateRemotePayment({
    amount,
    items,
    orderId,
    description,
    sessionId: data.session_id,
  });

  return Promise.resolve();
};

export const getTransactionDetails = async (transactionId: string) => {
  const apiKey = await Storage.getApiKey();
  if (!apiKey) {
    throw new Error('No API key found');
  }

  const environment = await Storage.getEnvironment();
  const domain = getApiDomain(environment);
  const url = `${domain}/v1/json/orders/${transactionId}?api_key=${apiKey}`;

  const response = await fetch(url, {
    method: 'GET',
  });
  const data = (await response.json()) as TransactionResponse;
  if (__DEV__) {
    console.log('Get transaction response: ', data);
  }

  if (response.status === 200 && data.success === true) {
    return data.data;
  } else {
    throw new Error(data.error_info ?? 'An error occurred while fetching the transaction details');
  }
};
