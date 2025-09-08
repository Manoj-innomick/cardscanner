import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  DeviceEventEmitter,
  ActivityIndicator,
  ScrollView,
  Button,
} from 'react-native';
import { NativeModules } from 'react-native';

interface CprData {
  cprNumber: string | null;
  fullName: string | null;
  dateOfBirth: string | null;
  nationality: string | null;
  gender: string | null;
  cardExpiryDate: string | null;
}

const CardScannerScreen: React.FC = () => {
  const [status, setStatus] = useState<string>('Ready. Press Start to begin scanning.');
  const [cprData, setCprData] = useState<CprData | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isScanning, setIsScanning] = useState<boolean>(false);

  useEffect(() => {
    const statusListener = DeviceEventEmitter.addListener('ScannerStatus', (statusMsg: string) => {
      setStatus(statusMsg);
      if (statusMsg.includes('Reading') || statusMsg.includes('Step')) {
        setIsLoading(true);
      } else if (statusMsg.includes('Successfully read')) {
        setIsLoading(false);
      }
    });

    const dataListener = DeviceEventEmitter.addListener('CprData', (data: CprData) => {
      setCprData(data);
      setIsLoading(false);
    });

    return () => {
      statusListener.remove();
      dataListener.remove();
      if (isScanning) {
        NativeModules.CprScanner.stopScanning();
      }
    };
  }, [isScanning]);

  const startScanning = () => {
    setIsScanning(true);
    NativeModules.CprScanner.startScanning();
  };

  const stopScanning = () => {
    setIsScanning(false);
    NativeModules.CprScanner.stopScanning();
  };

  const displayData = (data: CprData | null) => {
    if (!data) {
      return (
        <>
          <Text style={styles.label}>CPR Number:</Text>
          <Text style={styles.emptyField}>---</Text>
          <Text style={styles.label}>Full Name:</Text>
          <Text style={styles.emptyField}>---</Text>
          <Text style={styles.label}>Date of Birth:</Text>
          <Text style={styles.emptyField}>---</Text>
          <Text style={styles.label}>Nationality:</Text>
          <Text style={styles.emptyField}>---</Text>
          <Text style={styles.label}>Gender:</Text>
          <Text style={styles.emptyField}>---</Text>
          <Text style={styles.label}>Card Expiry Date:</Text>
          <Text style={styles.emptyField}>---</Text>
        </>
      );
    }
    return (
      <>
        <Text style={styles.label}>CPR Number:</Text>
        <Text style={styles.field}>{data.cprNumber || '---'}</Text>
        <Text style={styles.label}>Full Name:</Text>
        <Text style={styles.field}>{data.fullName || '---'}</Text>
        <Text style={styles.label}>Date of Birth:</Text>
        <Text style={styles.field}>{data.dateOfBirth || '---'}</Text>
        <Text style={styles.label}>Nationality:</Text>
        <Text style={styles.field}>{data.nationality || '---'}</Text>
        <Text style={styles.label}>Gender:</Text>
        <Text style={styles.field}>{data.gender || '---'}</Text>
        <Text style={styles.label}>Card Expiry Date:</Text>
        <Text style={styles.field}>{data.cardExpiryDate || '---'}</Text>
      </>
    );
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>CPR Card Scanner</Text>
        <Text style={styles.status}>{status}</Text>
        {isLoading && <ActivityIndicator size="large" color="#0000ff" style={styles.progress} />}
      </View>
      <View style={styles.buttonContainer}>
        <Button title={isScanning ? "Stop Scanning" : "Start Scanning"} onPress={isScanning ? stopScanning : startScanning} />
      </View>
      <View style={styles.dataContainer}>
        {displayData(cprData)}
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  header: { alignItems: 'center', marginBottom: 20 },
  title: { fontSize: 24, fontWeight: 'bold', marginBottom: 10 },
  status: { fontSize: 16, textAlign: 'center', marginBottom: 10, color: '#666' },
  progress: { marginTop: 10 },
  buttonContainer: { alignItems: 'center', marginBottom: 20 },
  dataContainer: { flex: 1 },
  label: { fontSize: 16, fontWeight: 'bold', marginTop: 10, marginBottom: 5 },
  field: { fontSize: 16, borderWidth: 1, borderColor: '#ddd', padding: 10, borderRadius: 5, backgroundColor: '#f9f9f9' },
  emptyField: { fontSize: 16, color: '#999', borderWidth: 1, borderColor: '#ddd', padding: 10, borderRadius: 5, backgroundColor: '#f9f9f9' },
});

export default CardScannerScreen;