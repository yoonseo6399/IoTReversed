@file:OptIn(ExperimentalStdlibApi::class)

package io.github.yoonseo6399.raspi

import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.toIdentifier
import io.github.yoonseo6399.iotModules.IoTSwitch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

enum class ModuleType(val topicSegment: String) {
    LAMP("lamp"),
    OUTLET("outlet");

    companion object {
        fun fromTopicSegment(value: String) = entries.firstOrNull { it.topicSegment == value }
    }
}

class BluetoothSwitchController(
    val configuration: SwitchConfiguration,
    private val topicRoot: String,
    private val defaultPollIntervalSeconds: Long,
    private val mqtt: MqttGateway,
    private val scope: CoroutineScope
) {
    private val stateMutex = Mutex()
    private var switch: IoTSwitch? = null
    private var monitor: Job? = null

    fun start() {
        check(monitor == null) { "Controller ${configuration.id} has already been started." }
        monitor = scope.launch { monitorDevice() }
    }

    suspend fun stop() {
        monitor?.cancelAndJoin()
        monitor = null
        stateMutex.withLock {
            switch?.let { connected -> runCatching { connected.disconnect() } }
            switch = null
        }
        publishAvailability(false)
    }

    suspend fun setState(moduleType: ModuleType, number: Int, state: Boolean) {
        require(number > 0) { "Module number must be positive." }
        try {
            stateMutex.withLock {
                val connected = ensureConnected()
                when (moduleType) {
                    ModuleType.LAMP -> {
                        val lamp = connected.lamps.firstOrNull { it.number == number }
                            ?: error("Lamp $number does not exist on ${configuration.id}.")
                        lamp.setState(state)
                    }
                    ModuleType.OUTLET -> {
                        val outlet = connected.outlets.firstOrNull { it.number == number }
                            ?: error("Outlet $number does not exist on ${configuration.id}.")
                        outlet.setState(state)
                    }
                }
                publishModuleState(moduleType, number, state)
            }
        } catch (error: Throwable) {
            handleFailure(error)
            throw error
        }
    }

    private suspend fun monitorDevice() {
        val interval = (configuration.pollIntervalSeconds ?: defaultPollIntervalSeconds).seconds
        while (currentCoroutineContext().isActive) {
            try {
                stateMutex.withLock {
                    val connected = ensureConnected()
                    connected.refreshStatus()
                    publishStates(connected)
                }
            } catch (error: Throwable) {
                handleFailure(error)
            }
            delay(interval)
        }
    }

    private suspend fun ensureConnected(): IoTSwitch {
        switch?.let { return it }
        val advertisement = withTimeout(30.seconds) {
            Scanner {}.advertisements.first { it.identifier.toString() == configuration.bluetoothIdentifier }
        }
        val connected = IoTSwitch(configuration.bluetoothIdentifier.toIdentifier()) { Peripheral(advertisement) }
        check(connected.connect()) { "Could not connect to ${configuration.id}." }
        switch = connected
        publishAvailability(true)
        return connected
    }

    private suspend fun publishStates(connected: IoTSwitch) {
        connected.lamps.forEach { module ->
            publishModuleState(ModuleType.LAMP, module.number, module.isOn.value)
        }
        connected.outlets.forEach { module ->
            publishModuleState(ModuleType.OUTLET, module.number, module.powerFlowState.value)
        }
    }

    private suspend fun publishModuleState(moduleType: ModuleType, number: Int, state: Boolean) {
        mqtt.publish(
            "$topicRoot/${configuration.id}/${moduleType.topicSegment}/$number/state",
            if (state) "ON" else "OFF",
            retained = true
        )
    }

    private suspend fun handleFailure(error: Throwable) {
        if (error is CancellationException) {
            throw error
        }
        stateMutex.withLock {
            switch?.let { connected -> runCatching { connected.disconnect() } }
            switch = null
        }
        publishAvailability(false)
        System.err.println("BLE controller ${configuration.id}: ${error.message}")
    }

    private suspend fun publishAvailability(available: Boolean) {
        runCatching {
            mqtt.publish(
                "$topicRoot/${configuration.id}/availability",
                if (available) "online" else "offline",
                retained = true
            )
        }
    }
}
