export interface Product {
  id: number;
  name: string;
  price: number;
  image?: string;
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

type PaymentStatus =
  | "initialized"
  | "completed"
  | "cancelled"
  | "void"
  | "expired"
  | "declined"
  | "uncleared";

export interface TransactionResponse {
  financial_status: PaymentStatus;
  order_id: string;
  session_id: string;
  status: PaymentStatus;
  transaction_id: number;
}
