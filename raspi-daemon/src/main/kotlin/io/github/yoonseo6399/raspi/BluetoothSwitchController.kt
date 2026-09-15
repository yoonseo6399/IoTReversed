package io.github.yoonseo6399.raspi

import io.github.yoonseo6399.communication.FetchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
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
import java.time.Instant

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
    private val mqtt: MqttPublisher,
    private val scope: CoroutineScope,
    private val discovery: BluetoothDiscovery,
    private val createSession: suspend () -> SwitchSession = { discoverSwitchSession(configuration, discovery) }
) {
    private val stateMutex = Mutex()
    private var switch: SwitchSession? = null
    private var monitor: Job? = null
    private var panicWatcher: Job? = null
    @Volatile private var panicked = false
    @Volatile private var halted = false
    private var consecutiveFailures = 0
    @Volatile private var currentSnapshot = DeviceSnapshot(configuration.id, configuration.displayName)

    /** Returns diagnostic and last-observed state without initiating BLE traffic. */
    fun snapshot(): DeviceSnapshot = currentSnapshot

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
    suspend fun setState(moduleType: ModuleType, number: Int, state: Boolean): StateResponse {
        require(number > 0) { "Module number must be positive." }
        return stateMutex.withLock {
            try {
                requireRunning()
                val connected = ensureConnected()
                currentSnapshot.module(moduleType, number)
                val success = connected.set(moduleType, number, state)
                if (success != true) throw DeviceApiException(504, "Control was not acknowledged")
                currentSnapshot = currentSnapshot.copy(modules = currentSnapshot.modules.map {
                    if (it.type == moduleType.topicSegment && it.number == number) it.copy(on = state) else it
                })
                publishModuleState(moduleType, number, state)
                currentSnapshot.module(moduleType, number, acknowledged = true)
            } catch (error: Throwable) {
                handleFailure(error)
                throw error
            }
        }
    }

    /** Explicit diagnostic shares the controller lock, panic guard and connection cleanup. */
    suspend fun readPower(): PowerResponse = stateMutex.withLock {
        try {
            requireRunning()
            val reading = ensureConnected().readPower()
            PowerResponse(configuration.id, reading.watts,
                reading.rawPayload.joinToString("") { "%02x".format(it.toInt() and 255) },
                Instant.now().toString())
        } catch (error: Throwable) {
            handleFailure(error)
            throw error
        }
    }

    /** Reads a fresh full BLE status sequence without changing any output. */
    suspend fun readStatus() {
        stateMutex.withLock {
            try {
                requireRunning()
                val previous = switch
                val connected = ensureConnected()
                if (previous === connected) connected.refresh()
                publishStates(connected)
                consecutiveFailures = 0
            } catch (error: Throwable) {
                handleFailure(error)
                throw error
            }
        }
    }

    /** Recovers ordinary failures with capped backoff; only panic or shutdown stops monitoring. */
    private suspend fun monitorDevice() {
        val interval = (configuration.pollIntervalSeconds ?: defaultPollIntervalSeconds).seconds
        while (currentCoroutineContext().isActive && !halted) {
            try {
                readStatus()
            } catch (error: TimeoutCancellationException) {
                if (!currentCoroutineContext().isActive) throw error
            } catch (error: CancellationException) {
                if (!currentCoroutineContext().isActive) throw error
            } catch (_: Exception) {
            }
            if (!halted) delay(if (consecutiveFailures == 0) interval else
                (interval * (1 shl consecutiveFailures)).coerceAtMost(60.seconds))
        }
    }

    /** Serializes scan/connect and retains failed candidates so every native session is closed. */
    private suspend fun ensureConnected(): SwitchSession {
        switch?.let {
            if (it.ready) return it
            System.err.println("BLE ${configuration.id}: replacing unavailable session; failure=${it.failure.value?.javaClass?.simpleName}")
            releaseConnection()
            publishAvailability("offline")
            requireRunning()
        }
        return discovery.exclusive {
            val connected = createSession()
            switch = connected
            panicWatcher = scope.launch {
                val failure = connected.failure.filterNotNull().first()
                if (failure is FetchException.DuplicationOverflow) {
                    markPanicked()
                    scope.launch {
                        stateMutex.withLock { if (switch === connected) releaseConnection() }
                    }
                } else {
                    currentSnapshot = currentSnapshot.copy(lastError = "${failure::class.simpleName}: ${failure.message}")
                    publishAvailability("offline")
                    System.err.println("BLE ${configuration.id}: session unavailable; reconnect on next request or poll")
                }
            }
            withTimeout(25.seconds) {
                connected.connect()
            }
            requireRunning()
            mqtt.publish(
                "$topicRoot/${configuration.id}/discovery",
                hubJson.encodeToString(DeviceDescription(
                    configuration.id, configuration.displayName,
                    connected.modules.map { ModuleDescription(it.type, it.number) }
                )), retained = true
            )
            publishStates(connected)
            publishAvailability("online")
            connected
        }
    }

    /** Publishes authoritative lamp and outlet states from the completed BLE response. */
    private suspend fun publishStates(connected: SwitchSession) {
        currentSnapshot = currentSnapshot.copy(
            modules = connected.modules,
            observedAt = Instant.now().toString(), lastError = null
        )
        connected.modules.forEach { publishModuleState(ModuleType.fromTopicSegment(it.type)!!, it.number, it.on) }
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
        currentSnapshot = currentSnapshot.copy(availability = "panicked", halted = true, lastError = "Repeated protocol packets")
        publishAvailability("panicked")
        System.err.println("BLE ${configuration.id}: PROTOCOL_HALTED; availability=panicked")
    }

    /** Cleans up the failing session under the caller's lock without disabling recovery for ordinary errors. */
    private suspend fun handleFailure(error: Throwable) {
        if (error is CancellationException && !currentCoroutineContext().isActive) throw error
        if (error is DeviceApiException && error.status != 504) return
        if (error is FetchException.DuplicationOverflow) markPanicked()
        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(3)
        currentSnapshot = currentSnapshot.copy(lastError = "${error::class.simpleName}: ${error.message}", halted = halted)
        System.err.println("BLE controller ${configuration.id}: ${error::class.simpleName}: ${error.message}; before cleanup; halted=$halted")
        releaseConnection()
        publishAvailability("offline")
        System.err.println("BLE ${configuration.id}: cleanup complete; reconnect=${!halted}; halted=$halted")
    }

    /** Disposes failed and completed sessions even while a coroutine is being cancelled. */
    private suspend fun releaseConnection() = withContext(NonCancellable) {
        if (switch?.failure?.value is FetchException.DuplicationOverflow && !panicked) markPanicked()
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
        currentSnapshot = currentSnapshot.copy(availability = if (panicked) "panicked" else value, halted = halted)
        try {
            mqtt.publish("$topicRoot/${configuration.id}/availability",
                if (panicked) "panicked" else value, retained = true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            System.err.println("MQTT availability ${configuration.id}: ${error.message}")
        }
    }

    /** Keeps HTTP and MQTT callers from reconnecting a panicked device. */
    private fun requireRunning() {
        if (halted) throw DeviceApiException(if (panicked) 409 else 503,
            currentSnapshot.lastError ?: "Device is halted; inspect before restarting")
    }
}
