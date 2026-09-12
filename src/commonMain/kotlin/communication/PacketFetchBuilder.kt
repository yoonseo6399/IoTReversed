package io.github.yoonseo6399.communication

import com.juul.kable.NotConnectedException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
sealed class FetchException(message: String) : Exception(message) {
    class Timeout : FetchException("Fetch timeout")
    class Disconnected : FetchException("Device disconnected")
    class DuplicationOverflow(cmd : Command) : FetchException("Duplication limit reached : $cmd")
    data class ProtocolError(val cmd: Command) :
        FetchException("Unexpected packet: $cmd")
}
class PacketFetchBuilder(
    private val connection: PacketTransport,
    private val initialRequest: Packet
) {
    private val targetCommands = mutableMapOf<Command,Boolean>()
    private var retryInterval = 1.seconds
    private var maxRetries = 3
    private var timeout = 2.seconds
    private var duplicationLimit = 6

    fun fetch(cmd: Command, ack : Boolean = true) = apply { targetCommands.put(cmd,ack) }

    fun retry(interval: Duration, count: Int) = apply {
        retryInterval = interval
        maxRetries = count
    }

    fun setTimeout(duration: Duration) = apply {
        timeout = duration
    }
    /** Subscribes before sending and completes after the final acknowledged response. */
    suspend fun execute(): Map<Command, Packet> = connection.requestMutex.withLock {
        connection.failure.value?.let { throw it }
        require(targetCommands.isNotEmpty()) { "At least one response command is required" }

        repeat(maxRetries) { attempt ->
            val results = mutableMapOf<Command, Packet>()
            var duplication = 0

            try {
                withTimeout(timeout) {
                    coroutineScope {
                        val failureWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                            throw connection.failure.filterNotNull().first()
                        }
                        val response = async(start = CoroutineStart.UNDISPATCHED) {
                            connection.packets.first { packet ->
                                if (!targetCommands.contains(packet.cmd)) return@first false
                                if (results.containsKey(packet.cmd)) {
                                    duplication++
                                    if (duplication >= duplicationLimit) throw FetchException.DuplicationOverflow(packet.cmd)
                                }
                                results[packet.cmd] = packet
                                results.keys == targetCommands.keys
                            }
                        }
                        connection.failure.value?.let { throw it }
                        connection.sendPacket(initialRequest)
                        response.await()
                        failureWatcher.cancel()
                    }
                }

                if (results.keys == targetCommands.keys) {
                    return@withLock results
                } else {
                    throw FetchException.ProtocolError(
                        targetCommands.keys.first { it !in results }
                    )
                }

            } catch (e: TimeoutCancellationException) {
                if (attempt == maxRetries - 1) throw FetchException.Timeout()
                delay(retryInterval)

            } catch (e: NotConnectedException) {
                if (attempt == maxRetries - 1) throw FetchException.Disconnected()
                delay(retryInterval)

            } catch (e: FetchException) {
                throw e
            }
        }

        error("Unreachable")
    }
}
