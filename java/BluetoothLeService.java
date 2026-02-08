import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import java.util.List;
import java.util.UUID;

public class BluetoothLeService extends Service {
    public static final String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public static final String ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public static final String ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public static final String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final String DEVICE_DOES_NOT_SUPPORT_UART = "com.example.bluetooth.le.DEVICE_DOES_NOT_SUPPORT_UART";
    public static final UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";
    public static final UUID FIRMWARE_REVISON_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_DISCONNECTED = 0;
    private static final String TAG = "MyBLE2";
    public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_POWER_LEVEL_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_POWER_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private final IBinder mBinder = new LocalBinder();
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    /* access modifiers changed from: private */
    public BluetoothGatt mBluetoothGatt;
    private BluetoothManager mBluetoothManager;
    /* access modifiers changed from: private */
    public int mConnectionState = 0;
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int i, int i2) {
            if (i2 == 2) {
                int unused = BluetoothLeService.this.mConnectionState = 2;
                BluetoothLeService.this.broadcastUpdate(BluetoothLeService.ACTION_GATT_CONNECTED);
                Log.i(BluetoothLeService.TAG, "Connected to GATT server.");
                Log.i(BluetoothLeService.TAG, "Attempting to start service discovery:" + BluetoothLeService.this.mBluetoothGatt.discoverServices());
            } else if (i2 == 0) {
                int unused2 = BluetoothLeService.this.mConnectionState = 0;
                Log.i(BluetoothLeService.TAG, "Disconnected from GATT server.");
                BluetoothLeService.this.broadcastUpdate(BluetoothLeService.ACTION_GATT_DISCONNECTED);
            }
        }

        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int i) {
            if (i == 0) {
                BluetoothLeService.this.broadcastUpdate(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
                return;
            }
            Log.w(BluetoothLeService.TAG, "onServicesDiscovered received: " + i);
        }

        public void onCharacteristicRead(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic, int i) {
            if (i == 0) {
                BluetoothLeService.this.broadcastUpdate(BluetoothLeService.ACTION_DATA_AVAILABLE, bluetoothGattCharacteristic);
            }
        }

        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
            BluetoothLeService.this.broadcastUpdate(BluetoothLeService.ACTION_DATA_AVAILABLE, bluetoothGattCharacteristic);
        }
    };
    private Handler mHandler;

    /* access modifiers changed from: private */
    public void broadcastUpdate(String str) {
        sendBroadcast(new Intent(str));
    }

    /* access modifiers changed from: private */
    public void broadcastUpdate(String str, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        int i;
        Intent intent = new Intent(str);
        if (TX_CHAR_UUID.equals(bluetoothGattCharacteristic.getUuid())) {
            intent.putExtra(EXTRA_DATA, bluetoothGattCharacteristic.getValue());
        } else if (UUID_HEART_RATE_MEASUREMENT.equals(bluetoothGattCharacteristic.getUuid())) {
            if ((bluetoothGattCharacteristic.getProperties() & 1) != 0) {
                Log.d(TAG, "Heart rate format UINT16.");
                i = 18;
            } else {
                Log.d(TAG, "Heart rate format UINT8.");
                i = 17;
            }
            int intValue = bluetoothGattCharacteristic.getIntValue(i, 1).intValue();
            Log.d(TAG, String.format("Received heart rate: %d", new Object[]{Integer.valueOf(intValue)}));
            intent.putExtra(EXTRA_DATA, String.valueOf(intValue));
        } else {
            byte[] value = bluetoothGattCharacteristic.getValue();
            if (value != null && value.length > 0) {
                StringBuilder sb = new StringBuilder(value.length);
                int length = value.length;
                for (int i2 = 0; i2 < length; i2++) {
                    sb.append(String.format("%02X ", new Object[]{Byte.valueOf(value[i2])}));
                }
                intent.putExtra(EXTRA_DATA, new String(value) + "\n" + sb.toString());
            }
            Log.d(TAG, "jyk broadcastUpdate2 etc2");
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public LocalBinder() {
        }

        /* access modifiers changed from: package-private */
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        if (this.mBluetoothManager == null) {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService("bluetooth");
            this.mBluetoothManager = bluetoothManager;
            if (bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        BluetoothAdapter adapter = this.mBluetoothManager.getAdapter();
        this.mBluetoothAdapter = adapter;
        if (adapter != null) {
            return true;
        }
        Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        return false;
    }

    public boolean connect(String str) {
        if (this.mBluetoothAdapter == null || str == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        String str2 = this.mBluetoothDeviceAddress;
        if (str2 == null || !str.equals(str2) || this.mBluetoothGatt == null) {
            BluetoothDevice remoteDevice = this.mBluetoothAdapter.getRemoteDevice(str);
            if (remoteDevice == null) {
                Log.w(TAG, "Device not found.  Unable to connect.");
                return false;
            }
            this.mBluetoothGatt = remoteDevice.connectGatt(this, true, this.mGattCallback);
            Log.d(TAG, "Trying to create a new connection.");
            this.mBluetoothDeviceAddress = str;
            this.mConnectionState = 1;
            return true;
        }
        Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
        if (!this.mBluetoothGatt.connect()) {
            return false;
        }
        this.mConnectionState = 1;
        return true;
    }

    public void disconnect() {
        BluetoothGatt bluetoothGatt;
        if (this.mBluetoothAdapter == null || (bluetoothGatt = this.mBluetoothGatt) == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
        } else {
            bluetoothGatt.disconnect();
        }
    }

    public void close() {
        BluetoothGatt bluetoothGatt = this.mBluetoothGatt;
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            this.mBluetoothGatt = null;
        }
    }

    public void readCharacteristic(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        BluetoothGatt bluetoothGatt;
        if (this.mBluetoothAdapter == null || (bluetoothGatt = this.mBluetoothGatt) == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
        } else {
            bluetoothGatt.readCharacteristic(bluetoothGattCharacteristic);
        }
    }

    public void enableTXNotification() {
        BluetoothGattService service = this.mBluetoothGatt.getService(RX_SERVICE_UUID);
        int i = 0;
        if (service == null) {
            int i2 = 0;
            while (i2 < 20) {
                SystemClock.sleep(50);
                service = this.mBluetoothGatt.getService(RX_SERVICE_UUID);
                if (service != null) {
                    break;
                }
                i2++;
            }
            Log.d(TAG, "-->enableTXNotification(): RxService try=" + i2);
            if (service == null) {
                Log.d(TAG, "enableTXNotification: Rx service not found!");
                broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
                return;
            }
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(TX_CHAR_UUID);
        if (characteristic == null) {
            while (i < 20) {
                SystemClock.sleep(50);
                characteristic = service.getCharacteristic(TX_CHAR_UUID);
                if (characteristic != null) {
                    break;
                }
                i++;
            }
            Log.d(TAG, "-->enableTXNotification(): TxChar try=" + i);
            if (characteristic == null) {
                Log.d(TAG, "enableTXNotification: Tx charateristic not found!");
                broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
                return;
            }
        }
        this.mBluetoothGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        this.mBluetoothGatt.writeDescriptor(descriptor);
        Log.d(TAG, "====>enableTXNotification end");
    }

    public void writeRXCharacteristic(byte[] bArr) {
        BluetoothGattService service = this.mBluetoothGatt.getService(RX_SERVICE_UUID);
        int i = 0;
        if (service == null) {
            int i2 = 0;
            while (i2 < 30) {
                SystemClock.sleep(50);
                service = this.mBluetoothGatt.getService(RX_SERVICE_UUID);
                if (service != null) {
                    break;
                }
                i2++;
            }
            Log.d(TAG, "-->writeRXCharacteristic(): RxService try=" + i2);
            if (service == null) {
                Log.d(TAG, "writeRXCharacteristic: Rx service not found!");
                broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
                return;
            }
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(RX_CHAR_UUID);
        if (characteristic == null) {
            while (i < 20) {
                SystemClock.sleep(50);
                characteristic = service.getCharacteristic(RX_CHAR_UUID);
                if (characteristic != null) {
                    break;
                }
                i++;
            }
            Log.d(TAG, "-->writeRXCharacteristic(); RxChar try=" + i);
            if (characteristic == null) {
                Log.d(TAG, "writeRXCharacteristic: Rx charateristic not found!");
                broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
                return;
            }
        }
        characteristic.setValue(bArr);
        this.mBluetoothGatt.writeCharacteristic(characteristic);
        if (DeviceControlActivity.isDcomPass) {
            Log.d(TAG, "[Tx]cmd=" + Integer.toHexString(bArr[3]));
            return;
        }
        Log.d(TAG, "[Tx]cmd=" + Integer.toHexString(bArr[2]));
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic bluetoothGattCharacteristic, boolean z) {
        BluetoothGatt bluetoothGatt;
        if (this.mBluetoothAdapter == null || (bluetoothGatt = this.mBluetoothGatt) == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        bluetoothGatt.setCharacteristicNotification(bluetoothGattCharacteristic, z);
        if (UUID_HEART_RATE_MEASUREMENT.equals(bluetoothGattCharacteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = bluetoothGattCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            this.mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        BluetoothGatt bluetoothGatt = this.mBluetoothGatt;
        if (bluetoothGatt == null) {
            return null;
        }
        return bluetoothGatt.getServices();
    }

    public int stateConnect() {
        return this.mConnectionState;
    }
}
