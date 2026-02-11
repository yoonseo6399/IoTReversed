package io.github.yoonseo6399

import com.juul.kable.Identifier
import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import com.juul.kable.characteristicOf
import com.juul.kable.toIdentifier
import io.github.yoonseo6399.communication.DeviceConnection
import io.github.yoonseo6399.communication.DeviceStatus
import io.github.yoonseo6399.communication.RX_CHAR_UUID
import io.github.yoonseo6399.communication.RX_SERVICE_UUID
import io.github.yoonseo6399.communication.TX_CHAR_UUID
import io.github.yoonseo6399.communication.uartRxParser
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

abstract class IoTModule(val connection: DeviceConnection){
    open val number = 0
}
class Lamp(connection: DeviceConnection,override val number: Int, var isOn: Boolean) : IoTModule(connection){

}
class Outlet(val number: Int,var state: Boolean){

}
class IoTSwitch(
    val uuid : Identifier,
    private val connection: DeviceConnection,
    val lamp : List<Lamp>,
    val outlet : List<Outlet>
) {

    companion object {
        @OptIn(ExperimentalStdlibApi::class, ExperimentalUuidApi::class)
        suspend fun connect(uuid : Identifier) : IoTSwitch?{
            val scope = CoroutineScope(Job() + CoroutineName("DeviceConnection : $uuid") + Dispatchers.IO)
            println("connecting...")
            val peripheral = Peripheral(uuid)
            var connection : DeviceConnection? = null
            try {
                peripheral.connect()
                peripheral.scope.launch {
                    val rxcharacteristic = characteristicOf(RX_SERVICE_UUID,RX_CHAR_UUID)
                    val txCharacteristic = characteristicOf(RX_SERVICE_UUID,TX_CHAR_UUID)
                    val observation = peripheral.observe(txCharacteristic)
                    val packet = observation.mapNotNull { uartRxParser(it) } //sus.. flow check needed
                    connection = DeviceConnection(peripheral,rxcharacteristic,txCharacteristic,packet)
                }.join()
            } catch (e : NotConnectedException){
                println("device cannot be found")
                return null
            }
            println("connected!")
            val dstat = connection!!.requestAllStatus() ?: return null
            val lamps = dstat.lampStatus.mapIndexed { i,e ->
                Lamp(i+1,e)
            }
            val outlets = dstat.concStatus.mapIndexed { i,e ->
                Outlet(i+1,e)
            }
            return IoTSwitch(uuid,connection,lamps,outlets)
        }
    }

}