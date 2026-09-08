package io.github.yoonseo6399

import com.juul.kable.Filter
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.toIdentifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.runBlocking

val uuid = "0ed6f69b-ac5c-1e18-106e-837e2afd4bee"
val kitchin = "cca91665-c55d-2a4f-4fbc-b82a6875af20"
fun Boolean.toByte(): Byte = if (this) 1 else 0
@OptIn(ExperimentalStdlibApi::class)
fun main() {
    println("started")
    runBlocking {
        //val a = register().await()
        //println(a?.identifier)
        //return@runBlocking
        val add = Scanner {
            }.advertisements.first { it.identifier.toString() == uuid }
        val switch = IoTSwitch.connect(uuid.toIdentifier(),{ Peripheral(add)}) ?: return@runBlocking
        switch.lamp.first().flipState()
        print("done")
//
        switch.outlet.first().flipState()
//
        //switch.printUnhandled()
        //delay(500)
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