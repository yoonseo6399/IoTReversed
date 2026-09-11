@file:OptIn(ExperimentalUuidApi::class)

package io.github.yoonseo6399.communication

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val registrationScope = CoroutineScope(Job() + CoroutineName("registrationScope") + Dispatchers.IO)

val RX_SERVICE_UUID = Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
val RX_CHAR_UUID = Uuid.parse("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
val TX_CHAR_UUID = Uuid.parse("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
val UUID_HEART_RATE_MEASUREMENT = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
data class DeviceStatus(
    val lampStatus : List<Boolean>,
    val concStatus : List<Boolean>,
    val concPowerUsage : List<Double>,
    val concCutStatus : ByteArray
) {
    override fun toString(): String {
        return "lamp : $lampStatus\n" +
                "conc : $concStatus\n" +
                "power : $concPowerUsage"

    }
}

const val CMD2_REQ_SETTING : Byte = 126

