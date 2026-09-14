import '../global.css';

import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { LogBox, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import * as RnPosAndroidIntegration from 'rn-pos-android-integration';

import CartProvider from 'src/providers/cart';
import { palettes } from 'src/utils/colors';
import Storage from 'src/utils/storage';

interface ErrorUtilsLike {
  getGlobalHandler: () => ((error: Error, isFatal?: boolean) => void) | undefined;
  setGlobalHandler: (handler: (error: Error, isFatal?: boolean) => void) => void;
}

// Reanimated 4.3.x turns on FORCE_REACT_RENDER_FOR_SETTLED_ANIMATIONS by default
// on native: every animated component registers itself and a global 500ms timer
// calls setState on them to sync "settled" animation styles back into React. That
// async setState races FlashList's cell recycling, so it occasionally lands on a
// cell fiber that hasn't finished mounting — producing a benign, DEV-only "state
// update on a component that hasn't mounted yet" warning. It's an upstream race,
// not an app bug, and has no production impact. The flag is static (no public JS
// setter), so we silence the known noise here.
LogBox.ignoreLogs([/state update on a component that hasn't mounted yet/]);

// Diagnostic only. A release build has no RedBox, so an uncaught error -- including one
// thrown while rendering a screen we just navigated to -- shows up as nothing but a blank
// screen. Mirror it onto the native trace before handing it back to the default handler.
const errorUtils = (globalThis as unknown as { ErrorUtils?: ErrorUtilsLike }).ErrorUtils;
if (errorUtils) {
  const previousHandler = errorUtils.getGlobalHandler();
  errorUtils.setGlobalHandler((error, isFatal) => {
    const detail = `${isFatal ? 'FATAL ' : ''}${error?.name ?? 'Error'}: ${error?.message ?? String(error)}`;
    RnPosAndroidIntegration.ackDiagnostics(`JS THREW ${detail}`.slice(0, 180));
    previousHandler?.(error, isFatal);
  });
}

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
