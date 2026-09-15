package io.github.yoonseo6399.communication

fun parsePowerValue(payload: ByteArray) : Int{
    require(payload.size >= 6) { "Truncated consumer-power response" }
    return decodeBcd(payload[4]) * 100 + decodeBcd(payload[5])
}

@OptIn(ExperimentalStdlibApi::class)
fun uartRxParser(bArr: ByteArray): Packet? {
    // 1. 최소 길이 확인 (헤더 5 + 체크섬 2 = 7바이트는 최소한 있어야 함)
    if (bArr.size < 7) return null

    val header1 = bArr[0].toInt() and 0xFF
    val header2 = bArr[1].toInt() and 0xFF
    val header3 = bArr[2].toInt() and 0xFF
    val dataLength = bArr[4].toInt() and 0xFF // b6 역할
    val cmd = bArr[3]
    if (header1 != 0x7E || header2 != 0x10 || header3 != 0x0F || dataLength > 13 || bArr.size < dataLength + 7) return null
    if(cmd == Command.Conc.Control.byte) {
        println("W : cmd Conc, bypassing checksum")
        return Command.fromByte(cmd)?.let { Packet(it, ByteArray(0)) }
    }
    // 2. 헤더 조건 검사 (0x7E, 0x10, 0x0F)
    if (header1 != 0x7E || header2 != 0x10 || header3 != 0x0F) {
        return null
    }

    // 3. 체크섬 계산 범위 설정
    val payloadEndIdx = dataLength + 5
    var xorChecksum = 0
    var addChecksum = 0
    var xorSum = 0
    var addSum = 0
    for (i in 0 until payloadEndIdx) {
        val current = bArr[i].toInt() and 0xFF
        xorSum = xorSum xor current
        addSum = (addSum + current) and 0xFF
    }

    // 4. 체크섬 비교
    val receivedXor = bArr[payloadEndIdx].toInt() and 0xFF
    val receivedCombined = bArr[payloadEndIdx + 1].toInt() and 0xFF
    val calculatedCombined = (addSum + xorSum) and 0xFF

    if (xorSum != receivedXor || calculatedCombined != receivedCombined) {
        println("ERR: Checksum Mismatch($xorSum,$calculatedCombined : $receivedXor,$receivedCombined) : ${bArr.toHexString()}")
        return null
    }

    // 5. 실제 데이터 추출 (5번 인덱스부터 dataLength 만큼)
    val data = bArr.sliceArray(5 until 5 + dataLength)

    return Command.Companion.fromByte(cmd)?.let { Packet(it,data) }
}
