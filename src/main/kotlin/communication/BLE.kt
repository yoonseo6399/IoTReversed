package io.github.yoonseo6399.communication

import com.juul.kable.Advertisement
import com.juul.kable.Filter
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.peripheral
import io.github.yoonseo6399.Packet
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

fun ACK(packet: Packet) {

}
fun connect(mac : String) : CoroutineScope{
    val scope = CoroutineScope(Job() + CoroutineName("DeviceConnection : $mac") + Dispatchers.IO)
    Scanner {
        filters {
            match {
                name = Filter.Name.Exact("Clio_UART")
            }
        }
    }
    return
}