package io.github.yoonseo6399.iotModules

import io.github.yoonseo6399.communication.Command
import io.github.yoonseo6399.communication.Packet
import io.github.yoonseo6399.toByte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class Lamp(context: IoTSwitch, number: Int, isOn: Boolean) : IoTModule(context,number){
    private val _isOn = MutableStateFlow(isOn)
    val isOn = _isOn.asStateFlow()

    suspend fun setState(state: Boolean) : Boolean{
        //IDK whether should I check the state
        try {
            context.state.value.sendPacket(Packet.create(Command.Lamp.Control,number.toByte(),state.toByte()))
            _isOn.value = state
            return true
        } catch (e : IllegalStateException) {
            println("set state failed : " + e.message)
        }
        return false
    }

    override suspend fun updateState(packet: Packet): Boolean {
        if (packet.cmd == Command.Lamp.State) {
            (packet.parse() as? List<Boolean>)?.getOrNull(number-1)?.let {
                _isOn.value = it
                return true
            }
            context.logger?.warn("Lamp received info, but invalid data: $packet")
        }
        return false
    }

    suspend fun flipState()=
        setState(!isOn.value)

}