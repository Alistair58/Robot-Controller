package com.amhapps.robotcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.STATE_CONNECTED
import android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat

class BleService() : Service() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt:BluetoothGatt? = null
    private val binder = LocalBinder()
    private var connectionState = STATE_DISCONNECTED


    private val bleGattCallback = object : BluetoothGattCallback(){
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                println("Connected")
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
                //If we've connected we probably don't need to check we have Bluetooth enabled
                println("Discovering services")
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                println("Disconnected")
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            println("onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            } else {
                println("onServicesDiscovered received: $status")
            }
        }
    }
    companion object {
        const val ACTION_GATT_CONNECTED =
            "com.amhapps.robotcontroller.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.amhapps.robotcontroller.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.amhapps.robotcontroller.ACTION_GATT_SERVICES_DISCOVERED"
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2

    }

    fun getSupportedGattServices(): List<BluetoothGattService?>? {
        return bluetoothGatt?.services
    }

    fun getMotorCharacteristic(gattServices:List<BluetoothGattService?>?,gattServiceUUID:String,gattCharacteristicUUID:String):BluetoothGattCharacteristic?{
        if(null == gattServices) return null
        gattServices.forEach{ gattService ->
            val foundServiceUUID = gattService?.uuid.toString()
            println(foundServiceUUID)
            if(foundServiceUUID==gattServiceUUID){
                val gattCharacteristics = gattService?.characteristics ?: return null
                gattCharacteristics.forEach{ gattCharacteristic ->
                    val foundCharacteristicUUID = gattCharacteristic?.uuid.toString()
                    println(foundCharacteristicUUID)
                    if(foundCharacteristicUUID==gattCharacteristicUUID){
                        println("Found characteristic")
                        return gattCharacteristic
                    }
                }
            }
        }
        return null
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService() : BleService {
            return this@BleService
        }
    }
    fun initialize(): Boolean {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            println("Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    fun connect(address: String,activity: Activity): Boolean {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                println("Got device with addr $address")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT),561)
                    return false //TODO Handle
                }
                bluetoothGatt = device.connectGatt(this,false,bleGattCallback)
                println("Connected")
                return true
            } catch (exception: IllegalArgumentException) {
                println("Device not found with provided address.")
                return false
            }
        } ?: run {
            println("BluetoothAdapter not initialized")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        if(null == characteristic){
            println("Null characteristic")
            return
        }
        bluetoothGatt?.let { gatt ->
            //The deprecated stuff is used as the newer stuff requires API 33 (Android 13)
            println("Sent write characteristic")
            gatt.writeCharacteristic(characteristic)
            //Doesn't seem to offer a no_response option
        } ?: run {
            println("BluetoothGatt not initialized")
        }
    }

    override fun onUnbind(intent: Intent?): Boolean { //Automatically called
        close()
        return super.onUnbind(intent)
    }

    private fun close() { //Avoids draining the device battery
        bluetoothGatt?.let { gatt ->
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            gatt.close()
            bluetoothGatt = null
        }
    }
}
