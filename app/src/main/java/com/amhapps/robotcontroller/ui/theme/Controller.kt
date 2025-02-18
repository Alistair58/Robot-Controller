package com.amhapps.robotcontroller.ui.theme

import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.amhapps.robotcontroller.BleService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.round

//
class Controller(private val bluetoothService: BleService?,private val motorCharacteristic: BluetoothGattCharacteristic?) {
    private var leftThrottle = 50
    private var rightThrottle = 50
    @Composable
    fun RobotControls(){
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ){
                MotorControlSlider({leftThrottle = it })
                MotorControlSlider({rightThrottle = it })
                LaunchedEffect(motorCharacteristic) {
                    while(motorCharacteristic!=null){
                        sendControls()
                        delay(50)
                    }
                }
            }
        }
    }

    private fun sendControls(){
        if(null == motorCharacteristic){
            println("Null")
            return
        }
        println("Sent L:$leftThrottle R:$rightThrottle")
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

}