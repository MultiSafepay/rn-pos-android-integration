import AsyncStorage from '@react-native-async-storage/async-storage';
import type { PosMode } from 'rn-pos-android-integration';

const Keys = {
  apiKey: 'api-key',
  posMode: 'pos-mode',
  environment: 'environment',
};

export type AppEnvironment = 'dev' | 'test' | 'live';

const DEFAULT_ENVIRONMENT: AppEnvironment = 'dev';
const DEFAULT_API_KEY = process.env.EXPO_PUBLIC_DEFAULT_API_KEY as string | undefined;

const storeApiKey = async (value: string) => {
  return await AsyncStorage.setItem(Keys.apiKey, value);
};

const getApiKey = async () => {
  return await AsyncStorage.getItem(Keys.apiKey);
};

const storePosMode = async (value: PosMode) => {
  return await AsyncStorage.setItem(Keys.posMode, value);
};

const getPosMode = async (): Promise<PosMode | null> => {
  const storedValue = await AsyncStorage.getItem(Keys.posMode);
  if (storedValue === 'sunmi-pos' || storedValue === 'soft-pos') {
    return storedValue;
  }
  return null;
};

const storeEnvironment = async (value: AppEnvironment) => {
  return await AsyncStorage.setItem(Keys.environment, value);
};

const getEnvironment = async (): Promise<AppEnvironment> => {
  const storedValue = await AsyncStorage.getItem(Keys.environment);
  if (storedValue === 'dev' || storedValue === 'test' || storedValue === 'live') {
    return storedValue;
  }
  return DEFAULT_ENVIRONMENT;
};

const initializeDefaults = async () => {
  const apiKey = await getApiKey();
  if (!apiKey && DEFAULT_API_KEY) {
    await storeApiKey(DEFAULT_API_KEY);
  }
};

const Storage = {
  storeApiKey,
  getApiKey,
  storePosMode,
  getPosMode,
  storeEnvironment,
  getEnvironment,
  initializeDefaults,
  DEFAULT_ENVIRONMENT,
  DEFAULT_API_KEY,
};
export default Storage;
