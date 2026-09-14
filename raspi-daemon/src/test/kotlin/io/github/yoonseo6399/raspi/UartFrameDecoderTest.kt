package io.github.yoonseo6399.raspi

import io.github.yoonseo6399.communication.*
import kotlin.test.*

@OptIn(ExperimentalStdlibApi::class)
class UartFrameDecoderTest {
    /** Replays the exact corrupt notification that made the deployed controller halt overnight. */
    @Test fun recoversStatusFromIncidentCapture() {
        val decoder = UartFrameDecoder()
        val corrupted = "7e100f4409037e100f34010155287e10".hexToByteArray()
        val result = decoder.receive(corrupted) + decoder.receive(frame(Command.Lamp.State, byteArrayOf(1, 0)))
        assertEquals(listOf(Command.Device.Status, Command.Lamp.State), result.map { it.cmd })
        assertContentEquals(byteArrayOf(1), result.first().payload)
        assertTrue(decoder.resynchronizations > 0)
    }

    /** Handles arbitrary notification splits and several frames in one notification. */
    @Test fun decodesFragmentedAndConcatenatedFrames() {
        val data = frame(Command.Lamp.State, byteArrayOf(1, 0)) + frame(Command.Conc.State, byteArrayOf(3, 1, 1, 1))
        for (split in 0..data.size) {
            val decoder = UartFrameDecoder()
            assertEquals(listOf(Command.Lamp.State, Command.Conc.State),
                (decoder.receive(data.copyOfRange(0, split)) + decoder.receive(data.copyOfRange(split, data.size))).map { it.cmd })
        }
    }

    /** Does not interpret an embedded header inside a valid payload as a second frame. */
    @Test fun preservesValidPayloadWithEmbeddedFrame() {
        val payload = frame(Command.Device.Status, byteArrayOf(1))
        val result = UartFrameDecoder().receive(frame(Command.Device.Status, payload))
        assertEquals(1, result.size)
        assertContentEquals(payload, result.single().payload)
    }

    /** Rejects corrupt frames and bounds-checks truncated headers before inspecting their payload. */
    @Test fun skipsCorruptionWithoutInventingAcknowledgements() {
        val bad = frame(Command.Lamp.State, byteArrayOf(1, 0)).also { it[it.lastIndex] = 0 }
        assertTrue(UartFrameDecoder().receive(bad).isEmpty())
        assertNull(uartRxParser("7e100f44ff0000".hexToByteArray()))
        assertNull(uartRxParser("00000032000000".hexToByteArray()))
        assertEquals(Command.Lamp.State, UartFrameDecoder().receive(bad + frame(Command.Lamp.State, byteArrayOf(1, 0))).single().cmd)
    }

    /** Encodes a device-to-host frame with both protocol checksums for decoder tests. */
    private fun frame(command: Command, payload: ByteArray): ByteArray {
        val data = byteArrayOf(0x7e, 0x10, 0x0f, command.byte, payload.size.toByte()) + payload
        val xor = data.fold(0) { value, byte -> value xor (byte.toInt() and 255) }
        val sum = (data.sumOf { it.toInt() and 255 } + xor) and 255
        return data + byteArrayOf(xor.toByte(), sum.toByte())
    }
}
