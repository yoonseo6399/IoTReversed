package io.github.yoonseo6399

import com.juul.kable.Advertisement
import com.juul.kable.Filter
import com.juul.kable.Identifier
import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.characteristicOf
import io.github.yoonseo6399.communication.Command
import io.github.yoonseo6399.communication.DeviceConnection
import io.github.yoonseo6399.communication.DeviceStatus
import io.github.yoonseo6399.communication.Packet
import io.github.yoonseo6399.communication.RX_CHAR_UUID
import io.github.yoonseo6399.communication.RX_SERVICE_UUID
import io.github.yoonseo6399.communication.TX_CHAR_UUID
import io.github.yoonseo6399.communication.registrationScope
import io.github.yoonseo6399.communication.uartRxParser
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
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

    suspend fun setState(state: Boolean){
        val result = connection.requestInfo(Packet.create(Command.Lamp.Control,number.toByte(),state.toByte()))
            .fetch(Command.Lamp.Control, ack = false)
            .execute()
        checkNotNull(result) { "Lamp $number did not acknowledge the requested state." }
        _stateFlow.value = state
    }
    suspend fun flipState(){
        setState(!isOn.value)
    }

    internal fun updateState(state: Boolean) {
        _stateFlow.value = state
    }
}
class Outlet(connection: DeviceConnection, override val number: Int, state: Boolean) : IoTModule(connection){
    private val _stateFlow = MutableStateFlow(state)
    val powerFlowState = _stateFlow.asStateFlow()

    suspend fun setState(state: Boolean){
        val packet = if(state) Packet.create(Command.Conc.Control,number.toByte())
            else Packet.create(Command.Conc.Control,number.toByte(),0)
        val result = connection.requestInfo(packet)
            .fetch(Command.Conc.Control, ack = false)
            .execute()
        checkNotNull(result) { "Outlet $number did not acknowledge the requested state." }
        _stateFlow.value = state
    }
    suspend fun flipState(){
        setState(!powerFlowState.value)
    }

    internal fun updateState(state: Boolean) {
        _stateFlow.value = state
    }
}
/**IoT Switch device controller**/
class IoTSwitch(
    val identifier : Identifier,
    private val connection: DeviceConnection,
    val lamp : Set<Lamp>,
    val outlet : Set<Outlet>
) {

    companion object {
        @OptIn(ExperimentalStdlibApi::class, ExperimentalUuidApi::class)
        suspend fun connect(identifier: Identifier, peripheralProvider: (Identifier) -> Peripheral) : IoTSwitch?{
            println("connecting...")
            val peripheral = peripheralProvider.invoke(identifier)
            var connection : DeviceConnection? = null
            try {
                peripheral.connect()
                peripheral.scope.launch {
                    val rxcharacteristic = characteristicOf(RX_SERVICE_UUID,RX_CHAR_UUID)
                    val txCharacteristic = characteristicOf(RX_SERVICE_UUID,TX_CHAR_UUID)
                    val observation = peripheral.observe(txCharacteristic)
                    val packet = observation.mapNotNull { uartRxParser(it) }
                    connection = DeviceConnection(peripheral,rxcharacteristic,txCharacteristic,packet)
                }.join()
            } catch (e : NotConnectedException){
                println("device cannot be found")
                return null
            }
            println("connected!")
            val dstat = connection!!.requestAllStatus() ?: return null
            println(dstat)
            val lamps = dstat.lampStatus.mapIndexed { i,e ->
                Lamp(connection,i+1,e)
            }
            val outlets = dstat.concStatus.mapIndexed { i,e ->
                Outlet(connection,i+1,e)
            }
            return IoTSwitch(identifier,connection, lamps.toSet(), outlets.toSet())
        }
        fun findNewDevice() : Deferred<Advertisement?> {
            return registrationScope.async {
                try {
                    withTimeout((50).seconds) {  Scanner {
                        filters {
                            match {
                                name = Filter.Name.Prefix("Clio_UART [Clio_UART.]")
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

    suspend fun refreshStatus(): DeviceStatus {
        val status = checkNotNull(connection.requestAllStatus()) { "Unable to retrieve switch status." }
        lamp.forEach { module ->
            status.lampStatus.getOrNull(module.number - 1)?.let(module::updateState)
        }
        outlet.forEach { module ->
            status.concStatus.getOrNull(module.number - 1)?.let(module::updateState)
        }
        return status
    }

    suspend fun disconnect() = connection.peripheral.disconnect()
}
