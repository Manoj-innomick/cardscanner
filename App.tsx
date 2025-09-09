import React, { useEffect, useState } from 'react';
import {
  AppState,
  NativeEventEmitter,
  NativeModules,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  useColorScheme,
  View
} from 'react-native';
import {
  SafeAreaProvider
} from 'react-native-safe-area-context';

interface CprData {
  cprNumber: string;
  fullName: string;
  dateOfBirth: string;
  nationality: string;
  gender: string;
  cardExpiryDate: string;
}

function App(): JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';
  const [status, setStatus] = useState<string>('Initializing...');
  const [cprData, setCprData] = useState<CprData | null>(null);

  useEffect(() => {
    const { CprScanner } = NativeModules;
    const eventEmitter = new NativeEventEmitter(CprScanner);

     CprScanner?.resume()
      .then(() => console.log('Scanner initialized'))
      .catch(err => console.error('Init error:', err));

    const statusListener = eventEmitter.addListener(
      'ScannerStatus',
      (status: string) => setStatus(status),
    );
    

    const dataListener = eventEmitter.addListener('CprData', (data: CprData) => {
      setCprData(data);
    });

    const handleAppStateChange = (nextAppState: string) => {
      if (nextAppState === 'active') {
        CprScanner?.resume();
      } else if (nextAppState === 'background') {
        CprScanner?.pause();
      }
    };
    const subscription = AppState.addEventListener('change', handleAppStateChange);

    return () => {
      statusListener.remove();
      dataListener.remove();
      subscription?.remove();
      CprScanner?.pause();
    };
  }, []);

  const backgroundStyle = {
    backgroundColor: isDarkMode ? '#000' : '#F0F4F8',
  };

  return (
    <SafeAreaProvider style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={backgroundStyle.backgroundColor}
      />
      <ScrollView contentInsetAdjustmentBehavior="automatic" style={backgroundStyle}>
        <View style={{ padding: 24 }}>
          <Text style={styles.title}>Bahrain CPR Scanner</Text>
          <Text style={styles.statusText}>{status}</Text>
          {cprData ? (
            <View style={styles.card}>
              <Text style={styles.label}>CPR Number:</Text>
              <Text style={styles.value}>{cprData.cprNumber}</Text>
              <Text style={styles.label}>Full Name:</Text>
              <Text style={styles.value}>{cprData.fullName}</Text>
              <Text style={styles.label}>Date of Birth:</Text>
              <Text style={styles.value}>{cprData.dateOfBirth}</Text>
              <Text style={styles.label}>Nationality:</Text>
              <Text style={styles.value}>{cprData.nationality}</Text>
              <Text style={styles.label}>Gender:</Text>
              <Text style={styles.value}>{cprData.gender}</Text>
              <Text style={styles.label}>Card Expiry Date:</Text>
              <Text style={styles.value}>{cprData.cardExpiryDate}</Text>
            </View>
          ) : (
            <Text style={styles.noData}>No data read yet. Insert card.</Text>
          )}
        </View>
      </ScrollView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1F2937',
    textAlign: 'center',
    marginBottom: 16,
  },
  statusText: {
    fontSize: 16,
    textAlign: 'center',
    padding: 12,
    backgroundColor: '#E5E7EB',
    borderRadius: 8,
    marginBottom: 24,
  },
  card: {
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 12,
    elevation: 4,
  },
  label: {
    fontSize: 16,
    fontWeight: 'bold',
    marginTop: 16,
    color: '#1F2937',
  },
  value: {
    fontSize: 16,
    marginBottom: 8,
    color: '#4B5563',
  },
  noData: {
    textAlign: 'center',
    fontSize: 16,
    color: '#6B7280',
    marginVertical: 24,
  },
});

export default App;