package com.amhapps.robotcontroller

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.amhapps.robotcontroller.ui.theme.Controller
import com.amhapps.robotcontroller.ui.theme.RobotControllerTheme
import kotlin.math.round

class MainActivity : ComponentActivity() {
    private var bluetoothService : BleService? = null
    private val robotAddress:String = "2C:CF:67:BD:C4:A2"
    private var connected by mutableStateOf(false)
    private val activity:Activity = this
    private val motorServiceUUID = "94f493c8-c579-41c2-87ac-e12c02455864"
    private val motorCharacteristicUUID = "94f493ca-c579-41c2-87ac-e12c02455864"
    private var motorCharacteristic:BluetoothGattCharacteristic? by mutableStateOf( null)
    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BleService.ACTION_GATT_CONNECTED -> {
                    println("Received gatt connected")
                    connected = true
                }
                BleService.ACTION_GATT_DISCONNECTED -> {
                    println("Received gatt disconnected")
                    connected = false
                }
                BleService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    // Show all the supported services and characteristics on the user interface.
                    motorCharacteristic = bluetoothService?.getMotorCharacteristic(
                        bluetoothService?.getSupportedGattServices(),
                        motorServiceUUID,
                        motorCharacteristicUUID
                    )
                    println("MotorCharacteristic == null ${motorCharacteristic==null}")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //Requires a newer API to fix the warning
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothService != null) {
            val result = bluetoothService!!.connect(robotAddress,this)
            println("Connect request result=$result")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BleService.ACTION_GATT_CONNECTED)
            addAction(BleService.ACTION_GATT_DISCONNECTED)
            addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED)
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            componentName: ComponentName,
            service: IBinder
        ) {
            println("Service connection")
            bluetoothService = (service as BleService.LocalBinder).getService()
            bluetoothService?.let { bluetooth ->
                if(!bluetooth.initialize()){
                    println("Unable to initialise Bluetooth")
                    finish()
                }
                println("Connecting")
                bluetooth.connect(robotAddress,activity)
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gattServiceIntent = Intent(this, BleService::class.java)
        println("Binding service")
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }
        enableEdgeToEdge()
        setContent {
            RobotControllerTheme {
                App(connected,motorCharacteristic)
            }
        }
    }

    @Composable
    fun App(connected:Boolean,motorCharacteristic: BluetoothGattCharacteristic?){

        if(connected){
            val controller = Controller(bluetoothService,motorCharacteristic)
            controller.RobotControls()
        }
        else{
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                Text("Connecting", fontSize = 40.sp)
            }

        }
    }

}







