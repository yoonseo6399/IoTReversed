package io.github.yoonseo6399.communication

import kotlin.time.TimeSource

class PacketDiagnostics(private val limit: Int = 6) {
    private var previous: Packet? = null
    private var previousTime = TimeSource.Monotonic.markNow()
    var repetitions: Int = 0
        private set

    /** Counts consecutive identical packets within a burst, not unchanged periodic status polls. */
    fun receive(packet: Packet): Boolean {
        repetitions = if (previous == packet && previousTime.elapsedNow().inWholeMilliseconds < 2000) {
            repetitions + 1
        } else {
            1
        }
        previous = packet
        previousTime = TimeSource.Monotonic.markNow()
        return repetitions >= limit
    }
}
