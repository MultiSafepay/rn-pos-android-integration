import AsyncStorage from "@react-native-async-storage/async-storage";

const Keys = {
  apiKey: "api-key",
};

const storeApiKey = async (value: string) => {
  return await AsyncStorage.setItem(Keys.apiKey, value);
};

const getApiKey = async () => {
  return await AsyncStorage.getItem(Keys.apiKey);
};

const Storage = {
  storeApiKey,
  getApiKey,
};
export default Storage;
