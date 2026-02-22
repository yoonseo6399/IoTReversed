@file:OptIn(ExperimentalUuidApi::class)
package io.github.yoonseo6399.iotModules

import com.juul.kable.characteristicOf
import io.github.yoonseo6399.communication.Command
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

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
            context.clearModules()
        }
    }
    class Error(context: IoTSwitch, val exception: Exception) : IoTSwitchState(context){
        override suspend fun onEnter() {
            context.logger?.err("Error: ${exception.message}")
            context.connectionEngine.stop()
        }
        override suspend fun onExit() {
            context.clearModules()
        }
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
        @OptIn(ExperimentalUuidApi::class)
        override suspend fun onEnter() {
            context.logger?.log("Initializing...")
            val rx = characteristicOf(RX_SERVICE_UUID, RX_CHAR_UUID)
            val tx = characteristicOf(RX_SERVICE_UUID, TX_CHAR_UUID)
            context.connectionEngine.startQueueProcessing(rx)
            context.connectionEngine.startObserving(tx)
            delay(200) // Wait for observation to be ready

            var failure: FetchResult.Failure? = null
            context.connectionEngine.fetchDefaultInfo().onSuccess {
                context.initializeModules(this)
            }.onFailure {
                failure = this
            }

            if(failure != null){
                context.setState(Error(context, failure!!.exception ?: Exception("Unknown initialization error")))
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
    val modules = mutableSetOf<IoTModule>()

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
    val state: StateFlow<IoTSwitchState> = _state.asStateFlow()

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

    internal fun initializeModules(fetchData : FetchData) {
        modules.clear()
        val lampStates = fetchData[Command.Lamp.State].parse() as? List<Boolean>
        lampStates?.forEachIndexed { index, isOn ->
            modules += Lamp(this, index, isOn)
        }
        val concStates = fetchData[Command.Conc.State].parse() as? List<Boolean>
        concStates?.forEachIndexed { index, isOn ->
            modules += Outlet(this, index, isOn)
        }
        logger?.log("Initialized ${modules.size} modules.")
    }

    internal fun clearModules() {
        modules.clear()
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: IoTModule> getModule(number: Int): T? {
        if (state.value !is IoTSwitchState.Connected) {
            logger?.warn("Cannot control modules while not connected.")
            return null
        }
        return modules.firstOrNull { it.number == number && it is T } as? T
    }

    internal suspend fun dispatchPacket(packet: Packet) {
        // TODO: Implement more sophisticated dispatching logic
        var patched = false
        for(module in modules){
            val success = module.updateState(packet)
            if(success) patched = true
        }
        if(!patched) logger?.warn("packet is not received by any of modules")
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

//--- Module Definitions ---

abstract class IoTModule(
    protected val context: IoTSwitch,
    val number: Int
) {
    abstract suspend fun updateState(packet : Packet) : Boolean
}

