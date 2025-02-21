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
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.startActivityForResult
import java.util.Timer
import kotlin.concurrent.schedule

class BleService() : Service() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt:BluetoothGatt? = null
    private val binder = LocalBinder()
    private var connectionState = STATE_DISCONNECTED
    val REQUEST_ENABLE_BT = 570




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
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                println("Disconnected")
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
            else if (newState == STATE_TIMEOUT){
                println("Timeout")
                connectionState = STATE_TIMEOUT
                broadcastUpdate(ACTION_GATT_TIMEOUT)
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
        const val ACTION_GATT_TIMEOUT =
            "com.amhapps.robotcontroller.ACTION_GATT_TIMEOUT"
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2
        private const val STATE_TIMEOUT = -1


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
    @SuppressLint("MissingPermission")
    fun initialize(activity: Activity,onStatusChange:(Int)->Unit): Boolean {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            onStatusChange(MainActivity.BLUETOOTH_ERROR)
            return false
        }
        if(! bluetoothAdapter!!.isEnabled){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(activity,enableBtIntent,REQUEST_ENABLE_BT,null)
            //Don't change status as a pop up will deal with this
            return false
        }
        return true
    }

    fun connect(address: String,activity: Activity,onStatusChange:(Int)->Unit) {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT),561)
                    connect(address,activity,onStatusChange)
                    //Not suitable for actual users but suitable for me
                }
                bluetoothGatt = device.connectGatt(this,false,bleGattCallback)
                Timer("connectionTimeout",false).schedule(10000){
                    //if no connection after 10s cancel
                    if(connectionState != STATE_CONNECTED){
                        bleGattCallback.onConnectionStateChange(bluetoothGatt,BluetoothGatt.GATT_FAILURE,
                            STATE_TIMEOUT)
                    }


                }
            } catch (exception: IllegalArgumentException) {
                //If the address isn't a mac address
                onStatusChange(MainActivity.BLUETOOTH_ERROR)
            }
        } ?: run {
            onStatusChange(MainActivity.BLUETOOTH_ERROR)
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        if(null == characteristic) return
        bluetoothGatt?.writeCharacteristic(characteristic) ?: run {
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
