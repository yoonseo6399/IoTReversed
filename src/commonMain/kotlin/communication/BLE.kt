@file:OptIn(ExperimentalUuidApi::class)

package io.github.yoonseo6399.communication

import com.juul.kable.Characteristic
import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import com.juul.kable.characteristicOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val registrationScope = CoroutineScope(Job() + CoroutineName("registrationScope") + Dispatchers.IO)

val RX_SERVICE_UUID = Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
val RX_CHAR_UUID = Uuid.parse("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
val TX_CHAR_UUID = Uuid.parse("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
val UUID_HEART_RATE_MEASUREMENT = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
data class DeviceStatus(
    val lampStatus : List<Boolean>,
    val concStatus : List<Boolean>,
    val concPowerUsage : List<Double>,
    val concCutStatus : ByteArray
) {
    override fun toString(): String {
        return "lamp : $lampStatus\n" +
                "conc : $concStatus\n" +
                "power : $concPowerUsage"

    }
}

class DeviceConnection private constructor(val peripheral: Peripheral,val rxCharacteristic: Characteristic,val txCharacteristic: Characteristic){
    lateinit var packets : Flow<Packet>
        private set

    companion object{
        suspend fun fromOrNull(peripheral: Peripheral) : DeviceConnection?{
            peripheral.disconnect()
            delay(500)
            var connection : DeviceConnection? = null
            try {
                peripheral.connect()
                peripheral.scope.launch {
                    val rxcharacteristic = characteristicOf(RX_SERVICE_UUID,RX_CHAR_UUID)
                    val txCharacteristic = characteristicOf(RX_SERVICE_UUID,TX_CHAR_UUID)
                    val observation = peripheral.observe(txCharacteristic)
                    connection = DeviceConnection(peripheral,rxcharacteristic,txCharacteristic)
                    println("wtf")

                    val packet = observation.mapNotNull { uartRxParser(it) }.onEach { if(it.cmd.ack) connection.ack(it) }.shareIn(peripheral.scope,
                        SharingStarted.Eagerly, replay = 20)
                    connection.packets = packet
                }.join()
                println("wtf")

            } catch (e : NotConnectedException){
                println("device cannot be found : ${e.message}")
                return null
            }
            println("wtff")

            return connection
        }
    }
    /**
        sendPacket(Packet.create(Command.Power.Control,1))
        //I DON'T KNOW WHY BUY REQ-POWER_CONTROL's response is
        val concPowerValue = waitForPacket(Command.Power.State).payload.let { parsePowerValue(it) }.also { println(it) }

        return null//return DeviceStatus(lampCount,lampStatus) //46107 packet is sus.. why send ctrl power?
    **/
    fun requestInfo(packet: Packet) = PacketFetchBuilder(this, packet)

    suspend fun requestAllStatus(): DeviceStatus? {
        // 사용 예시
        println("requesting..")
        val result = requestInfo(Packet.create(Command.Device.Status, 1))// this fails without any error
            .fetch(Command.Device.Status)
            .fetch(Command.Lamp.State)
            .fetch(Command.Conc.State)
            .fetch(Command.Conc.PowerState)
            .fetch(Command.Conc.CutState)
            .retry(1.seconds, 5)
            .setTimeout(8.seconds)
            .execute()
        println("request complete!")
        if (result == null) {
            println("통신 복구 실패: 데이터를 모두 가져오지 못했습니다.")
            return null
        }

        // 결과 가공
        val lampInfo = result[Command.Lamp.State]!!.parse() as List<Boolean>
        val concStatus = result[Command.Conc.State]!!.parse() as List<Boolean>
        val concPowerUsage = result[Command.Conc.PowerState]!!.parse() as List<Double>
        val concCutStatus = result[Command.Conc.CutState]!!.payload

        println(concCutStatus.map { it.toInt() })
        return DeviceStatus(lampInfo,concStatus,concPowerUsage,concCutStatus)
    }
    suspend fun waitForPacket(cmd : Command,ack : Boolean = true) : Packet {
        println("wait for packet : $cmd")
        val packet = packets.first { it.cmd.also { println(it) } == cmd }
        if(ack) ack(packet)
        println("Successfully Rcvd packet : $packet")
        return packet
    }
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun sendPacket(packet: Packet) {
        peripheral.write(rxCharacteristic,packet.serialize())
    }

    suspend fun ack(packet: Packet){
        sendPacket(Packet.create(packet.cmd,1))
    }
}

const val CMD2_REQ_SETTING : Byte = 126



