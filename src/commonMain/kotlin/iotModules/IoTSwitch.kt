package io.github.yoonseo6399.iotModules

import com.juul.kable.Advertisement
import com.juul.kable.Filter
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import io.github.yoonseo6399.communication.DeviceConnection
import io.github.yoonseo6399.communication.FetchException
import io.github.yoonseo6399.communication.Packet
import io.github.yoonseo6399.communication.registrationScope
import iotModules.ConnectionEngine
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.math.log
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

abstract class IoTModule(val connection: DeviceConnection){
    open val number = 0
}


sealed class IoTSwitchState(val context : IoTSwitch) {
    open fun sendPacket(packet: Packet){

    }
    abstract fun onEnter()
    abstract fun onExit()
    class Disconnected(context: IoTSwitch,reason: String) : IoTSwitchState(context){
        override fun sendPacket(packet: Packet) {

        }

        override fun onEnter() {
            context.connectionEngine
        }
    }
}
class IoTSwitch(val connectionEngine: ConnectionEngine,val name: String = "unnamed"){
    var logger : IoTLogger? = null
    fun setLogger(logger : IoTLogger){
        if(this.logger != null) {
            logger.warn("logger already set!")
            return
        }
        logger.init("[IoT$name] ")
        this.logger = logger
    }
    private val _state = MutableStateFlow<IoTSwitchState>(IoTSwitchState.Disconnected(this,"created"))
    val state = _state.asStateFlow()
    internal fun setState(newState: IoTSwitchState){
        _state.value.onExit()
        _state.value = newState
        newState.onEnter()
    }
}
interface IoTLogger {
    fun init(prefix : String)
    fun log(str : String)
    fun warn(str : String)
    fun rcvdPacket(str : String)
    fun err(str: String)
}