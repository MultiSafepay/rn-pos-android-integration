import type { ImageSourcePropType } from 'react-native';

export interface Product {
  id: number;
  name: string;
  price: number;
  asset?: ImageSourcePropType;
  image?: string;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface PretransactionData {
  order_id: string;
  payment_url: string;
  session_id: string;
  events_token?: string;
  events_url?: string;
}

export interface PretransactionResponse {
  success?: boolean;
  data?: PretransactionData;
  error_code?: number;
  error_info?: string;
}

export type PaymentStatus = 'initialized' | 'completed' | 'cancelled' | 'void' | 'expired' | 'declined' | 'uncleared';

export interface TransactionResponse {
  success?: boolean;
  data?: {
    financial_status: PaymentStatus;
    order_id: string;
    status: PaymentStatus;
    transaction_id: number;
  };
  error_code?: number;
  error_info?: string;
}
