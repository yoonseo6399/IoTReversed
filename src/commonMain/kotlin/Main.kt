package io.github.yoonseo6399

import com.juul.kable.toIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

val uuid = "0ed6f69b-ac5c-1e18-106e-837e2afd4bee"
val kitchin = "cca91665-c55d-2a4f-4fbc-b82a6875af20"
fun Boolean.toByte(): Byte = if (this) 1 else 0
@OptIn(ExperimentalStdlibApi::class)
fun main() {

    runBlocking {
        //val a = register().await()
        //println(a?.identifier)
        //return@runBlocking
//        val switch = IoTSwitch.connect(uuid.toIdentifier()) ?: return@runBlocking
//        switch.lamp.first().flipState()
//
//        switch.outlet.first().flipState()
//
//        switch.printUnhandled()
        delay(500)
//        println(r)
//        println("remainder")
//        d.peripheral.scope.launch {
//            d.packets.collect { println(it) }
//        }
//        delay(500)
//        d.peripheral.close()
//        d.peripheral.disconnect()
    }
}
//7e200f3102010162440000000000000000000000
//7e200f3102010162440000000000000000000000
/**[3, 1, 1, 1, 0, 1, 0, 1, 0]
[3, 0, 0, 0, 163, 0, 5, 0, 67]
[3, 0, 0, 0, 0, 0, 0, 0, 0]**/