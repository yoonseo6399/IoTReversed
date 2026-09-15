package io.github.yoonseo6399.raspi

import io.github.yoonseo6399.iotModules.IoTSwitch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

const val REGISTRATION_PATH = "registry/devices/register"

@Serializable
data class RegistrationRequest(
    val room: String? = null,
    val requestId: String,
    val id: String? = null,
    val name: String? = null,
    val pollIntervalSeconds: Long? = null
) {
    /** Validates settings and normalizes the legacy room alias for deduplication. */
    fun normalized(): RegistrationRequest {
        val displayName = (name ?: room)?.trim()
        require(displayName != null && displayName.length in 1..100) { "name (or room) must contain 1–100 characters" }
        require(name == null || room == null || name.trim() == room.trim()) { "name and room must match when both are provided" }
        require(requestId.isNotBlank() && requestId.length <= 100) { "requestId must contain 1–100 characters" }
        id?.let { validateTopicSegment(it, "device id") }
        pollIntervalSeconds?.let { require(it in 1..86400) { "pollIntervalSeconds must be between 1 and 86400" } }
        return copy(room = null, name = displayName)
    }
}

@Serializable
data class RegistrationResult(
    val requestId: String,
    val status: String,
    val device: SwitchConfiguration? = null,
    val error: String? = null
)

class DeviceRegistration(
    private val registry: DeviceRegistry,
    private val discovery: BluetoothDiscovery,
    private val mqtt: MqttPublisher,
    private val findNewMac: suspend (Set<String>) -> String? = { known ->
        discovery.exclusive {
            try {
                IoTSwitch.findNewDevice(matches = {
                    val mac = bluetoothMac(it.identifier.toString())
                    mac != null && mac !in known
                })?.let { bluetoothMac(it.identifier.toString()) }
            } finally {
                withContext(NonCancellable) { delay(750) }
            }
        }
    }
) {
    private val mutex = Mutex()
    private data class Completed(val request: RegistrationRequest, val result: RegistrationResult)
    private val completed = linkedMapOf<String, Completed>()

    /** Shares discovery, validation, persistence and deduplication across HTTP and MQTT. */
    suspend fun register(request: RegistrationRequest): RegistrationResult {
        val normalized = try {
            request.normalized()
        } catch (error: IllegalArgumentException) {
            return publish(RegistrationResult(request.requestId, "invalid", error = error.message))
        }
        if (!mutex.tryLock()) return publish(RegistrationResult(request.requestId, "busy"))
        try {
            completed[normalized.requestId]?.let {
                return publish(if (it.request == normalized) it.result else
                    RegistrationResult(normalized.requestId, "conflict", error = "requestId was already used for another registration"))
            }
            val current = registry.configuration.value
            if (current.devices.any { it.id == normalized.id }) {
                return publish(RegistrationResult(normalized.requestId, "conflict", error = "Device id already registered"))
            }
            publish(RegistrationResult(normalized.requestId, "waiting"))
            val result = try {
                val known = current.devices.mapNotNull { bluetoothMac(it.bluetoothIdentifier) }.toSet()
                val mac = withTimeout(30.seconds) { findNewMac(known) }
                if (mac == null) RegistrationResult(normalized.requestId, "not_found")
                else {
                    val address = requireNotNull(bluetoothMac(mac)) { "Invalid discovered MAC address" }
                    val device = SwitchConfiguration(
                        normalized.id ?: "switch-${address.replace(":", "").lowercase()}",
                        address, checkNotNull(normalized.name), normalized.pollIntervalSeconds
                    )
                    withContext(NonCancellable) {
                        registry.register(device)
                        val registered = RegistrationResult(normalized.requestId, "registered", device)
                        remember(normalized, registered)
                        registered
                    }
                }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                RegistrationResult(normalized.requestId, "not_found")
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                RegistrationResult(normalized.requestId, "conflict", error = error.message)
            } catch (error: Exception) {
                System.err.println("Device registration failed: ${error::class.simpleName}")
                RegistrationResult(normalized.requestId, "error", error = "Registration failed; inspect daemon logs")
            }
            remember(normalized, result)
            return publish(result)
        } finally {
            mutex.unlock()
        }
    }

    /** Retains bounded results so cross-transport retries cannot register another device. */
    private fun remember(request: RegistrationRequest, result: RegistrationResult) {
        completed[request.requestId] = Completed(request, result)
        if (completed.size > 100) completed.remove(completed.keys.first())
    }

    /** Reports progress without turning an MQTT outage into a failed HTTP registration. */
    private suspend fun publish(result: RegistrationResult): RegistrationResult {
        try {
            mqtt.publish("${registry.configuration.value.topicRoot}/$REGISTRATION_PATH/result", hubJson.encodeToString(result))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            System.err.println("MQTT registration result publication failed: ${error::class.simpleName}")
        }
        return result
    }
}
