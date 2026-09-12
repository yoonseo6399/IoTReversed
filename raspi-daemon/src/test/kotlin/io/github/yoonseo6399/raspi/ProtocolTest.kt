package io.github.yoonseo6399.raspi

import io.github.yoonseo6399.communication.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class ProtocolTest {
    private class Transport(val reply: suspend Transport.() -> Unit) : PacketTransport {
        override val packets = MutableSharedFlow<Packet>()
        override val failure = MutableStateFlow<Throwable?>(null)
        override val requestMutex = Mutex()
        var sends = 0

        /** Simulates an immediate reply during the write to expose subscription races. */
        override suspend fun sendPacket(packet: Packet) { sends++; reply() }
    }

    /** A complete status sequence must finish without waiting for one extra packet or a timeout. */
    @Test fun completesAtFinalPacket() = runTest {
        val commands = listOf(Command.Device.Status, Command.Lamp.State, Command.Conc.State, Command.Conc.PowerState, Command.Conc.CutState)
        val transport = Transport { commands.forEach { packets.emit(Packet.create(it, 1)) } }
        val builder = PacketFetchBuilder(transport, Packet.create(Command.Device.Status, 1))
        commands.forEach { builder.fetch(it) }
        assertEquals(commands.toSet(), builder.execute().keys)
        assertEquals(1, transport.sends)
        assertEquals(0, testScheduler.currentTime)
    }

    /** Identical duplicates are still consumed but cannot cause an unbounded request loop. */
    @Test fun repeatedPacketsAbortWithoutRetry() = runTest {
        val transport = Transport { repeat(8) { packets.emit(Packet.create(Command.Lamp.State, 1, 0)) } }
        assertFailsWith<FetchException.DuplicationOverflow> {
            PacketFetchBuilder(transport, Packet.create(Command.Device.Status, 1))
                .fetch(Command.Lamp.State).fetch(Command.Conc.State).execute()
        }
        assertEquals(1, transport.sends)
    }

    /** A latched receiver failure prevents another request from reaching the device. */
    @Test fun propagatesPanicBeforeSending() = runTest {
        val transport = Transport {}
        transport.failure.value = FetchException.DuplicationOverflow(Command.Lamp.State)
        assertFailsWith<FetchException.DuplicationOverflow> {
            PacketFetchBuilder(transport, Packet.create(Command.Device.Status, 1)).fetch(Command.Lamp.State).execute()
        }
        assertEquals(0, transport.sends)
    }

    /** Concurrent callers must consume their own fresh responses. */
    @Test fun serializesTransactions() = runTest {
        val transport = Transport { packets.emit(Packet.create(Command.Lamp.State, sends.toByte())) }
        val one = async { PacketFetchBuilder(transport, Packet.create(Command.Device.Status, 1)).fetch(Command.Lamp.State).execute() }
        val two = async { PacketFetchBuilder(transport, Packet.create(Command.Device.Status, 1)).fetch(Command.Lamp.State).execute() }
        assertEquals(1, one.await().getValue(Command.Lamp.State).payload[0].toInt())
        assertEquals(2, two.await().getValue(Command.Lamp.State).payload[0].toInt())
    }

    /** Two retransmissions per command are accepted when the sequence keeps progressing. */
    @Test fun distinguishesPanicFromProgress() {
        val diagnostics = PacketDiagnostics()
        repeat(3) {
            assertFalse(diagnostics.receive(Packet.create(Command.Lamp.State, 1, 0)))
            assertFalse(diagnostics.receive(Packet.create(Command.Lamp.State, 1, 0)))
            assertFalse(diagnostics.receive(Packet.create(Command.Conc.State, 3)))
        }
        repeat(5) { assertFalse(diagnostics.receive(Packet.create(Command.Lamp.State, 1, 0))) }
        assertTrue(diagnostics.receive(Packet.create(Command.Lamp.State, 1, 0)))
    }

    /** Accepts human-readable and BlueZ addresses and only the Android pairing names. */
    @Test fun parsesDiscoveryIdentifiers() {
        assertEquals("E0:C7:39:F7:C8:9D", bluetoothMac("e0:c7:39:f7:c8:9d"))
        assertEquals("E0:C7:39:F7:C8:9D", bluetoothMac("{\"object_path\":\"/org/bluez/hci0/dev_E0_C7_39_F7_C8_9D\"}"))
        assertNull(bluetoothMac("/org/bluez/hci0/dev_E0_C7"))
        assertTrue(isRegistrationAdvertisement("Clio_UART."))
        assertTrue(isRegistrationAdvertisement("Clio_UART [Clio_UART.]"))
        assertFalse(isRegistrationAdvertisement("Clio_UART"))
    }
}
