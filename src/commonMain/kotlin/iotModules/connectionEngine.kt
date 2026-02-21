package iotModules

import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.characteristicOf
import io.github.yoonseo6399.communication.Command
import io.github.yoonseo6399.communication.FetchResult
import io.github.yoonseo6399.communication.Packet
import io.github.yoonseo6399.communication.PacketFetchBuilder
import io.github.yoonseo6399.communication.RX_CHAR_UUID
import io.github.yoonseo6399.communication.RX_SERVICE_UUID
import io.github.yoonseo6399.communication.TX_CHAR_UUID
import io.github.yoonseo6399.communication.uartRxParser
import io.github.yoonseo6399.iotModules.IoTSwitch
import io.github.yoonseo6399.iotModules.IoTSwitchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi


//Engine 이 해야할일. 패킷관리, 전송, 연결 무결성 체크
abstract class ConnectionEngine(val peripheral: Peripheral){
    val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var packetChannel = Channel<Packet>(20)
    var receivedPacketFlow : Flow<Packet>? = null
    lateinit var context : IoTSwitch
    internal fun register(context : IoTSwitch){
        this.context = context
    }
    /**no if statement required, State-Pattern guarantees that no packet is enqueued while disconnected state**/
    open suspend fun enQueuePacket(packet: Packet) {
        packetChannel.send(packet)
    }
    suspend fun fetchDefaultInfo() : FetchResult{
        context.logger?.log("fetching default info...")
        return PacketFetchBuilder(this,Packet.create(Command.Device.Status, 1))// this fails without any error
            .fetch(Command.Device.Status)
            .fetch(Command.Lamp.State)
            .fetch(Command.Conc.State)
            .fetch(Command.Conc.PowerState)
            .fetch(Command.Conc.CutState)
            .retry(5.seconds, 2)
            .setTimeout(8.seconds)
            .execute()
    }
    /**@throws kotlinx.coroutines.TimeoutCancellationException if given timeout is exceeded
     * @throws com.juul.kable.NotConnectedException if kable's Connection fails**/
    abstract suspend fun tryConnectWithTimeout(timeout : Duration) : Boolean
    abstract fun startQueueProcessing(rxCharacteristic : Characteristic)
    abstract fun startObserving(txCharacteristic: Characteristic)
    abstract fun stop()
    abstract fun close()
}

class DefaultConnectionEngine(peripheral: Peripheral) : ConnectionEngine(peripheral){
    override suspend fun tryConnectWithTimeout(timeout: Duration) : Boolean {
        return withTimeout(timeout) {
            peripheral.connect()
            true
        }
    }

    override fun startQueueProcessing(rxCharacteristic : Characteristic) {
        //무결성을 보장하기 위한 state observer
        connectionScope.launch {
            peripheral.state.filter { it is State.Disconnected }.first()
            context.setState(IoTSwitchState.Disconnected(context,"Unexpectedly Disconnected (Kable state)"))
        }
        connectionScope.launch {
            for (packet in packetChannel){
                peripheral.write(rxCharacteristic,packet.serialize())
                delay(250)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun startObserving(txCharacteristic: Characteristic) {
        receivedPacketFlow = peripheral.observe(txCharacteristic)
                                .mapNotNull { uartRxParser(it) }
                                .onEach { context.logger?.rcvdPacket(it.toString()) }
                                .shareIn(peripheral.scope, SharingStarted.Eagerly,10)
    }
    override fun stop() {
        packetChannel.cancel(CancellationException("Disconnected, all unsent packet will be removed"))
        connectionScope.cancel(CancellationException("Disconnected. Cancelling packet queue"))
        packetChannel = Channel<Packet>(20)
    }

    override fun close() {
        packetChannel.cancel(CancellationException("closed"))
        connectionScope.cancel(CancellationException("closed"))
        peripheral.close()
    }
}