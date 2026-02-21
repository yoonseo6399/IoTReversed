package io.github.yoonseo6399.communication

import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import iotModules.ConnectionEngine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
sealed class FetchException(message: String) : Exception(message) {
    class Timeout : FetchException("Fetch timeout")
    class Disconnected : FetchException("Device disconnected")
    class DuplicationOverflow(cmd : Command) : FetchException("Duplication limit reached : $cmd")
    data class ProtocolError(val cmd: Command) :
        FetchException("Unexpected packet: $cmd")
    class FlowNotInitialized : FetchException("Packet Flow is not initialized")

}
class PacketFetchBuilder(
    private val engine: ConnectionEngine,
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
    /**warn this is not Async do not use it with other request**/
    @OptIn(FlowPreview::class)
    suspend fun execute(): FetchResult {
        if(engine.receivedPacketFlow == null) FetchResult.Failure(FetchException.FlowNotInitialized())
        repeat(maxRetries) { attempt ->
            val results = mutableMapOf<Command, Packet>()
            var duplication = 0

            try {
                withTimeout(timeout) {
                    coroutineScope {
                        engine.enQueuePacket(initialRequest)
                        engine.receivedPacketFlow
                            .takeWhile { results.keys != targetCommands.keys }
                            .collect { packet ->
                                if (!targetCommands.contains(packet.cmd)) return@collect
                                if (results.containsKey(packet.cmd)) {
                                    duplication++
                                    if (duplication >= duplicationLimit) throw FetchException.DuplicationOverflow(packet.cmd)
                                }
                                results[packet.cmd] = packet
                            }
                    }
                }

                // 성공
                if (results.keys == targetCommands.keys) {
                    return FetchResult.Success(results)
                } else {
                    throw FetchException.ProtocolError(
                        targetCommands.keys.first { it !in results }
                    )
                }

            } catch (e: TimeoutCancellationException) {
                if (attempt == maxRetries - 1) return FetchResult.Failure(FetchException.Timeout())
                delay(retryInterval)

            } catch (e: NotConnectedException) {
                if (attempt == maxRetries - 1) return FetchResult.Failure(FetchException.Disconnected())
                delay(retryInterval)

            } catch (e: FetchException) {
                // duplication / protocol error → 즉시 중단
                return FetchResult.Failure(e)
            }
        }

        error("Unreachable")
    }
}
sealed class FetchResult(val exception: FetchException?,val data : Map<Command, Packet>? = null){
    fun onSuccess(action : FetchData.() -> Unit) : FetchResult {
        if (this is Success) action(FetchData(this.data!!))
        return this
    }
    fun onFailure(action : Failure.() -> Unit) : FetchResult {
        if (this is Failure) action(this)
        return this
    }
    class Success(data: Map<Command, Packet>) : FetchResult(null,data)
    class Failure(exception: FetchException) : FetchResult(exception,null)
}
data class FetchData(val data : Map<Command, Packet>) : Map<Command, Packet> by data{
    override fun get(key: Command): Packet {
        return data[key] ?: throw NoSuchElementException("Data not exist")
    }
}