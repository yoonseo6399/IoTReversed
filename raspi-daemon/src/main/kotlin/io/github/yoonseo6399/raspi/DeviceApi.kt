package io.github.yoonseo6399.raspi

import kotlinx.serialization.Serializable

@Serializable
data class ModuleState(val type: String, val number: Int, val on: Boolean)

@Serializable
data class DeviceSnapshot(
    val id: String,
    val displayName: String,
    val availability: String = "offline",
    val modules: List<ModuleState> = emptyList(),
    val observedAt: String? = null,
    val lastError: String? = null,
    val halted: Boolean = false
)

@Serializable
data class StateRequest(val on: Boolean, val requestId: String? = null)

@Serializable
data class StateResponse(
    val id: String, val type: String, val number: Int, val on: Boolean,
    val availability: String, val observedAt: String?, val acknowledged: Boolean = false
)

class DeviceApiException(val status: Int, message: String) : Exception(message)

@Serializable
data class PowerResponse(
    val id: String, val watts: Int, val rawPayloadHex: String, val observedAt: String,
    val source: String = "legacy-0x45-bcd"
)

interface DeviceApi {
    suspend fun power(id: String): PowerResponse = throw DeviceApiException(501, "Power diagnostic is unavailable")
    suspend fun devices(): List<DeviceSnapshot>
    suspend fun status(id: String, fresh: Boolean): DeviceSnapshot
    suspend fun set(id: String, type: ModuleType, number: Int, on: Boolean): StateResponse
}

/** Rejects unavailable state instead of returning a stale value as a successful live read. */
fun DeviceSnapshot.module(type: ModuleType, number: Int, acknowledged: Boolean = false): StateResponse {
    if (availability == "panicked") throw DeviceApiException(409, "Device is panicked; inspect before restarting")
    if (availability != "online") throw DeviceApiException(503, lastError ?: "Device is offline")
    val module = modules.firstOrNull { it.type == type.topicSegment && it.number == number }
        ?: throw DeviceApiException(404, "Module does not exist")
    return StateResponse(id, type.topicSegment, number, module.on, availability, observedAt, acknowledged)
}
