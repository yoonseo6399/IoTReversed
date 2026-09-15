package io.github.yoonseo6399.raspi

import io.github.yoonseo6399.communication.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Synthetic vectors derived from APK code, not captures from hardware. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ApkProtocolTest {
    @Test fun binaryAndAsciiCommandsCannotBeConfused() {
        assertEquals(Command.Lamp.State, Command.fromByte(0x41))
        assertEquals(Cmd2Command.CONTROL_LAMP, Cmd2Command.fromCode(0x41))
        assertEquals(Command.Power.State, Command.fromByte(0x45))
        assertEquals(Cmd2Command.SET_INFO, Cmd2Command.fromCode(0x45))
        assertNull(Command.fromByte(0x50))
        assertEquals(Cmd2Command.LAMP_STATUS, Cmd2Command.fromCode(0x50))
        assertFalse(Command.Power.Control.ack)
        assertTrue(Command.Power.State.ack)
        assertFalse(Command.Fan.ControlTime.ack)
    }

    @Test fun livePowerUsesDecimalDigitsWithoutScaling() {
        for ((hi, lo, watts) in listOf(Triple(0, 0, 0), Triple(0, 0x55, 55), Triple(0, 0x83, 83), Triple(0x12, 0x34, 1234))) {
            assertEquals(watts, parsePowerValue(byteArrayOf(0, 0, 0, 0, hi.toByte(), lo.toByte(), 0, 0)))
        }
        assertFailsWith<IllegalArgumentException> { parsePowerValue(byteArrayOf(0, 0, 0, 0, 0, 0x5a)) }
        assertFailsWith<IllegalArgumentException> { parsePowerValue(byteArrayOf(0)) }
    }

    @Test fun inferredOutletPowerRespectsCountAndUnsignedBytes() {
        assertEquals(listOf(55.0), Command.Conc.PowerState.parse(byteArrayOf(1, 0, 110, 0, 0)))
        assertEquals(listOf(128.0), Command.Conc.PowerState.parse(byteArrayOf(1, 1, 0)))
        assertEquals(emptyList(), Command.Conc.PowerState.parse(byteArrayOf(0)))
        assertFailsWith<IllegalArgumentException> { Command.Conc.PowerState.parse(byteArrayOf(2, 0, 110)) }
        assertFailsWith<IllegalArgumentException> { Command.Conc.PowerState.parse(byteArrayOf()) }
    }

    @Test fun cumulativePagesAreNotLiveWatts() {
        val data = byteArrayOf(0, 1, 1, 0, 0x80.toByte(), 0, 0xff.toByte(), 0xff.toByte(), 0, 2, 0, 3, 2)
        assertEquals(EnergyPage(2, listOf(1, 256, 32768, 65535, 2, 3)), parseEnergyPage(data))
        assertFailsWith<IllegalArgumentException> { parseEnergyPage(data.copyOf(12)) }
        data[12] = 4
        assertFailsWith<IllegalArgumentException> { parseEnergyPage(data) }
    }

    @Test fun parsesTemperatureFanCapabilitiesAndErrors() {
        assertEquals(TemperatureStatus(23.5, 25.0, true, true), parseTemperature(byteArrayOf(0, 1, 0x23, 0, 0x25, 1, 1)))
        assertEquals(AirStatus(23, 25, true, 2), parseAir(byteArrayOf(0, 0, 0x23, 0, 0x25, 1, 2)))
        assertEquals(FanStatus(true, 3, 90), parseFan(byteArrayOf(0, 1, 3, 0x90.toByte(), 0, 0)))
        assertEquals(DeviceCapabilities(4, true, false, true, 128), parseCapabilities(byteArrayOf(5, 0, 1, 0, 1, 0, 0x80.toByte())))
        val error = assertFailsWith<DeviceStatusError> { parseTemperature(byteArrayOf(1, 0, 69, 76, 48, 49)) }
        assertEquals("EL01", error.code)
        assertFailsWith<DeviceStatusError> { parseFan(byteArrayOf(1, 0, 69, 76, 48, 49)) }
        assertFailsWith<IllegalArgumentException> { parseTemperature(byteArrayOf(0, 1)) }
        assertFailsWith<IllegalArgumentException> { parseFan(byteArrayOf(0, 1, 1, 0x9a.toByte())) }
        assertFailsWith<IllegalArgumentException> { parseCapabilities(byteArrayOf(0)) }
    }

    @Test fun controlPayloadsMatchApkEncoding() {
        assertEquals(Packet.create(Command.Temp.ControlChange, 1, 0x25), temperatureTargetPacket(25.5))
        assertEquals(Packet.create(Command.Fan.ControlTime, 0x60), fanTimerPacket(60))
        assertFailsWith<IllegalArgumentException> { temperatureTargetPacket(25.2) }
        assertFailsWith<IllegalArgumentException> { fanTimerPacket(100) }
    }

    @Test fun liveReadSubscribesBeforeWriteAndConsumesBothReplies() = runTest {
        var sends = 0
        val transport = object : PacketTransport {
            override val packets = MutableSharedFlow<Packet>()
            override val failure = MutableStateFlow<Throwable?>(null)
            override val requestMutex = Mutex()
            override suspend fun sendPacket(packet: Packet) {
                sends++
                assertEquals(Packet.create(Command.Power.Control, 1), packet)
                packets.emit(Packet.create(Command.Power.Control, 1))
                // Feed a checksum-valid synthetic notification through the real decoder.
                val frame = byteArrayOf(0x7e, 0x10, 0x0f, 0x45, 8, 0, 0, 0, 0, 0, 0x55, 0, 0, 0, 0)
                var sum = 0
                var xor = 0
                for (i in 0..12) { sum += frame[i].toInt() and 255; xor = xor xor (frame[i].toInt() and 255) }
                frame[13] = xor.toByte()
                frame[14] = (sum + xor).toByte()
                val decoder = UartFrameDecoder()
                assertTrue(decoder.receive(frame.copyOfRange(0, 7)).isEmpty())
                decoder.receive(frame.copyOfRange(7, frame.size)).forEach { packets.emit(it) }
            }
        }
        val reading = transport.readConsumerPower()
        assertEquals(55, reading.watts)
        assertEquals(8, reading.rawPayload.size)
        assertEquals(1, sends)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun missingPowerStatusTimesOutWithoutRetry() = runTest {
        var sends = 0
        val transport = object : PacketTransport {
            override val packets = MutableSharedFlow<Packet>()
            override val failure = MutableStateFlow<Throwable?>(null)
            override val requestMutex = Mutex()
            override suspend fun sendPacket(packet: Packet) { sends++; packets.emit(Packet.create(Command.Power.Control, 1)) }
        }
        assertFailsWith<FetchException.Timeout> { transport.readConsumerPower() }
        assertEquals(1, sends)
    }
}
