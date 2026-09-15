package io.github.yoonseo6399.communication

/** Packed decimal, not binary or hexadecimal text. Invalid digits must not become plausible watts. */
internal fun decodeBcd(value: Byte): Int {
    val unsigned = value.toInt() and 255
    val tens = unsigned shr 4
    val ones = unsigned and 15
    require(tens <= 9 && ones <= 9) { "Invalid packed-decimal digit" }
    return tens * 10 + ones
}

internal fun encodeBcd(value: Int): Byte {
    require(value in 0..99)
    return ((value / 10 shl 4) or (value % 10)).toByte()
}

data class TemperatureStatus(val currentCelsius: Double, val targetCelsius: Double, val away: Boolean, val on: Boolean)
data class AirStatus(val currentCelsius: Int, val targetCelsius: Int, val on: Boolean, val speed: Int)
data class FanStatus(val on: Boolean, val speed: Int, val minutes: Int)
data class DeviceCapabilities(val lamps: Int, val temperature: Boolean, val airConditioner: Boolean, val fan: Boolean, val model: Int?)
/** Six unsigned big-endian counters per page. Units and sampling period are not established. */
data class EnergyPage(val index: Int, val counters: List<Int>)

class DeviceStatusError(val code: String) : Exception("Device reported status error: $code")

private fun requireHealthy(data: ByteArray, length: Int) {
    require(data.isNotEmpty()) { "Empty status payload" }
    if (data[0] != 0.toByte()) {
        require(data.size >= 6) { "Truncated device error" }
        throw DeviceStatusError(data.copyOfRange(2, 6).decodeToString())
    }
    require(data.size >= length) { "Truncated status payload" }
}

fun parseTemperature(data: ByteArray): TemperatureStatus {
    requireHealthy(data, 7)
    require(data[1].toInt() in 0..1 && data[3].toInt() in 0..1) { "Invalid half-degree flag" }
    return TemperatureStatus(decodeBcd(data[2]) + data[1] * 0.5,
        decodeBcd(data[4]) + data[3] * 0.5, data[5] == 1.toByte(), data[6] == 1.toByte())
}

fun parseAir(data: ByteArray): AirStatus {
    requireHealthy(data, 7)
    return AirStatus(decodeBcd(data[2]), decodeBcd(data[4]), data[5] == 1.toByte(), data[6].toInt() and 255)
}

fun parseFan(data: ByteArray): FanStatus {
    requireHealthy(data, 4)
    return FanStatus(data[1] == 1.toByte(), data[2].toInt() and 255, decodeBcd(data[3]))
}

fun parseCapabilities(data: ByteArray): DeviceCapabilities {
    require(data.size >= 6) { "Truncated capability response" }
    return DeviceCapabilities((data[0].toInt() and 255).coerceAtMost(4), data[2] == 1.toByte(),
        data[3] == 1.toByte(), data[4] == 1.toByte(), data.getOrNull(6)?.let { it.toInt() and 255 })
}

fun parseEnergyPage(data: ByteArray): EnergyPage {
    require(data.size == 13) { "Energy page must contain six counters and a page index" }
    val page = data[12].toInt() and 255
    require(page in 0..3) { "Invalid energy page" }
    return EnergyPage(page, List(6) { i -> ((data[i * 2].toInt() and 255) shl 8) or (data[i * 2 + 1].toInt() and 255) })
}
