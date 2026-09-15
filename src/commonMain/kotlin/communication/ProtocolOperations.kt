package io.github.yoonseo6399.communication

import kotlin.time.Duration.Companion.seconds

data class ConsumerPowerReading(val watts: Int, val rawPayload: ByteArray)

/** APK power refresh: 0x33 [01], receipt 0x33 (no ACK), status 0x45 (receiver ACKs [01]). */
suspend fun PacketTransport.readConsumerPower(): ConsumerPowerReading {
    val result = PacketFetchBuilder(this, Packet.create(Command.Power.Control, 1))
        .fetch(Command.Power.Control).fetch(Command.Power.State)
        .retry(1.seconds, 1).setTimeout(8.seconds).execute()
    val payload = result.getValue(Command.Power.State).payload
    val watts = try { parsePowerValue(payload) } catch (_: IllegalArgumentException) {
        throw FetchException.InvalidPayload(Command.Power.State)
    }
    return ConsumerPowerReading(watts, payload.copyOf())
}

private suspend fun PacketTransport.readStatus(request: Command, response: Command): ByteArray =
    PacketFetchBuilder(this, Packet.create(request, 1)).fetch(request).fetch(response)
        .retry(1.seconds, 1).setTimeout(8.seconds).execute().getValue(response).payload

suspend fun PacketTransport.readTemperature(): TemperatureStatus = parseTemperature(readStatus(Command.Temp.RawState, Command.Temp.DetailState))
suspend fun PacketTransport.readAir(): AirStatus = parseAir(readStatus(Command.Air.RawState, Command.Air.DetailState))
suspend fun PacketTransport.readFan(): FanStatus = parseFan(readStatus(Command.Fan.RawState, Command.Fan.DetailState))
suspend fun PacketTransport.readCapabilities(): DeviceCapabilities = parseCapabilities(
    PacketFetchBuilder(this, Packet.create(Command.Device.OtherDevices, 1)).fetch(Command.Device.OtherDevices)
        .retry(1.seconds, 1).setTimeout(8.seconds).execute().getValue(Command.Device.OtherDevices).payload)

/** Builds the APK's half-degree flag + packed-decimal target; allowed range needs device validation. */
fun temperatureTargetPacket(celsius: Double): Packet {
    require(celsius.isFinite() && celsius in 0.0..99.5 && celsius * 2 == (celsius * 2).toInt().toDouble())
    return Packet.create(Command.Temp.ControlChange, ((celsius * 2).toInt() % 2).toByte(), encodeBcd(celsius.toInt()))
}

fun fanTimerPacket(minutes: Int): Packet {
    require(minutes in 0..90 && minutes % 30 == 0) { "APK supports 0, 30, 60 or 90 minutes" }
    return Packet.create(Command.Fan.ControlTime, encodeBcd(minutes))
}
