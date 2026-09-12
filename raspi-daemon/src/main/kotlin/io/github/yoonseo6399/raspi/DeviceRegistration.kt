package io.github.yoonseo6399.raspi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class RegistrationRequest(val room: String, val requestId: String, val id: String? = null)

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
    private val findNewMac: suspend (Set<String>) -> String = { known ->
        discovery.exclusive {
            val advertisement = find(15) {
                val mac = bluetoothMac(it.identifier.toString())
                mac != null && mac !in known && isRegistrationAdvertisement(it.name)
            }
            checkNotNull(bluetoothMac(advertisement.identifier.toString()))
        }
    }
) {
    private val mutex = Mutex()
    private val completed = linkedMapOf<String, RegistrationResult>()

    /** Registers the first unregistered Android-compatible pairing advertisement using the requested room. */
    suspend fun register(request: RegistrationRequest) {
        require(request.room.isNotBlank() && request.room.length <= 100) { "room must contain 1–100 characters" }
        require(request.requestId.isNotBlank() && request.requestId.length <= 100) { "requestId must contain 1–100 characters" }
        request.id?.let { validateTopicSegment(it, "device id") }
        if (!mutex.tryLock()) {
            publish(RegistrationResult(request.requestId, "busy"))
            return
        }
        try {
            completed[request.requestId]?.let { publish(it); return }
            val result = try {
                val current = registry.configuration.value
                require(current.devices.none { it.id == request.id }) { "Device id already registered" }
                publish(RegistrationResult(request.requestId, "waiting"))
                val known = current.devices.mapNotNull { bluetoothMac(it.bluetoothIdentifier) }.toSet()
                val mac = findNewMac(known)
                val device = SwitchConfiguration(request.id ?: "switch-${mac.replace(":", "").lowercase()}", mac, request.room.trim())
                registry.register(device)
                RegistrationResult(request.requestId, "registered", device)
            } catch (_: TimeoutCancellationException) {
                RegistrationResult(request.requestId, "not_found")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                RegistrationResult(request.requestId, "error", error = error.message)
            }
            completed[request.requestId] = result
            if (completed.size > 100) completed.remove(completed.keys.first())
            publish(result)
        } finally {
            mutex.unlock()
        }
    }

    /** Publishes a correlated registration result without retaining an executable request. */
    private suspend fun publish(result: RegistrationResult) {
        mqtt.publish("${registry.configuration.value.topicRoot}/registry/devices/register/result",
            hubJson.encodeToString(result))
    }
}
