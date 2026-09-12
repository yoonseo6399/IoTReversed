package io.github.yoonseo6399.raspi

import com.juul.kable.Peripheral
import io.github.yoonseo6399.communication.FetchException
import io.github.yoonseo6399.iotModules.IoTSwitch
import io.github.yoonseo6399.iotModules.IoTSwitchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.time.Duration.Companion.seconds

enum class ModuleType(val topicSegment: String) {
    LAMP("lamp"),
    OUTLET("outlet");

    companion object {
        /** Resolves a supported module topic segment. */
        fun fromTopicSegment(value: String) = entries.firstOrNull { it.topicSegment == value }
    }
}

@Serializable
data class ModuleDescription(val type: String, val number: Int)

@Serializable
data class DeviceDescription(val id: String, val displayName: String, val modules: List<ModuleDescription>)

class BluetoothSwitchController(
    val configuration: SwitchConfiguration,
    private val topicRoot: String,
    private val defaultPollIntervalSeconds: Long,
    private val mqtt: MqttGateway,
    private val scope: CoroutineScope,
    private val discovery: BluetoothDiscovery
) {
    private val stateMutex = Mutex()
    private var switch: IoTSwitch? = null
    private var monitor: Job? = null
    private var panicWatcher: Job? = null
    @Volatile private var panicked = false
    @Volatile private var halted = false
    private var consecutiveFailures = 0

    /** Starts one status monitor for this device. */
    fun start() {
        check(monitor == null) { "Controller ${configuration.id} has already been started." }
        monitor = scope.launch { monitorDevice() }
    }

    /** Releases the connection and preserves a latched panic availability. */
    suspend fun stop() {
        monitor?.cancelAndJoin()
        monitor = null
        stateMutex.withLock { releaseConnection() }
        publishAvailability("offline")
    }

    /** Executes a serialized control request and publishes only an acknowledged result. */
    suspend fun setState(moduleType: ModuleType, number: Int, state: Boolean) {
        require(number > 0) { "Module number must be positive." }
        try {
            stateMutex.withLock {
                check(!halted) { "Device ${configuration.id} is halted; inspect it before restarting." }
                val connected = ensureConnected()
                val success = when (moduleType) {
                    ModuleType.LAMP -> connected.lamps.firstOrNull { it.number == number }?.setState(state)
                    ModuleType.OUTLET -> connected.outlets.firstOrNull { it.number == number }?.setState(state)
                }
                check(success == true) { "Control was not acknowledged for ${configuration.id}." }
                publishModuleState(moduleType, number, state)
            }
        } catch (error: Throwable) {
            handleFailure(error)
            throw error
        }
    }

    /** Reads a fresh full BLE status sequence without changing any output. */
    suspend fun readStatus() {
        try {
            stateMutex.withLock {
                check(!halted) { "Device ${configuration.id} is halted; inspect it before restarting." }
                val alreadyConnected = switch != null
                val connected = ensureConnected()
                if (alreadyConnected) connected.refreshStatus()
                publishStates(connected)
                consecutiveFailures = 0
            }
        } catch (error: Throwable) {
            handleFailure(error)
            throw error
        }
    }

    /** Polls until cancelled or stopped by a protocol fault or three consecutive failures. */
    private suspend fun monitorDevice() {
        val interval = (configuration.pollIntervalSeconds ?: defaultPollIntervalSeconds).seconds
        while (currentCoroutineContext().isActive && !halted) {
            try {
                readStatus()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
            delay(interval)
        }
    }

    /** Serializes scan/connect and retains failed candidates so every native session is closed. */
    private suspend fun ensureConnected(): IoTSwitch {
        switch?.let { return it }
        return discovery.exclusive {
            val expected = bluetoothMac(configuration.bluetoothIdentifier)
            val advertisement = find {
                if (expected != null) bluetoothMac(it.identifier.toString()) == expected
                else it.identifier.toString() == configuration.bluetoothIdentifier
            }
            println("BLE ${configuration.id}: matched ${bluetoothMac(advertisement.identifier.toString())}")
            val connected = IoTSwitch(advertisement.identifier) { Peripheral(advertisement) }
            switch = connected
            panicWatcher = scope.launch {
                connected.connection.failure.filterIsInstance<FetchException.DuplicationOverflow>().first()
                markPanicked()
            }
            withTimeout(25.seconds) {
                if (!connected.connect()) {
                    val state = connected.state.value
                    if (state is IoTSwitchState.Error) throw state.throwable
                    error("Could not connect to ${configuration.id}.")
                }
            }
            mqtt.publish(
                "$topicRoot/${configuration.id}/discovery",
                hubJson.encodeToString(DeviceDescription(
                    configuration.id, configuration.displayName,
                    connected.lamps.map { ModuleDescription("lamp", it.number) } +
                        connected.outlets.map { ModuleDescription("outlet", it.number) }
                )), retained = true
            )
            publishAvailability("online")
            connected
        }
    }

    /** Publishes authoritative lamp and outlet states from the completed BLE response. */
    private suspend fun publishStates(connected: IoTSwitch) {
        connected.lamps.forEach { publishModuleState(ModuleType.LAMP, it.number, it.isOn.value) }
        connected.outlets.forEach { publishModuleState(ModuleType.OUTLET, it.number, it.powerFlowState.value) }
    }

    /** Publishes one retained Homebridge state. */
    private suspend fun publishModuleState(moduleType: ModuleType, number: Int, state: Boolean) {
        mqtt.publish("$topicRoot/${configuration.id}/${moduleType.topicSegment}/$number/state",
            if (state) "ON" else "OFF", retained = true)
    }

    /** Latches a protocol panic and announces it immediately, including between polling requests. */
    private suspend fun markPanicked() {
        panicked = true
        halted = true
        publishAvailability("panicked")
        System.err.println("BLE ${configuration.id}: PROTOCOL_HALTED; availability=panicked")
    }

    /** Stops incomplete protocol exchanges and bounds retries of ordinary connection failures. */
    private suspend fun handleFailure(error: Throwable) {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        stateMutex.withLock {
            if (error is FetchException.DuplicationOverflow) markPanicked()
            if (error is FetchException && error !is FetchException.Disconnected) halted = true
            consecutiveFailures++
            if (consecutiveFailures >= 3) halted = true
            releaseConnection()
        }
        publishAvailability("offline")
        System.err.println("BLE controller ${configuration.id}: ${error::class.simpleName}: ${error.message}; halted=$halted")
    }

    /** Disposes failed and completed sessions even while a coroutine is being cancelled. */
    private suspend fun releaseConnection() = withContext(NonCancellable) {
        panicWatcher?.cancelAndJoin()
        panicWatcher = null
        val connected = switch
        switch = null
        if (connected != null) {
            try {
                connected.disconnect()
            } catch (error: Exception) {
                System.err.println("BLE ${configuration.id} cleanup: ${error.message}")
            } finally {
                connected.close()
            }
        }
    }

    /** Keeps panicked retained instead of overwriting it with offline during shutdown. */
    private suspend fun publishAvailability(value: String) {
        try {
            mqtt.publish("$topicRoot/${configuration.id}/availability",
                if (panicked) "panicked" else value, retained = true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            System.err.println("MQTT availability ${configuration.id}: ${error.message}")
        }
    }
}
