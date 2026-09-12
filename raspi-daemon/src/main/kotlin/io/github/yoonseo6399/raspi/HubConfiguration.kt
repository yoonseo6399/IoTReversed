package io.github.yoonseo6399.raspi

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.AtomicMoveNotSupportedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val hubJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

@Serializable
data class MqttConfiguration(
    val brokerUri: String = "tcp://127.0.0.1:1883",
    val clientId: String = "iot-hub-rpi",
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class SwitchConfiguration(
    val id: String,
    val bluetoothIdentifier: String,
    val displayName: String = id,
    val pollIntervalSeconds: Long? = null
)

@Serializable
data class HubConfiguration(
    val mqtt: MqttConfiguration = MqttConfiguration(),
    val topicRoot: String = "iot-hub",
    val defaultPollIntervalSeconds: Long = 20,
    val devices: List<SwitchConfiguration> = emptyList()
)

class DeviceRegistry(private val configurationPath: Path) {
    private val mutex = Mutex()
    private val _configuration = MutableStateFlow(HubConfiguration())

    val configuration: StateFlow<HubConfiguration> = _configuration

    suspend fun load(): HubConfiguration = mutex.withLock {
        val loaded = withContext(Dispatchers.IO) {
            require(Files.isRegularFile(configurationPath)) {
                "Configuration file does not exist: $configurationPath"
            }
            hubJson.decodeFromString<HubConfiguration>(Files.readString(configurationPath))
        }
        loaded.validate()
        _configuration.value = loaded
        loaded
    }

    suspend fun upsert(device: SwitchConfiguration): HubConfiguration = mutex.withLock {
        device.validate()
        val current = _configuration.value
        val updatedDevices = current.devices.filterNot { it.id == device.id } + device
        val updated = current.copy(devices = updatedDevices.sortedBy(SwitchConfiguration::id))
        updated.validate()
        persist(updated)
        _configuration.value = updated
        updated
    }

    /** Adds a discovered device atomically without overwriting an existing room or MAC registration. */
    suspend fun register(device: SwitchConfiguration): HubConfiguration = mutex.withLock {
        val current = _configuration.value
        require(current.devices.none { it.id == device.id }) { "Device id already registered" }
        val updated = current.copy(devices = (current.devices + device).sortedBy(SwitchConfiguration::id))
        updated.validate()
        persist(updated)
        _configuration.value = updated
        updated
    }

    suspend fun remove(deviceId: String): HubConfiguration = mutex.withLock {
        validateTopicSegment(deviceId, "device id")
        val current = _configuration.value
        val updated = current.copy(devices = current.devices.filterNot { it.id == deviceId })
        require(updated.devices.size != current.devices.size) { "Unknown device: $deviceId" }
        persist(updated)
        _configuration.value = updated
        updated
    }

    private suspend fun persist(configuration: HubConfiguration) {
        withContext(Dispatchers.IO) {
            val destination = configurationPath.toAbsolutePath()
            val parent = checkNotNull(destination.parent)
            Files.createDirectories(parent)
            val temporary = Files.createTempFile(parent, ".devices-", ".json")
            try {
                Files.writeString(temporary, hubJson.encodeToString(configuration))
                try {
                    Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, destination, REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}

fun HubConfiguration.validate() {
    require(topicRoot.isNotBlank() && !topicRoot.startsWith('/') && !topicRoot.endsWith('/')) {
        "topicRoot must be a non-empty MQTT topic prefix without leading or trailing '/'."
    }
    require(defaultPollIntervalSeconds > 0) { "defaultPollIntervalSeconds must be positive." }
    require(devices.map(SwitchConfiguration::id).distinct().size == devices.size) { "Device ids must be unique." }
    val addresses = devices.map { bluetoothMac(it.bluetoothIdentifier) ?: it.bluetoothIdentifier }
    require(addresses.distinct().size == addresses.size) { "Bluetooth identifiers must be unique." }
    require(!topicRoot.contains('+') && !topicRoot.contains('#') && !topicRoot.contains('\u0000')) { "topicRoot cannot contain MQTT wildcards or NUL" }
    devices.forEach(SwitchConfiguration::validate)
}

private fun SwitchConfiguration.validate() {
    validateTopicSegment(id, "device id")
    require(bluetoothIdentifier.isNotBlank()) { "bluetoothIdentifier is required for $id." }
    pollIntervalSeconds?.let { require(it > 0) { "pollIntervalSeconds must be positive for $id." } }
}

fun validateTopicSegment(value: String, label: String) {
    require(value.matches(Regex("[A-Za-z0-9_-]+"))) {
        "$label must contain only letters, digits, '_' or '-': $value"
    }
}
