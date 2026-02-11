package io.github.yoonseo6399

import io.github.yoonseo6399.communication.connect
import io.github.yoonseo6399.communication.register
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

val uuid = "0ed6f69b-ac5c-1e18-106e-837e2afd4bee"
val kitchin = "cca91665-c55d-2a4f-4fbc-b82a6875af20"
fun Boolean.toByte(): Byte = if (this) 1 else 0
@OptIn(ExperimentalStdlibApi::class)
fun main() {

    runBlocking {
        //val a = register().await()
        //println(a?.identifier)
        //return@runBlocking
        val d = connect(kitchin) ?: return@runBlocking

        val r = try {
            val status = d.requestAllStatus()
            val lampStatFlip = (status?.lampStatus[0]?.not())?.toByte()
            status
//            lampStatFlip?.let {
//                d.sendPacket(Packet.create(Command.Lamp.Control,1, it))
//            }
            //withTimeout(50000) { d.sendPacket(Packet.create(Command.Conc.Control,3)) }
            //withTimeout(5000) { d.requestInfo(Packet.create(Command.Lamp.Control,1,0)) }
        } catch (e : TimeoutCancellationException) { null }
        println(r)
        println("remainder")
        d.peripheral.scope.launch {
            d.packets.collect { println(it) }
        }
        delay(500)
        d.peripheral.close()
        d.peripheral.disconnect()
    }
}
//7e200f3102010162440000000000000000000000
//7e200f3102010162440000000000000000000000
/**[3, 1, 1, 1, 0, 1, 0, 1, 0]
[3, 0, 0, 0, 163, 0, 5, 0, 67]
[3, 0, 0, 0, 0, 0, 0, 0, 0]**/