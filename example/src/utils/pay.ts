import * as RnPosAndroidIntegration from 'rn-pos-android-integration';
import { PretransactionData, PretransactionResponse, Product } from 'src/types';
import { v4 as uuidv4 } from 'uuid';
import 'react-native-get-random-values';

import Storage from './storage';

interface StartOrderRequest {
  apiKey: string;
  amount: number;
  orderId: string;
  description: string;
}
export const startOrder = async ({ apiKey, amount, orderId, description }: StartOrderRequest) => {
  // Call the payment API

  const environment = await Storage.getEnvironment();
  if (__DEV__) {
    console.log('Using environment:', environment);
  }

  const domain = ((): string => {
    switch (environment) {
      case 'dev':
        return 'https://api.dev.multisafepay.com';
      case 'test':
        return 'https://testapi.multisafepay.com';
      case 'live':
      default:
        return 'https://api.multisafepay.com';
    }
  })();

  const url = `${domain}/v1/json/orders?api_key=${apiKey}`;
  const body = JSON.stringify({
    type: 'redirect',
    order_id: orderId,
    gateway: '',
    currency: 'EUR',
    amount,
    description,
    //   gateway_info: { terminal_id: "TERMINAL_ID" },
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

export const payOrder = async (products: Product[]) => {
  const orderId = uuidv4();
  const description = 'React Native POS ' + orderId;

  const apiKey = await Storage.getApiKey();
  if (!apiKey) {
    throw new Error('No API key found');
  }

  const posMode = (await Storage.getPosMode()) ?? 'sunmi-pos';
  RnPosAndroidIntegration.setPosMode(posMode);

  const amount = products.reduce((acc, product) => acc + product.price * 100, 0);
  const data = await startOrder({ apiKey, amount, orderId, description });

  const canInitiatePayment = await RnPosAndroidIntegration.canInitiatePayment();
  if (!canInitiatePayment) {
    throw new Error("Can't initiate payment: device not supported");
  }

  const items: RnPosAndroidIntegration.OrderItem[] = products.map((product) => ({
    name: product.name,
    unit_price: product.price.toString(),
    quantity: '1',
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
