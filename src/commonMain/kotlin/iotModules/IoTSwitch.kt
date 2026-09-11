package io.github.yoonseo6399.iotModules

import com.juul.kable.Advertisement
import com.juul.kable.Filter
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import io.github.yoonseo6399.communication.DeviceConnection
import io.github.yoonseo6399.communication.DeviceStatus
import io.github.yoonseo6399.communication.FetchException
import io.github.yoonseo6399.communication.registrationScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

abstract class IoTModule(val connection: DeviceConnection){
    open val number = 0
}

sealed interface IoTSwitchState {
    object Created : IoTSwitchState
    object Idle : IoTSwitchState
    object Connecting : IoTSwitchState
    object Discovering : IoTSwitchState
    object Initializing : IoTSwitchState
    object Ready : IoTSwitchState
    data class Disconnected(val reason: String? = null) : IoTSwitchState
    data class Error(val throwable: Throwable) : IoTSwitchState
}

/**IoT Switch device controller**/
class IoTSwitch(
    val identifier : Identifier, peripheralProvider: (Identifier) -> Peripheral
) {
    val connection = DeviceConnection(identifier,peripheralProvider)
    private val _state = MutableStateFlow<IoTSwitchState>(IoTSwitchState.Created)
    lateinit var lamps : List<Lamp>
    lateinit var outlets : List<Outlet>

    val state: StateFlow<IoTSwitchState> = _state.asStateFlow()
    companion object {
        fun findNewDevice() : Deferred<Advertisement?> {
            return registrationScope.async {
                try {
                    withTimeout((15).seconds) {  Scanner {
                        filters {
                            match {
                                name = Filter.Name.Prefix("Clio_UART [Clio_UART.]")
                            }
                            match {
                                name = Filter.Name.Prefix("Clio_UART.")
                            }
                        }
                    }.advertisements.first() }
                } catch (e : TimeoutCancellationException){
                    null
                }
            }
        }
    }


    @OptIn(ExperimentalStdlibApi::class, ExperimentalUuidApi::class)
    suspend fun connect() : Boolean{
        if(_state.value !is IoTSwitchState.Disconnected && _state.value !is IoTSwitchState.Created) {
            println("Warning : connect called during #${state.value}")
            return false
        }
        _state.value = IoTSwitchState.Connecting
        if(!connection.establish()) { //establishing connection
            _state.value = IoTSwitchState.Disconnected("connection failed")
            return false
        }


        _state.value = IoTSwitchState.Initializing
        val statData = try {
            connection.requestAllStatus()
        } catch (e : FetchException){
            _state.value = IoTSwitchState.Error(e)
            return false
        }
        println(statData)
        lamps = statData.lampStatus.mapIndexed { i, e ->
            Lamp(connection,i+1,e)
        }
        outlets = statData.concStatus.mapIndexed { i, e ->
            Outlet(connection,i+1,e)
        }

        _state.value = IoTSwitchState.Ready
        return true
    }
    suspend fun disconnect() = connection.disconnect()
    fun close() = connection.close()

    suspend fun refreshStatus(): DeviceStatus {
        val status = connection.requestAllStatus()
        lamps.forEach { module ->
            status.lampStatus.getOrNull(module.number - 1)?.let(module::updateState)
        }
        outlets.forEach { module ->
            status.concStatus.getOrNull(module.number - 1)?.let(module::updateState)
        }
        return status
    }
}

/**
 * return null, and log str**/
fun <T> abort(str : String) : T? {
    println(str)
    return null
}
