package io.github.yoonseo6399.raspi

import com.juul.kable.NotConnectedException
import io.github.yoonseo6399.communication.Command
import io.github.yoonseo6399.communication.FetchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectionTest {
    private class Session(var connectError: Throwable? = null) : SwitchSession {
        override var ready = false
        override val failure = MutableStateFlow<Throwable?>(null)
        override val modules = listOf(ModuleState("lamp", 1, false))
        var refreshError: Throwable? = null
        var writeError: Throwable? = null
        var writes = 0
        var refreshes = 0
        var closed = false

        /** Simulates initialization without BLE hardware. */
        override suspend fun connect() { connectError?.let { throw it }; ready = true }

        /** Models a read failing in an otherwise initialized session. */
        override suspend fun refresh() { refreshes++; refreshError?.let { throw it } }

        /** Counts writes to detect unsafe command replay during recovery. */
        override suspend fun set(type: ModuleType, number: Int, on: Boolean): Boolean {
            writes++
            writeError?.let { throw it }
            return true
        }

        /** Simulates a closed BLE link. */
        override suspend fun disconnect() { ready = false }

        /** Records native session disposal. */
        override fun close() { closed = true }
    }

    private class Publisher : MqttPublisher {
        val availability = mutableListOf<String>()

        /** Records availability without publishing to a real broker. */
        override suspend fun publish(topic: String, payload: String, retained: Boolean) {
            if (topic.endsWith("/availability")) availability += payload
        }
    }

    /** Creates the actual controller with replaceable sessions and no hardware dependency. */
    private fun controller(scope: CoroutineScope, mqtt: Publisher = Publisher(), factory: suspend () -> SwitchSession) =
        BluetoothSwitchController(SwitchConfiguration("room", "AA:BB:CC:DD:EE:FF"), "test", 1,
            mqtt, scope, BluetoothDiscovery(), factory)

    /** A dead cached session is discarded before the next request and availability changes promptly. */
    @Test fun disconnectedSessionIsReplaced() = runTest {
        val first = Session()
        val second = Session()
        var attempts = 0
        val hub = controller(backgroundScope) { if (attempts++ == 0) first else second }
        hub.readStatus()
        runCurrent()
        first.ready = false
        first.failure.value = NotConnectedException("radio disconnected")
        runCurrent()
        assertEquals("offline", hub.snapshot().availability)
        assertFalse(hub.snapshot().halted)
        hub.readStatus()
        assertEquals(2, attempts)
        assertTrue(first.closed)
        assertEquals(0, first.refreshes)
        assertEquals(0, second.refreshes)
        assertEquals("online", hub.snapshot().availability)
        hub.stop()
    }

    /** More than three ordinary connection failures cannot kill the monitor. */
    @Test fun monitorSurvivesRepeatedConnectionFailures() = runTest {
        var attempts = 0
        val hub = controller(backgroundScope) {
            attempts++
            Session(if (attempts <= 4) NotConnectedException("offline") else null)
        }
        hub.start()
        advanceTimeBy(23_000)
        runCurrent()
        assertEquals(5, attempts)
        assertEquals("online", hub.snapshot().availability)
        assertFalse(hub.snapshot().halted)
        hub.stop()
    }

    /** A failed command is reported once, while the monitor reconnects without replaying it. */
    @Test fun failedWriteDoesNotStopRecoveryOrReplay() = runTest {
        val first = Session()
        val second = Session()
        var attempts = 0
        val hub = controller(backgroundScope) { if (attempts++ == 0) first else second }
        hub.start()
        runCurrent()
        first.writeError = NotConnectedException("disconnected during write")
        assertFailsWith<NotConnectedException> { hub.setState(ModuleType.LAMP, 1, true) }
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals("online", hub.snapshot().availability)
        assertEquals(1, first.writes)
        assertEquals(0, second.writes)
        assertTrue(first.closed)
        hub.stop()
    }

    /** A local session cancellation or response timeout does not cancel the controller's own coroutine. */
    @Test fun monitorSurvivesSessionCancellationAndTimeout() = runTest {
        val first = Session().apply { refreshError = CancellationException("native session cancelled") }
        val second = Session().apply { refreshError = FetchException.Timeout() }
        val third = Session()
        val sessions = listOf(first, second, third)
        var attempts = 0
        val hub = controller(backgroundScope) { sessions[attempts++] }
        hub.start()
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(3, attempts)
        assertTrue(first.closed)
        assertTrue(second.closed)
        assertFalse(hub.snapshot().halted)
        assertEquals("online", hub.snapshot().availability)
        hub.stop()
    }

    /** A receiver panic stays latched, closes its connection and never triggers a reconnect. */
    @Test fun panicStopsConnectionAndSurvivesShutdown() = runTest {
        val first = Session()
        val publisher = Publisher()
        var attempts = 0
        val hub = controller(backgroundScope, publisher) { attempts++; first }
        hub.start()
        runCurrent()
        first.failure.value = FetchException.DuplicationOverflow(Command.Lamp.State)
        runCurrent()
        advanceTimeBy(120_000)
        assertEquals(1, attempts)
        assertTrue(first.closed)
        assertTrue(hub.snapshot().halted)
        assertEquals("panicked", hub.snapshot().availability)
        assertFailsWith<DeviceApiException> { hub.readStatus() }
        hub.stop()
        assertEquals("panicked", publisher.availability.last())
    }
}
