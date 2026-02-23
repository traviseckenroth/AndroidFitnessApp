package com.example.myapplication.util

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleHeartRateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    
    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _foundDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val foundDevices: StateFlow<List<BluetoothDevice>> = _foundDevices

    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // Some devices might not have a name in the scan record, but Garmin usually does.
            // We scan without filters now to be more inclusive.
            if (device.name != null && _foundDevices.value.none { it.address == device.address }) {
                Log.d("BleHRManager", "Discovered: ${device.name} - ${device.address}")
                _foundDevices.value = _foundDevices.value + device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleHRManager", "Scan failed with error: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            Log.e("BleHRManager", "BluetoothLeScanner not available.")
            return
        }
        
        _foundDevices.value = emptyList()
        
        // Removed specific service UUID filter because some devices (like Garmin) 
        // might not broadcast it in the advertisement packet.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
        Log.d("BleHRManager", "Scanning for BLE devices (unfiltered)...")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bluetoothLeScanner?.stopScan(scanCallback)
        Log.d("BleHRManager", "Scan stopped.")
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        Log.d("BleHRManager", "Connecting to ${device.name} (${device.address})")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun cleanup() {
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null
        _isConnected.value = false
        _heartRate.value = 0
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BleHRManager", "Connected to GATT server.")
                _isConnected.value = true
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BleHRManager", "Disconnected from GATT server.")
                _isConnected.value = false
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(HEART_RATE_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
                
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Log.i("BleHRManager", "Heart rate notifications enabled.")
                    }
                } else {
                    Log.w("BleHRManager", "Heart Rate Service found but characteristic missing.")
                }
            } else {
                Log.w("BleHRManager", "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
             if (HEART_RATE_MEASUREMENT_CHAR_UUID == characteristic.uuid) {
                val hrValue = parseHeartRate(value)
                _heartRate.value = hrValue
            }
        }
    }

    private fun parseHeartRate(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        
        val flag = data[0].toInt()
        val format = if (flag and 0x01 != 0) {
            BluetoothGattCharacteristic.FORMAT_UINT16
        } else {
            BluetoothGattCharacteristic.FORMAT_UINT8
        }
        
        return if (format == BluetoothGattCharacteristic.FORMAT_UINT8) {
            data[1].toInt() and 0xFF
        } else {
            val low = data[1].toInt() and 0xFF
            val high = data[2].toInt() and 0xFF
            (high shl 8) or low
        }
    }
}
