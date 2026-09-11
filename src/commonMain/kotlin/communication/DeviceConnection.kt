package io.github.yoonseo6399.communication

import com.juul.kable.Characteristic
import com.juul.kable.Identifier
import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.characteristicOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Discovering : ConnectionState()
    object RxInitializing : ConnectionState()
    object TxInitializing : ConnectionState()
    object Ready : ConnectionState()
}
class DeviceConnection (identifier : Identifier, peripheralProvider: (Identifier) -> Peripheral){
    lateinit var packets : Flow<Packet>
    private val peripheral = peripheralProvider.invoke(identifier)
    val packetQueue = Channel<Packet>(Channel.Factory.UNLIMITED)
    private val _state : MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
    val state = _state.asStateFlow()
    lateinit var txCharacteristic : Characteristic
    lateinit var rxCharacteristic : Characteristic
    private var observingJob : Job? = null
    val scope: CoroutineScope = peripheral.scope


    suspend fun establish() : Boolean{
        try {
            if (peripheral.state.value !is State.Connected) {
                _state.value = ConnectionState.Connecting
                peripheral.connect()
            }
            peripheral.state.filter { it is State.Connected }.first() //wait for connected
            discoverCharacteristics()
            enableTxQueue()
            enablePacketReceive()
            delay(300) //stablizer
            startObserveState()
            _state.value = ConnectionState.Ready
        } catch (e : NotConnectedException){
            _state.value = ConnectionState.Disconnected
            println("device cannot be found : ${e.message}")
            return false
        }
        return true
    }

    @OptIn(ExperimentalUuidApi::class)
    fun discoverCharacteristics(){
        rxCharacteristic = characteristicOf(RX_SERVICE_UUID, RX_CHAR_UUID)
        txCharacteristic = characteristicOf(RX_SERVICE_UUID, TX_CHAR_UUID)
    }
    fun enableTxQueue(){
        peripheral.scope.launch {
            for (packet in packetQueue) { //suspended loop
                try {
                    peripheral.write(rxCharacteristic, packet.serialize())
                    println("packet sent!, ${packet}")
                    delay(250) // ⭐ 원본 CommThread의 sleep
                } catch (e: Exception) {
                    println("TX failed: ${e.message}")
                }
            }

        }
    }
    fun enablePacketReceive(){
        packets = peripheral.observe(txCharacteristic)
            .onStart { delay(200) }
            .mapNotNull { uartRxParser(it) }
            .onEach {
                if(it.cmd.ack) ack(it)
                println("rcvd : ${it.cmd.ack}#${it}")
            }
            .shareIn(peripheral.scope, SharingStarted.Eagerly, replay = 20)
    }
    fun startObserveState(){
        if(observingJob != null && observingJob!!.isActive) return
        observingJob = peripheral.scope.launch {
            peripheral.state.filter { it is State.Disconnected }.collect { _state.value = ConnectionState.Disconnected }
        }
    }

    suspend fun disconnect() {
        peripheral.disconnect()
    }
    fun close() {
        peripheral.close()
    }

    /**
        sendPacket(Packet.create(Command.Power.Control,1))
        //I DON'T KNOW WHY BUY REQ-POWER_CONTROL's response is
        val concPowerValue = waitForPacket(Command.Power.State).payload.let { parsePowerValue(it) }.also { println(it) }

        return null//return DeviceStatus(lampCount,lampStatus) //46107 packet is sus.. why send ctrl power?
    **/
    fun requestInfo(packet: Packet) = PacketFetchBuilder(this, packet)


    /**
     * @throws FetchException if fetching fails**/
    suspend fun requestAllStatus(): DeviceStatus {
        // 사용 예시
        val result = requestInfo(Packet.create(Command.Device.Status, 1))// this fails without any error
            .fetch(Command.Device.Status)
            .fetch(Command.Lamp.State)
            .fetch(Command.Conc.State)
            .fetch(Command.Conc.PowerState)
            .fetch(Command.Conc.CutState)
            .retry(5.seconds, 2)
            .setTimeout(8.seconds)
            .execute()


        return DeviceStatus(
            lampStatus = result[Command.Lamp.State]!!.parse() as List<Boolean>,
            concStatus = result[Command.Conc.State]!!.parse() as List<Boolean>,
            concPowerUsage = result[Command.Conc.PowerState]!!.parse() as List<Double>,
            concCutStatus = result[Command.Conc.CutState]!!.payload
        )
    }
    /* ===================== 송신 API ===================== */
    @OptIn(ExperimentalStdlibApi::class)
    /** 외부에서 호출하는 송신 (직접 write 금지) */
    suspend fun sendPacket(packet: Packet) {
        packetQueue.send(packet)
    }

    suspend fun ack(packet: Packet){
        peripheral.write(rxCharacteristic, Packet.create(packet.cmd,1).serialize())
    }
}