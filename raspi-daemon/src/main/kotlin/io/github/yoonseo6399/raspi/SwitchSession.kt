package io.github.yoonseo6399.raspi

import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import io.github.yoonseo6399.communication.ConsumerPowerReading
import io.github.yoonseo6399.communication.readConsumerPower
import io.github.yoonseo6399.communication.ConnectionState
import io.github.yoonseo6399.iotModules.IoTSwitch
import io.github.yoonseo6399.iotModules.IoTSwitchState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

interface SwitchSession {
    val ready: Boolean
    val failure: StateFlow<Throwable?>
    val modules: List<ModuleState>
    suspend fun readPower(): ConsumerPowerReading = throw DeviceApiException(501, "Power diagnostic is unavailable")
    suspend fun connect()
    suspend fun refresh()
    suspend fun set(type: ModuleType, number: Int, on: Boolean): Boolean
    suspend fun disconnect()
    fun close()
}

class KableSwitchSession(private val device: IoTSwitch) : SwitchSession {
    override val ready: Boolean
        get() = device.connection.state.value is ConnectionState.Ready && failure.value == null && device.connection.scope.isActive
    override val failure = device.connection.failure
    override val modules: List<ModuleState>
        get() = device.lamps.map { ModuleState("lamp", it.number, it.isOn.value) } +
            device.outlets.map { ModuleState("outlet", it.number, it.powerFlowState.value) }

    override suspend fun readPower(): ConsumerPowerReading = device.connection.readConsumerPower()

    /** Preserves the transport failure when initialization cannot complete. */
    override suspend fun connect() {
        if (!device.connect()) {
            val state = device.state.value
            throw (state as? IoTSwitchState.Error)?.throwable
                ?: failure.value ?: NotConnectedException("Device initialization disconnected")
        }
    }

    /** Refreshes all modules using one ACK-driven exchange. */
    override suspend fun refresh() { device.refreshStatus() }

    /** Executes a single explicit command without replaying a failed write. */
    override suspend fun set(type: ModuleType, number: Int, on: Boolean): Boolean = when (type) {
        ModuleType.LAMP -> device.lamps.firstOrNull { it.number == number }?.setState(on) == true
        ModuleType.OUTLET -> device.outlets.firstOrNull { it.number == number }?.setState(on) == true
    }

    /** Disconnects while the device receiver can finish ACKs. */
    override suspend fun disconnect() = device.disconnect()

    /** Releases native callbacks owned by this session. */
    override fun close() = device.close()
}

/** Resolves the configured address to a new native peripheral; caller owns the adapter lock. */
suspend fun discoverSwitchSession(configuration: SwitchConfiguration, discovery: BluetoothDiscovery): SwitchSession {
    val expected = bluetoothMac(configuration.bluetoothIdentifier)
    val advertisement = discovery.find {
        if (expected != null) bluetoothMac(it.identifier.toString()) == expected
        else it.identifier.toString() == configuration.bluetoothIdentifier
    }
    println("BLE ${configuration.id}: matched ${bluetoothMac(advertisement.identifier.toString())}")
    return KableSwitchSession(IoTSwitch(advertisement.identifier) { Peripheral(advertisement) })
}
