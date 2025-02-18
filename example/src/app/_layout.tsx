import { Stack } from 'expo-router';
import CartProvider from 'src/providers/cart';

export default function Layout() {
  return (
    <CartProvider>
      <Stack />
    </CartProvider>
  );
}
