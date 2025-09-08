/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import {
  SafeAreaProvider
} from 'react-native-safe-area-context';
import CardScannerScreen from './src/components/CardScannerScreen';

function App() {

  return (
    <SafeAreaProvider>
      <CardScannerScreen />
    </SafeAreaProvider>
  );
}

export default App;
