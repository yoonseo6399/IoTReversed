package io.github.yoonseo6399.communication

class UartFrameDecoder {
    private var pending = byteArrayOf()
    var resynchronizations = 0
        private set

    /** Reassembles notifications and recovers checksum-valid frames behind truncated or corrupt prefixes. */
    fun receive(bytes: ByteArray): List<Packet> {
        val packets = mutableListOf<Packet>()
        for (byte in bytes) {
            pending += byte
            while (pending.size >= 3) {
                if (!headerAt(0)) {
                    pending = pending.drop(1).toByteArray()
                    continue
                }
                if (pending.size < 5) break
                val length = pending[4].toInt() and 255
                if (length > 13) {
                    resynchronizations++
                    pending = pending.drop(1).toByteArray()
                    continue
                }
                val size = length + 7
                if (pending.size < size) {
                    break
                }
                val frame = pending.copyOfRange(0, size)
                if (validFrameAt(0) || frame[3] == Command.Conc.Control.byte) {
                    uartRxParser(frame)?.let(packets::add)
                    pending = pending.drop(size).toByteArray()
                } else {
                    resynchronizations++
                    pending = pending.drop(1).toByteArray()
                }
            }
        }
        return packets
    }

    /** Identifies the device-to-host UART header without indexing beyond available bytes. */
    private fun headerAt(offset: Int): Boolean = pending.size >= offset + 3 &&
        pending[offset] == 0x7e.toByte() && pending[offset + 1] == 0x10.toByte() && pending[offset + 2] == 0x0f.toByte()

    /** Uses both checksums before trusting an embedded candidate as a new frame boundary. */
    private fun validFrameAt(offset: Int): Boolean {
        if (!headerAt(offset) || pending.size < offset + 7) return false
        val length = pending[offset + 4].toInt() and 255
        if (length > 13 || pending.size < offset + length + 7) return false
        var xor = 0
        var sum = 0
        for (i in offset until offset + length + 5) {
            val value = pending[i].toInt() and 255
            xor = xor xor value
            sum = (sum + value) and 255
        }
        return xor == (pending[offset + length + 5].toInt() and 255) &&
            ((sum + xor) and 255) == (pending[offset + length + 6].toInt() and 255)
    }
}
