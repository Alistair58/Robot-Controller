package com.amhapps.robotcontroller

import android.app.Activity
import android.app.ComponentCaller
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.amhapps.robotcontroller.ui.theme.RobotControllerTheme

class MainActivity : ComponentActivity() {
    private var bluetoothService : BleService? = null
    private val robotAddress:String = "2C:CF:67:BD:C4:A2"
    private var status by mutableStateOf(0)
    private val activity:Activity = this
    private val robotServiceUUID = "94f493c8-c579-41c2-87ac-e12c02455864"
    private val motorCharacteristicUUID = "94f493ca-c579-41c2-87ac-e12c02455864"
    private val autoModeCharacteristicUUID = "94f493cc-c579-41c2-87ac-e12c02455864"
    private val stuckCharacteristicUUID = "94f493ce-c579-41c2-87ac-e12c02455864"
    private val turretCalibrationCharacteristicUUID = "94f493d0-c579-41c2-87ac-e12c02455864"
    private var motorCharacteristic:BluetoothGattCharacteristic? by mutableStateOf( null)
    private var autoModeCharacteristic:BluetoothGattCharacteristic? by mutableStateOf( null)
    private var stuckCharacteristic:BluetoothGattCharacteristic? by mutableStateOf( null)
    private var turretCalibrationCharacteristic:BluetoothGattCharacteristic? by mutableStateOf( null)
    companion object{
        val CONNECTED = 1
        val CONNECTING = 0
        val DISCONNECTED = -1
        val ROBOT_NOT_ON = -2
        val BLUETOOTH_ERROR = -3
        val AUTO_MODE = 2
        val ROBOT_STUCK = -4
        val CALIBRATING_TURRET = 3
        val TURRET_CALIBRATED = 4
        val TURRET_CALIBRATION_FAILURE = -5
        val SHOW_CALIBRATION_TUTORIAL = 5
        val VALID_STATUSES = intArrayOf(
            CONNECTED,
            CONNECTING, 
            DISCONNECTED, 
            ROBOT_NOT_ON,
            BLUETOOTH_ERROR,
            AUTO_MODE,
            ROBOT_STUCK,
            CALIBRATING_TURRET,
            TURRET_CALIBRATED,
            TURRET_CALIBRATION_FAILURE)
    }
    fun changeStatus(newStatus:Int){
        println("Changing status to $newStatus")
        if(newStatus in VALID_STATUSES){
            println("Done")
            status = newStatus
        }
    }
    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BleService.ACTION_GATT_CONNECTED -> {
                    println("Received gatt connected")
                    //Only change the status after we have the services
                }
                BleService.ACTION_GATT_DISCONNECTED -> {
                    println("Received gatt disconnected")
                    status = DISCONNECTED
                }
                BleService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    motorCharacteristic = bluetoothService?.getCharacteristic(
                        bluetoothService?.getSupportedGattServices(),
                        robotServiceUUID,
                        motorCharacteristicUUID
                    )
                    autoModeCharacteristic = bluetoothService?.getCharacteristic(
                        bluetoothService?.getSupportedGattServices(),
                        robotServiceUUID,
                        autoModeCharacteristicUUID
                    )
                    stuckCharacteristic = bluetoothService?.getCharacteristic(
                        bluetoothService?.getSupportedGattServices(),
                        robotServiceUUID,
                        stuckCharacteristicUUID
                    )
                    if(stuckCharacteristic!=null){
                        bluetoothService?.setCharacteristicNotification(stuckCharacteristic!!,true)
                    }
                    turretCalibrationCharacteristic = bluetoothService?.getCharacteristic(
                        bluetoothService?.getSupportedGattServices(),
                        robotServiceUUID,
                        turretCalibrationCharacteristicUUID
                    )
                    Thread.sleep(50) //Works?
                    if(turretCalibrationCharacteristic!=null){
                        bluetoothService?.setCharacteristicNotification(turretCalibrationCharacteristic!!,true)
                    }
                    status = CONNECTED
                    //Will cause a recomposition and so controls will be shown
                }
                BleService.ACTION_GATT_TIMEOUT -> {
                    status = ROBOT_NOT_ON
                }
                BleService.ACTION_GATT_AUTO_MODE_CONFIRMED -> {
                    println("Auto mode confirmed")
                    status = AUTO_MODE
                }
                BleService.ACTION_GATT_MANUAL_MODE_CONFIRMED -> {
                    println("Manual mode confirmed")
                    status = CONNECTED
                }
                BleService.ACTION_GATT_ROBOT_STUCK -> {
                    println("Robot stuck")
                    status = ROBOT_STUCK
                }
                BleService.ACTION_GATT_TURRET_CALIBRATION -> {
                    println("Calibrating turret")
                    status = CALIBRATING_TURRET
                }
                BleService.ACTION_GATT_TURRET_CALIBRATED -> {
                    println("Turret calibrated")
                    status = TURRET_CALIBRATED
                }
                BleService.ACTION_GATT_TURRET_CALIBRATION_FAILURE -> {
                    println("Could not calibrate turret")
                    status = TURRET_CALIBRATION_FAILURE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //Requires a newer API to fix the warning
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothService != null) {
            bluetoothService!!.connect(robotAddress,activity,{status = it})
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
            addAction(BleService.ACTION_GATT_TIMEOUT)
            addAction(BleService.ACTION_GATT_AUTO_MODE_CONFIRMED)
            addAction(BleService.ACTION_GATT_MANUAL_MODE_CONFIRMED)
            addAction(BleService.ACTION_GATT_ROBOT_STUCK)
            addAction(BleService.ACTION_GATT_TURRET_CALIBRATION)
            addAction(BleService.ACTION_GATT_TURRET_CALIBRATION_FAILURE)
            addAction(BleService.ACTION_GATT_TURRET_CALIBRATED)
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
                if(!bluetooth.initialize(activity,{status = it})){
                    println("Unable to initialise Bluetooth")
                    finish()
                }
                println("Connecting")
                bluetooth.connect(robotAddress,activity,{status = it})
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        caller: ComponentCaller
    ) {
        super.onActivityResult(requestCode, resultCode, data, caller)
        when(requestCode){
            bluetoothService?.REQUEST_ENABLE_BT -> {
                if(resultCode== RESULT_OK){
                    bluetoothService?.connect(robotAddress,activity,{status = it})
                }
                else{
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    ActivityCompat.startActivityForResult(
                        activity,
                        enableBtIntent,
                        bluetoothService?.REQUEST_ENABLE_BT!!,
                        null
                    )
                    //Keeps asking
                    //fine as only I'm using the app
                }
            }


        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gattServiceIntent = Intent(this, BleService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        enableEdgeToEdge()
        setContent {
            RobotControllerTheme {
                App(status,motorCharacteristic,autoModeCharacteristic)
            }
        }
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun App(status:Int,motorCharacteristic: BluetoothGattCharacteristic?,autoModeCharacteristic: BluetoothGattCharacteristic?) {
        val onStatusChange: (Int) -> Unit = { this.status = it }
        //this. doesn't work inside the composable
        when (status) {
             DISCONNECTED-> {
                LandscapeColumn {
                    Text("Disconnected", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(30.dp))
                    ConnectButton(onStatusChange)
                }
             }
             CONNECTED, ROBOT_STUCK, TURRET_CALIBRATED, TURRET_CALIBRATION_FAILURE-> {
                var leftThrottle = remember { mutableStateOf(50) }
                var rightThrottle = remember { mutableStateOf(50) }
                val controller = Controller(
                    bluetoothService,
                    motorCharacteristic,
                    autoModeCharacteristic,
                    leftThrottle,
                    rightThrottle,
                    {leftThrottle.value = it},
                    {rightThrottle.value = it},
                    onStatusChange)
                controller.RobotControls()
                when(status) {
                    ROBOT_STUCK -> {
                        BasicAlertDialog(
                            onDismissRequest = { onStatusChange(CONNECTED) },
                            modifier = Modifier.background(color = Color.White),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Robot is stuck",
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "If the robot was in Auto Mode, then manual control has resumed",
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    TURRET_CALIBRATED -> {
                        BasicAlertDialog(
                            onDismissRequest = { onStatusChange(CONNECTED) },
                            modifier = Modifier.background(color = Color.White),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Turret Calibrated Successfully",
                                    color = Color.Green,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp,
                                    lineHeight = 40.sp
                                )
                            }
                        }
                    }
                    TURRET_CALIBRATION_FAILURE -> {
                        BasicAlertDialog(
                            onDismissRequest = { onStatusChange(CONNECTED) },
                            modifier = Modifier.background(color = Color.White),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Turret Calibration Failed",
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp,
                                    lineHeight = 40.sp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "The orientation of the turret has remained unchanged.\nIf this was undesired, please try again.",
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
            SHOW_CALIBRATION_TUTORIAL -> {
                BackHandler {
                    onStatusChange(CONNECTED)
                }
                LandscapeColumn {
                    Row() {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.3f)
                        ) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                "Ensure that the robot has no objects closer than 30cm to the turret.",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                "Place the calibration device on the opposite side to which the turret is currently facing"
                                        + ". This may require flipping the calibration arm.",
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            //0.9f of the remaining width
                        ) {
                            Row(
                                modifier = Modifier.fillMaxHeight(0.8f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(0.33f)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.calibration_device),
                                        contentDescription = "Turret calibration on the left side",
                                    )
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(0.5f)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.calibration_left),
                                        contentDescription = "Turret calibration on the left side",
                                    )
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(1f)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.calibration_right),
                                        contentDescription = "Turret calibration on the right side",
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            turretCalibrationCharacteristic?.value = byteArrayOf(BleService.turretCalibrationPacket,1)
                            bluetoothService?.writeCharacteristic(turretCalibrationCharacteristic)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta),
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier.padding(10.dp,5.dp)
                    ) {
                        Text(text="Begin Turret Calibration", fontSize = 20.sp,color = Color.White,
                            modifier = Modifier.padding(5.dp))
                    }
                }
            }
            CALIBRATING_TURRET -> {
                LandscapeColumn {
                    Text("Calibrating Turret", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Keep the robot still and keep your finger in the correct position", fontSize = 24.sp)
                }
            }
            CONNECTING -> {
                LandscapeColumn {
                    Text("Connecting", fontSize = 40.sp)
                }
            }
            AUTO_MODE->{
                LandscapeColumn {
                    Text("Auto mode", fontSize = 40.sp)
                    ExitAutoModeButton()
                    DisconnectButton(bluetoothService = bluetoothService)
                }
            }
            ROBOT_NOT_ON-> {
                LandscapeColumn {
                    Text("Robot is not on", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(30.dp))
                    ConnectButton(onStatusChange)
                }
            }
            BLUETOOTH_ERROR-> {
                LandscapeColumn {
                    Text("Bluetooth error", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(30.dp))
                    ConnectButton(onStatusChange)
                }
            }

        }
    }

    @Composable
    fun ConnectButton(onStatusChange:(Int)->Unit){
        Button(
            onClick = {
                bluetoothService?.connect(robotAddress, activity, onStatusChange)
                onStatusChange(0)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
            shape = RoundedCornerShape(5.dp)

        ) {
            Text(
                text="Connect",
                fontSize = 30.sp, 
                color = Color.White, 
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    @Composable
    private fun ExitAutoModeButton(){
        Button(
            onClick = {
                println(autoModeCharacteristic==null)
                if(null != autoModeCharacteristic){
                    autoModeCharacteristic!!.value = byteArrayOf(0xaa.toByte(),0)
                    bluetoothService?.writeCharacteristic(autoModeCharacteristic)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier.padding(10.dp,5.dp)
        ) {
            Text(text="Exit Auto Mode", fontSize = 20.sp,color = Color.White,
                modifier = Modifier.padding(5.dp))
        }

    }

}

@Composable
fun LandscapeColumn(content: @Composable() () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}



@Composable
fun DisconnectButton(bluetoothService: BleService?){
    Button(
        onClick = {bluetoothService?.disconnect()}, //The main app will sort it out and show the disconnected page
        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
        shape = RoundedCornerShape(5.dp),
        modifier = Modifier.padding(10.dp,5.dp)
    ) {
        Text(text="Disconnect", fontSize = 20.sp,color = Color.White,
            modifier = Modifier.padding(5.dp))
    }

}



