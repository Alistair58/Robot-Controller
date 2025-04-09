package com.amhapps.robotcontroller

import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.round

//
class Controller(private val bluetoothService: BleService?,private val motorCharacteristic: BluetoothGattCharacteristic?,private val autoModeCharacteristic: BluetoothGattCharacteristic?) {
    private var leftThrottle = 50
    private var rightThrottle = 50
    private val controllerDelay = 50L
    @Composable
    fun RobotControls(){
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight(0.8f).fillMaxWidth()
            ){
                MotorControlSlider({leftThrottle = it })
                MotorControlSlider({rightThrottle = it })
            }
            Row(horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ){
                LandscapeColumn(){
                    Row {
                        AutoModeButton()
                        DisconnectButton(bluetoothService)
                    }
                }

            }
        }
        LaunchedEffect(motorCharacteristic) {
            while(motorCharacteristic!=null){
                sendControls()
                delay(controllerDelay)
            }
        }
    }

    private fun sendControls(){
        if(null == motorCharacteristic) return
        motorCharacteristic.value = byteArrayOf(0xa1.toByte(),leftThrottle.toByte(),rightThrottle.toByte())
        bluetoothService?.writeCharacteristic(motorCharacteristic)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MotorControlSlider(onThrottleChange:(Int)->Unit){
        var throttle by remember { mutableStateOf(50) } //Works better with the local variable
        onThrottleChange(50) //Very important
        Column(modifier = Modifier.padding(100.dp,10.dp)){
            val interactionSource = remember { MutableInteractionSource() }
            LaunchedEffect(interactionSource) { //Launch a new thread when an interaction occurs
                interactionSource.interactions.collect { interaction ->
                    if(interaction is DragInteraction.Stop){ //When the user lets go, reset it, like a normal controller
                        onThrottleChange(50)
                        throttle = 50
                    }
                }
            }
            //Motor speed is an integer between 0 and 100
            //Slider value is a float between 0 and 1
            Slider(
                modifier = Modifier
                    .graphicsLayer { //Ensures the whole view actually moves
                        //https://stackoverflow.com/questions/71123198/create-vertical-sliders-in-jetpack-compose
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxHeight,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(-placeable.width, 0)
                        }
                    },
                value = throttle.toFloat()/100 ,
                onValueChange = {onThrottleChange(round(it*100).toInt()) ;throttle = round(it*100).toInt()},
                colors = SliderDefaults.colors(
                    thumbColor = Color.Red,
                    activeTrackColor = Color.Red,
                    inactiveTrackColor = Color.Gray
                ),
                interactionSource = interactionSource,
                thumb = {
                    Box(
                        modifier = Modifier
                            .width(40.dp) //Slider is rotated and so this will actually be wider than it is tall
                            .height(100.dp)
                            .background(color = Color.Red)
                    )
                }
            )
        }
    }

    @Composable
    private fun AutoModeButton(){
        Button(
            onClick = {
                println("Clicked")
                println(autoModeCharacteristic==null)
                if(null != autoModeCharacteristic){
                    autoModeCharacteristic.value = byteArrayOf(0xaa.toByte(),1)
                    bluetoothService?.writeCharacteristic(autoModeCharacteristic)
                    println("Written")
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier.padding(10.dp,5.dp)
        ) {
            Text(text="Auto Mode", fontSize = 20.sp,color = Color.White,
                modifier = Modifier.padding(5.dp))
        }
    }



}