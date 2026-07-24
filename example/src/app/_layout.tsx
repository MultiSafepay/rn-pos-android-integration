import '../global.css';

import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { LogBox, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import CartProvider from 'src/providers/cart';
import { palettes } from 'src/utils/colors';
import Storage from 'src/utils/storage';

// Reanimated 4.3.x turns on FORCE_REACT_RENDER_FOR_SETTLED_ANIMATIONS by default
// on native: every animated component registers itself and a global 500ms timer
// calls setState on them to sync "settled" animation styles back into React. That
// async setState races FlashList's cell recycling, so it occasionally lands on a
// cell fiber that hasn't finished mounting — producing a benign, DEV-only "state
// update on a component that hasn't mounted yet" warning. It's an upstream race,
// not an app bug, and has no production impact. The flag is static (no public JS
// setter), so we silence the known noise here.
LogBox.ignoreLogs([/state update on a component that hasn't mounted yet/]);

export default function Layout() {
  const palette = palettes[useColorScheme() === 'dark' ? 'dark' : 'light'];

  useEffect(() => {
    // Initialize default API key / settings if not set.
    Storage.initializeDefaults();
  }, []);

  return (
    <SafeAreaProvider>
      <CartProvider>
        <Stack screenOptions={{ contentStyle: { backgroundColor: palette.background } }}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen
            name="(modals)/confirmation"
            options={{ animation: 'fade', presentation: 'transparentModal', headerShown: false }}
          />
        </Stack>
        <StatusBar style="auto" />
      </CartProvider>
    </SafeAreaProvider>
  );
}
