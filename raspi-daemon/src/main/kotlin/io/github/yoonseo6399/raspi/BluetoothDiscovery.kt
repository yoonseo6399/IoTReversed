package io.github.yoonseo6399.raspi

import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class BluetoothDiscovery {
    private val adapterMutex = Mutex()

    /** Serializes BlueZ discovery and connection setup across all controllers and registration. */
    suspend fun <T> exclusive(action: suspend BluetoothDiscovery.() -> T): T =
        adapterMutex.withLock { action() }

    /** Waits for an advertisement and lets native StopDiscovery settle before connecting. */
    suspend fun find(timeoutSeconds: Long = 30, matches: (Advertisement) -> Boolean): Advertisement {
        try {
            return withTimeout(timeoutSeconds.seconds) { Scanner {}.advertisements.first(matches) }
        } finally {
            withContext(NonCancellable) { delay(750) }
        }
    }
}

/** Normalizes either a MAC address or a Kable BlueZ object-path identifier. */
fun bluetoothMac(identifier: String): String? {
    val address = identifier.trim()
    if (Regex("(?i)^[0-9a-f]{2}([:_-][0-9a-f]{2}){5}$").matches(address)) {
        return address.replace('_', ':').replace('-', ':').uppercase()
    }
    return Regex("(?i)dev_([0-9a-f]{2}(?:_[0-9a-f]{2}){5})(?![0-9a-f_])")
        .find(address)?.groupValues?.get(1)?.replace('_', ':')?.uppercase()
}

/** Matches the registration advertisement names used by the Android application. */
fun isRegistrationAdvertisement(name: String?): Boolean =
    name?.let { it.startsWith("Clio_UART.") || it.startsWith("Clio_UART [Clio_UART.]") } == true
