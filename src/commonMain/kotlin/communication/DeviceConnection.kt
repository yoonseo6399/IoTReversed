package io.github.yoonseo6399.communication

import com.juul.kable.Identifier
import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.characteristicOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

interface PacketTransport {
    val packets: SharedFlow<Packet>
    val failure: StateFlow<Throwable?>
    val requestMutex: Mutex
    suspend fun sendPacket(packet: Packet)
}

@OptIn(ExperimentalUuidApi::class, ExperimentalStdlibApi::class)
class DeviceConnection(identifier: Identifier, peripheralProvider: (Identifier) -> Peripheral) : PacketTransport {
    private val peripheral = peripheralProvider(identifier)
    private val received = MutableSharedFlow<Packet>(extraBufferCapacity = 64)
    private val _failure = MutableStateFlow<Throwable?>(null)
    private val writeMutex = Mutex()
    private val diagnostics = PacketDiagnostics()
    private val rxCharacteristic = characteristicOf(RX_SERVICE_UUID, RX_CHAR_UUID)
    private val txCharacteristic = characteristicOf(RX_SERVICE_UUID, TX_CHAR_UUID)
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private var receiver: Job? = null
    private var observingJob: Job? = null
    private val label = identifier.toString()

    override val requestMutex = Mutex()
    override val packets = received.asSharedFlow()
    override val failure = _failure.asStateFlow()
    val state = _state.asStateFlow()
    val scope: CoroutineScope = peripheral.scope

    /** Connects and waits for notification subscription before sending the first request. */
    suspend fun establish(): Boolean {
        try {
            _state.value = ConnectionState.Connecting
            println("BLE [$label] connecting")
            peripheral.connect()
            _state.value = ConnectionState.RxInitializing
            val subscribed = CompletableDeferred<Unit>()
            receiver = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    peripheral.observe(txCharacteristic) { subscribed.complete(Unit) }.collect { bytes ->
                        val packet = uartRxParser(bytes)
                        if (packet == null) {
                            println("BLE [$label] RX malformed=${bytes.toHexString()}")
                            return@collect
                        }
                        val repeated = diagnostics.receive(packet)
                        if (packet.cmd.ack) ack(packet)
                        println("BLE [$label] RX cmd=${packet.cmd.byte} payload=${packet.payload.toHexString()} repeat=${diagnostics.repetitions} ack=${packet.cmd.ack}")
                        if (repeated && _failure.value == null) {
                            _failure.value = FetchException.DuplicationOverflow(packet.cmd)
                            println("BLE [$label] PROTOCOL_HALTED repeated packet; no further requests")
                        }
                        received.emit(packet)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    _failure.value = error
                    subscribed.completeExceptionally(error)
                }
            }
            withTimeout(10.seconds) { subscribed.await() }
            observingJob = scope.launch {
                peripheral.state.collect { current ->
                    println("BLE [$label] link=$current")
                    if (current is State.Disconnected) {
                        _state.value = ConnectionState.Disconnected
                        if (_failure.value == null) _failure.value = NotConnectedException("Device disconnected")
                    }
                }
            }
            _state.value = ConnectionState.Ready
            return true
        } catch (error: NotConnectedException) {
            _state.value = ConnectionState.Disconnected
            println("BLE [$label] connect failed: ${error.message}")
            return false
        }
    }

    /** Disconnects while ACK handling remains active, then releases the receiver and state observer. */
    suspend fun disconnect() = withContext(NonCancellable) {
        try {
            withTimeout(5.seconds) { peripheral.disconnect() }
        } finally {
            receiver?.cancelAndJoin()
            observingJob?.cancelAndJoin()
            _state.value = ConnectionState.Disconnected
        }
    }

    /** Releases native callbacks and all module collectors owned by this peripheral. */
    fun close() = peripheral.close()

    /** Builds one serialized protocol transaction; ACKs remain owned by the receiver. */
    fun requestInfo(packet: Packet) = PacketFetchBuilder(this, packet)

    /** Reads the complete ACK-driven sequence without retrying an incomplete status request. */
    suspend fun requestAllStatus(): DeviceStatus {
        val result = requestInfo(Packet.create(Command.Device.Status, 1))
            .fetch(Command.Device.Status)
            .fetch(Command.Lamp.State)
            .fetch(Command.Conc.State)
            .fetch(Command.Conc.PowerState)
            .fetch(Command.Conc.CutState)
            .retry(5.seconds, 1)
            .setTimeout(8.seconds)
            .execute()
        return DeviceStatus(
            lampStatus = result.getValue(Command.Lamp.State).parse() as List<Boolean>,
            concStatus = result.getValue(Command.Conc.State).parse() as List<Boolean>,
            concPowerUsage = result.getValue(Command.Conc.PowerState).parse() as List<Double>,
            concCutStatus = result.getValue(Command.Conc.CutState).payload
        )
    }

    /** Writes a request synchronously and propagates failures instead of silently losing queued work. */
    override suspend fun sendPacket(packet: Packet) {
        failure.value?.let { throw it }
        writeMutex.withLock {
            failure.value?.let { throw it }
            peripheral.write(rxCharacteristic, packet.serialize())
        }
        println("BLE [$label] TX cmd=${packet.cmd.byte} payload=${packet.payload.toHexString()}")
    }

    /** ACKs every required notification, including duplicates, before delivering it to callers. */
    suspend fun ack(packet: Packet) {
        writeMutex.withLock {
            peripheral.write(rxCharacteristic, Packet.create(packet.cmd, 1).serialize())
        }
    }
}
