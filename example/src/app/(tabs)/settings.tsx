import { Stack } from 'expo-router';
import { useCallback, useEffect, useMemo, useState, FC } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as Application from 'expo-application';
import Colors from 'src/utils/colors';
import Storage, { AppEnvironment } from 'src/utils/storage';
import * as RnPosAndroidIntegration from 'rn-pos-android-integration';

import type { PosMode } from 'rn-pos-android-integration';

interface SettingsButtonProps {
  title: string;
  onPress: () => void;
  isSelected: boolean;
  hasRightMargin?: boolean;
}

const SettingsButton: FC<SettingsButtonProps> = ({ isSelected, title, onPress, hasRightMargin }) => {
  return (
    <Pressable
      onPress={onPress}
      style={{
        flex: 1,
        paddingVertical: 12,
        borderWidth: 1,
        borderRadius: 6,
        borderColor: isSelected ? Colors.primary : Colors.secondaryLight,
        backgroundColor: isSelected ? Colors.primary : Colors.white,
        marginRight: hasRightMargin ? 10 : 0,
      }}
    >
      <Text
        style={{
          color: isSelected ? Colors.white : Colors.primary,
          fontWeight: 'bold',
          textAlign: 'center',
          textTransform: 'uppercase',
        }}
      >
        {title}
      </Text>
    </Pressable>
  );
};

const Settings: FC = () => {
  const [apiKey, setApiKey] = useState<string | undefined>();
  const [posMode, setPosMode] = useState<PosMode>('sunmi-pos');
  const [environment, setEnvironment] = useState<AppEnvironment>(Storage.DEFAULT_ENVIRONMENT);

  const appVersion = useMemo(() => {
    return `${Application.nativeApplicationVersion} - ${Application.nativeBuildVersion}`;
  }, []);

  const onSaveApiKey = useCallback((apiKey: string) => {
    Storage.storeApiKey(apiKey).catch((error) => {
      Alert.alert('Error', (error as Error)?.message);
    });
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

  const tryToInitializeApiKey = useCallback(async () => {
    const apiKey = await Storage.getApiKey();
    if (apiKey) {
      setApiKey(apiKey);
    } else {
      // Uncomment the following lines as a workaround to inject the API key in development
      const yourAPIKey = '2f2ecfab0b608bec716955f13d6930c66173ca8e'; // Dev API key
      setApiKey(yourAPIKey);
      await onSaveApiKey(yourAPIKey);
    }
  }, [onSaveApiKey]);

  const initializePosMode = useCallback(async () => {
    const storedPosMode = await Storage.getPosMode();
    const mode = storedPosMode ?? 'sunmi-pos';
    setPosMode(mode);
    RnPosAndroidIntegration.setPosMode(mode);
  }, []);

  const initializeEnvironment = useCallback(async () => {
    const storedEnvironment = await Storage.getEnvironment();
    setEnvironment(storedEnvironment);
  }, []);

  useEffect(() => {
    try {
      tryToInitializeApiKey();
    } catch (e) {
      console.error(e);
    }
  }, [tryToInitializeApiKey]);

  useEffect(() => {
    initializePosMode().catch((error) => {
      Alert.alert('Error', (error as Error)?.message);
    });
  }, [initializePosMode]);

  useEffect(() => {
    initializeEnvironment().catch((error) => {
      Alert.alert('Error', (error as Error)?.message);
    });
  }, [initializeEnvironment]);

  const posModeOptions = useMemo<PosMode[]>(() => ['sunmi-pos', 'soft-pos'], []);
  const environmentOptions = useMemo<AppEnvironment[]>(() => ['dev', 'test', 'live'], []);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: Colors.white }}>
      <Stack.Screen />
      <View style={{ margin: 15, padding: 15 }}>
        <Text style={styles.sectionText}>Api Key:</Text>
        <TextInput
          defaultValue={apiKey}
          placeholder="Enter your API key"
          style={styles.textInput}
          onEndEditing={({ nativeEvent }) => {
            if (__DEV__) {
              console.log('Api key:', nativeEvent.text);
            }
            onSaveApiKey(nativeEvent.text);
          }}
        />
        <View style={{ marginTop: 30 }}>
          <Text style={styles.sectionText}>POS Mode:</Text>
          <View style={{ flexDirection: 'row' }}>
            {posModeOptions.map((modeOption, index) => {
              const isSelected = posMode === modeOption;
              return (
                <SettingsButton
                  key={modeOption}
                  onPress={() => onSavePosMode(modeOption)}
                  title={modeOption === 'sunmi-pos' ? 'Sunmi POS' : 'Soft POS'}
                  isSelected={isSelected}
                  hasRightMargin={index !== posModeOptions.length - 1}
                />
              );
            })}
          </View>
        </View>
        <View style={{ marginTop: 30 }}>
          <Text style={styles.sectionText}>Environment:</Text>
          <View style={{ flexDirection: 'row' }}>
            {environmentOptions.map((option, index) => {
              const isSelected = environment === option;
              return (
                <SettingsButton
                  key={option}
                  onPress={() => {
                    setEnvironment(option);
                    Storage.storeEnvironment(option).catch((error) => {
                      Alert.alert('Error', (error as Error)?.message);
                    });
                  }}
                  title={option}
                  isSelected={isSelected}
                  hasRightMargin={index !== environmentOptions.length - 1}
                />
              );
            })}
          </View>
        </View>
        <View style={{ marginTop: 30, paddingTop: 20, borderTopWidth: 1, borderTopColor: Colors.secondaryLight }}>
          <Text style={[styles.sectionText, { textAlign: 'center', fontWeight: 'normal', color: Colors.secondary }]}>
            Version: {appVersion}
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
};
export default Settings;

const styles = StyleSheet.create({
  sectionText: { fontWeight: 'bold', marginBottom: 10, color: Colors.primary },
  textInput: {
    borderColor: Colors.primary,
    borderWidth: 1,
    borderRadius: 5,
    padding: 10,
    color: Colors.primary,
  },
});
