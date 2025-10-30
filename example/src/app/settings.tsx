import { Stack } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Colors from 'src/utils/colors';
import Storage, { AppEnvironment } from 'src/utils/storage';
import * as RnPosAndroidIntegration from 'rn-pos-android-integration';

import type { PosMode } from 'rn-pos-android-integration';

const Settings = () => {
  const [apiKey, setApiKey] = useState<string | undefined>();
  const [posMode, setPosMode] = useState<PosMode>('sunmi-pos');
  const [environment, setEnvironment] = useState<AppEnvironment>(Storage.DEFAULT_ENVIRONMENT);
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
      // const yourAPIKey = "YOUR_API_KEY";
      // setApiKey(yourAPIKey);
      // await onSaveApiKey(yourAPIKey);
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
      <Stack.Screen
        options={{
          title: 'Settings',
          headerStyle: { backgroundColor: Colors.secondary },
          headerTintColor: Colors.white,
          headerTitleStyle: {
            fontWeight: 'bold',
          },
        }}
      />
      <View style={{ margin: 15, padding: 15 }}>
        <Text style={{ fontWeight: 'bold', marginBottom: 10 }}>Api Key:</Text>
        <TextInput
          defaultValue={apiKey}
          placeholder="Enter your API key (LIVE)"
          style={{
            borderColor: Colors.secondary,
            borderWidth: 1,
            borderRadius: 5,
            padding: 10,
          }}
          onEndEditing={({ nativeEvent }) => {
            if (__DEV__) {
              console.log('Api key:', nativeEvent.text);
            }
            onSaveApiKey(nativeEvent.text);
          }}
        />
        <View style={{ marginTop: 30 }}>
          <Text style={{ fontWeight: 'bold', marginBottom: 10 }}>POS Mode:</Text>
          <View style={{ flexDirection: 'row' }}>
            {posModeOptions.map((modeOption, index) => {
              const isSelected = posMode === modeOption;
              return (
                <Pressable
                  key={modeOption}
                  onPress={() => onSavePosMode(modeOption)}
                  style={{
                    flex: 1,
                    paddingVertical: 12,
                    borderWidth: 1,
                    borderRadius: 6,
                    borderColor: isSelected ? Colors.secondary : Colors.secondaryLight,
                    backgroundColor: isSelected ? Colors.secondary : Colors.white,
                    marginRight: index === posModeOptions.length - 1 ? 0 : 10,
                  }}
                >
                  <Text
                    style={{
                      color: isSelected ? Colors.white : Colors.secondary,
                      fontWeight: 'bold',
                      textAlign: 'center',
                      textTransform: 'uppercase',
                    }}
                  >
                    {modeOption === 'sunmi-pos' ? 'Sunmi POS' : 'Soft POS'}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>
        <View style={{ marginTop: 30 }}>
          <Text style={{ fontWeight: 'bold', marginBottom: 10 }}>Environment:</Text>
          <View style={{ flexDirection: 'row' }}>
            {environmentOptions.map((option, index) => {
              const isSelected = environment === option;
              return (
                <Pressable
                  key={option}
                  onPress={() => {
                    setEnvironment(option);
                    Storage.storeEnvironment(option).catch((error) => {
                      Alert.alert('Error', (error as Error)?.message);
                    });
                  }}
                  style={{
                    flex: 1,
                    paddingVertical: 12,
                    borderWidth: 1,
                    borderRadius: 6,
                    borderColor: isSelected ? Colors.secondary : Colors.secondaryLight,
                    backgroundColor: isSelected ? Colors.secondary : Colors.white,
                    marginRight: index === environmentOptions.length - 1 ? 0 : 10,
                  }}
                >
                  <Text
                    style={{
                      color: isSelected ? Colors.white : Colors.secondary,
                      fontWeight: 'bold',
                      textAlign: 'center',
                      textTransform: 'uppercase',
                    }}
                  >
                    {option}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>
      </View>
    </SafeAreaView>
  );
};
export default Settings;
