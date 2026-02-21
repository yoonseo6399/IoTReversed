package io.github.yoonseo6399.iotModules

import com.juul.kable.NotConnectedException
import com.juul.kable.characteristicOf
import io.github.yoonseo6399.communication.DeviceConnection
import io.github.yoonseo6399.communication.FetchData
import io.github.yoonseo6399.communication.FetchResult
import io.github.yoonseo6399.communication.Packet
import io.github.yoonseo6399.communication.RX_CHAR_UUID
import io.github.yoonseo6399.communication.RX_SERVICE_UUID
import io.github.yoonseo6399.communication.TX_CHAR_UUID
import iotModules.ConnectionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

abstract class IoTModule(val connection: DeviceConnection){
    open val number = 0
}

sealed class IoTSwitchState(val context : IoTSwitch) {
    /**@throws IllegalStateException on Connection State that cannot send IO operation**/
    open suspend fun sendPacket(packet: Packet){
        throw IllegalStateException("This state does not support IO operations (Behavior not Overridden)")
    }
    abstract suspend fun onEnter()
    abstract suspend fun onExit()

    class Disconnected(context: IoTSwitch, val reason: String) : IoTSwitchState(context){
        override suspend fun sendPacket(packet: Packet) {
            throw IllegalStateException("Disconnected. Reason: $reason")
        }

        override suspend fun onEnter() {
            context.logger?.log("Disconnected. Reason: $reason")
            context.connectionEngine.stop()
        }

        override suspend fun onExit() {
            //TODO Cleanup logic for disconnected state if any
            context.connectionEngine.stop()
        }
    }
    class Error(context: IoTSwitch, val exception: Exception) : IoTSwitchState(context){
        override suspend fun onEnter() {}
        override suspend fun onExit() {}
    }
    class Connecting(context: IoTSwitch) : IoTSwitchState(context) {
        override suspend fun onEnter() {
            context.logger?.log("Connecting...")
            try {
                context.connectionEngine.tryConnectWithTimeout(15.seconds)
                context.setState(Initializing(context))
            } catch (e: TimeoutCancellationException) {
                context.setState(Disconnected(context, "Connection failed: Timeout"))
            } catch (e: Exception) {
                context.setState(Disconnected(context, "Connection failed: ${e.message}"))
            }
        }
        override suspend fun onExit() {}
    }

    class Initializing(context: IoTSwitch) : IoTSwitchState(context){

        //this code actually the weakest part of this project
        //it does not consider any disconnection state
        //idk how to fix it
        override suspend fun onEnter() {
            context.logger?.log("Initializing...")
            var fail : FetchResult.Failure? = null
            context.connectionEngine.fetchDefaultInfo().onSuccess {
                context.initialize(this)
            }.onFailure {
                fail = this
            }
            if(fail != null){
                context.setState(Error(context,fail.exception!!))
                return
            }
            context.setState(Connected(context))
        }
        override suspend fun onExit() {}
    }

    class Connected(context: IoTSwitch) : IoTSwitchState(context) {
        override suspend fun sendPacket(packet: Packet) {
            context.connectionEngine.enQueuePacket(packet)
        }

        override suspend fun onEnter() {
            context.logger?.log("Connection successful.")
            val rx = characteristicOf(RX_SERVICE_UUID, RX_CHAR_UUID)
            val tx = characteristicOf(RX_SERVICE_UUID, TX_CHAR_UUID)
            context.connectionEngine.startQueueProcessing(rx)
            context.connectionEngine.startObserving(tx)
        }

        override suspend fun onExit() {
            context.logger?.log("Connection lost.")
        }
    }
}
class IoTSwitch(
    internal val connectionEngine: ConnectionEngine,
    val name: String = "unnamed",
    private val scope: CoroutineScope
){
    private var stateTransitionJob: Job? = null
    var logger : IoTLogger? = null

    init {
        connectionEngine.register(this)
    }

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

    fun connect() {
        if (stateTransitionJob?.isActive == true || _state.value !is IoTSwitchState.Disconnected) {
            logger?.warn("Cannot connect: Already connected, connecting, or disconnecting.")
            return
        }

        stateTransitionJob = scope.launch {
            setState(IoTSwitchState.Connecting(this@IoTSwitch))
        }
    }

    fun disconnect() {
        if (stateTransitionJob?.isActive == true) {
            stateTransitionJob?.cancel()
        }
        stateTransitionJob = scope.launch {
            setState(IoTSwitchState.Disconnected(this@IoTSwitch, "User action"))
        }
    }

    internal fun initialize(fetchData : FetchData) {
        // TODO: Apply the fetched data to the IoT Switch's properties
        logger?.log("Initialized with data: $fetchData")
    }

    internal suspend fun setState(newState: IoTSwitchState){
        if (_state.value::class == newState::class) return

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