import { StyleSheet, Text, View } from 'react-native';

import * as RnPosAndroidIntegration from 'rn-pos-android-integration';

export default function App() {
  return (
    <View style={styles.container}>
      <Text>{RnPosAndroidIntegration.hello()}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
