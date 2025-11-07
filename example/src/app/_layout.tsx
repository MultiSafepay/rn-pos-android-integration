import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';

import CartProvider from 'src/providers/cart';
import Storage from 'src/utils/storage';

export default function Layout() {
  useEffect(() => {
    // Initialize default API key if not set
    Storage.initializeDefaults();
  }, []);

  return (
    <CartProvider>
      <Stack>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen
          name="(modals)/confirmation"
          options={{ animation: 'fade_from_bottom', presentation: 'transparentModal', headerShown: false }}
        />
      </Stack>
      <StatusBar style="auto" />
    </CartProvider>
  );
}
