import { Stack } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { Alert, Text, TextInput, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import Colors from "src/utils/colors";
import Storage from "src/utils/storage";

const Settings = () => {
  const [apiKey, setApiKey] = useState<string | undefined>();
  const onSaveApiKey = useCallback((apiKey: string) => {
    Storage.storeApiKey(apiKey).catch((error) => {
      Alert.alert("Error", (error as Error)?.message);
    });
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

  useEffect(() => {
    try {
      tryToInitializeApiKey();
    } catch (e) {
      console.error(e);
    }
  }, [tryToInitializeApiKey]);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: Colors.white }}>
      <Stack.Screen
        options={{
          title: "Settings",
          headerStyle: { backgroundColor: Colors.secondary },
          headerTintColor: Colors.white,
          headerTitleStyle: {
            fontWeight: "bold",
          },
        }}
      />
      <View style={{ margin: 15, padding: 15 }}>
        <Text style={{ fontWeight: "bold", marginBottom: 10 }}>Api Key:</Text>
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
              console.log("Api key:", nativeEvent.text);
            }
            onSaveApiKey(nativeEvent.text);
          }}
        />
      </View>
    </SafeAreaView>
  );
};
export default Settings;
