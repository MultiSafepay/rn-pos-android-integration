import { FC, useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Application from 'expo-application';
import * as RnPosAndroidIntegration from 'rn-pos-android-integration';
import type { PosMode } from 'rn-pos-android-integration';

import BrandLogo from 'src/components/brand-logo';
import ScreenHeader from 'src/components/screen-header';
import SegmentedControl, { type Option } from 'src/components/segmented-control';
import { useColors } from 'src/utils/colors';
import Storage, { AppEnvironment } from 'src/utils/storage';

const POS_MODE_OPTIONS: Option<PosMode>[] = [
  { label: 'Sunmi POS', value: 'sunmi-pos' },
  { label: 'Soft POS', value: 'soft-pos' },
];

const ENVIRONMENT_OPTIONS: Option<AppEnvironment>[] = [
  { label: 'Dev', value: 'dev' },
  { label: 'Test', value: 'test' },
  { label: 'Live', value: 'live' },
];

const SectionCard: FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <View className="gap-3">
    <Text className="ml-1 text-xs font-semibold uppercase tracking-wider text-muted">{label}</Text>
    <View className="rounded-2xl bg-surface p-4" style={{ borderCurve: 'continuous' }}>
      {children}
    </View>
  </View>
);

const Settings: FC = () => {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const [apiKey, setApiKey] = useState<string | undefined>();
  const [posMode, setPosMode] = useState<PosMode>('sunmi-pos');
  const [environment, setEnvironment] = useState<AppEnvironment>(Storage.DEFAULT_ENVIRONMENT);

  const appVersion = useMemo(
    () => `${Application.nativeApplicationVersion} · ${Application.nativeBuildVersion}`,
    []
  );

  const onSaveApiKey = useCallback((value: string) => {
    Storage.storeApiKey(value).catch((error) => Alert.alert('Error', (error as Error)?.message));
  }, []);

  const onSavePosMode = useCallback(async (mode: PosMode) => {
    try {
      setPosMode(mode);
      await Storage.storePosMode(mode);
      RnPosAndroidIntegration.setPosMode(mode);
    } catch (error) {
      Alert.alert('Error', (error as Error)?.message);
    }
  }, []);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const [storedKey, storedMode, storedEnv] = await Promise.all([
          Storage.getApiKey(),
          Storage.getPosMode(),
          Storage.getEnvironment(),
        ]);
        if (!active) {
          return;
        }
        const mode = storedMode ?? 'sunmi-pos';
        RnPosAndroidIntegration.setPosMode(mode);
        setPosMode(mode);
        setEnvironment(storedEnv);
        if (storedKey) {
          setApiKey(storedKey);
        } else {
          // Workaround to inject a default API key in development.
          const yourAPIKey = '2f2ecfab0b608bec716955f13d6930c66173ca8e';
          setApiKey(yourAPIKey);
          onSaveApiKey(yourAPIKey);
        }
      } catch (error) {
        if (__DEV__) {
          console.error(error);
        }
      }
    })();
    return () => {
      active = false;
    };
  }, [onSaveApiKey]);

  const onChangeEnvironment = useCallback((option: AppEnvironment) => {
    setEnvironment(option);
    Storage.storeEnvironment(option).catch((error) => Alert.alert('Error', (error as Error)?.message));
  }, []);

  return (
    <View className="flex-1 bg-background">
      <ScreenHeader title="Settings" subtitle="Configure your payment terminal" />

      <ScrollView
        contentContainerStyle={{ paddingHorizontal: 20, paddingTop: 8, paddingBottom: insets.bottom + 32, gap: 24 }}
        showsVerticalScrollIndicator={false}
      >
        <SectionCard label="API key">
          <TextInput
            defaultValue={apiKey}
            placeholder="Enter your MultiSafepay API key"
            placeholderTextColor={colors.muted}
            autoCapitalize="none"
            autoCorrect={false}
            className="rounded-xl bg-surface-2 px-4 py-3 text-content"
            style={{ borderCurve: 'continuous' }}
            onEndEditing={({ nativeEvent }) => onSaveApiKey(nativeEvent.text)}
          />
          <Text className="ml-1 mt-2 text-xs text-muted">Used to create orders against the selected environment.</Text>
        </SectionCard>

        <SectionCard label="Terminal mode">
          <SegmentedControl options={POS_MODE_OPTIONS} value={posMode} onChange={onSavePosMode} />
        </SectionCard>

        <SectionCard label="Environment">
          <SegmentedControl options={ENVIRONMENT_OPTIONS} value={environment} onChange={onChangeEnvironment} />
        </SectionCard>

        <View className="mt-2 items-center gap-2">
          <BrandLogo height={16} />
          <Text className="text-xs text-muted">MultiSafepay POS demo</Text>
          <Text className="text-xs text-muted" style={{ fontVariant: ['tabular-nums'] }}>
            Version {appVersion}
          </Text>
        </View>
      </ScrollView>
    </View>
  );
};

export default Settings;
