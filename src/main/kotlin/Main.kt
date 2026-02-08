package io.github.yoonseo6399

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    val exRX = "7e100f41050101000000250a".hexToByteArray()
    val result = uartRxParser(exRX)
    println(result)
}