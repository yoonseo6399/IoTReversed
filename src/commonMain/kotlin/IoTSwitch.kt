package io.github.yoonseo6399

import com.juul.kable.Advertisement
import com.juul.kable.Filter
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import io.github.yoonseo6399.communication.Command
import io.github.yoonseo6399.communication.DeviceConnection
import io.github.yoonseo6399.communication.Packet
import io.github.yoonseo6399.communication.registrationScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

abstract class IoTModule(val connection: DeviceConnection){
    open val number = 0
}
class Lamp(connection: DeviceConnection,override val number: Int, isOn: Boolean) : IoTModule(connection){
    private val _stateFlow = MutableStateFlow(isOn)
    val isOn = _stateFlow.asStateFlow()

    suspend fun setState(state: Boolean) : Boolean{
        //IDK whether should I check the state
        val r = connection.requestInfo(Packet.create(Command.Lamp.Control,number.toByte(),state.toByte()))
            .fetch(Command.Lamp.Control, ack = false)
            .execute()
        if(r==null) return false
        _stateFlow.value = state
        return true
    }
    suspend fun flipState()=
        setState(!isOn.value)

}
class Outlet(connection: DeviceConnection, override val number: Int, state: Boolean) : IoTModule(connection){
    private val _stateFlow = MutableStateFlow(state)
    val powerFlowState = _stateFlow.asStateFlow()

    suspend fun setState(state: Boolean){
        //IDK whether should I check the state
        val packet = if(state) Packet.create(Command.Conc.Control,number.toByte())
            else Packet.create(Command.Conc.Control,number.toByte(),0)
        connection.requestInfo(packet)
            .fetch(Command.Conc.Control, ack = false)
            .execute()
        _stateFlow.value = state
    }
    suspend fun flipState(){
        setState(!powerFlowState.value)
    }
}
/**IoT Switch device controller**/
class IoTSwitch(
    val identifier : Identifier,
    private val connection: DeviceConnection,
    val lamp : Set<Lamp>,
    val outlet : Set<Outlet>
) {
    init {
        Command.allCommands
    }
    companion object {
        @OptIn(ExperimentalStdlibApi::class, ExperimentalUuidApi::class)
        suspend fun connect(identifier: Identifier, peripheralProvider: (Identifier) -> Peripheral) : IoTSwitch?{
            println("connecting...")
            val peripheral = peripheralProvider.invoke(identifier)
            val connection = DeviceConnection.fromOrNull(peripheral) ?: return abort("connection failed")
            println("connected")
            val statData = connection.requestAllStatus() ?: return abort("request failed")
            println(statData)
            val lamps = statData.lampStatus.mapIndexed { i, e ->
                Lamp(connection,i+1,e)
            }
            val outlets = statData.concStatus.mapIndexed { i, e ->
                Outlet(connection,i+1,e)
            }
            println("connection complete!")
            return IoTSwitch(identifier,connection, lamps.toSet(), outlets.toSet())
        }
        fun findNewDevice() : Deferred<Advertisement?> {
            return registrationScope.async {
                try {
                    withTimeout((15).seconds) {  Scanner {
                        filters {
                            match {
                                name = Filter.Name.Prefix("Clio_UART [Clio_UART.]")
                            }
                            match {
                                name = Filter.Name.Exact("Clio_UART.")
                            }
                        }
                    }.advertisements.first() }
                } catch (e : TimeoutCancellationException){
                    null
                }
            }
        }
    }
    fun printUnhandled(){
        connection.peripheral.scope.launch {
            println("printing Unhandled")
            connection.packets.toList().forEach {
                println(it)
            }
        }
    }

    suspend fun disconnect() = connection.peripheral.disconnect()
    fun close() = connection.peripheral.close()
}

/**
 * return null, and log str**/
fun <T> abort(str : String) : T? {
    println(str)
    return null
}