package io.github.yoonseo6399.iotModules

import io.github.yoonseo6399.communication.Command
import io.github.yoonseo6399.communication.DeviceConnection
import io.github.yoonseo6399.communication.FetchException
import io.github.yoonseo6399.communication.Packet
import io.github.yoonseo6399.toByte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class Lamp(connection: DeviceConnection, override val number: Int, isOn: Boolean) : IoTModule(connection){
    private val _stateFlow = MutableStateFlow(isOn)
    val isOn = _stateFlow.asStateFlow()
    init {
        connection.scope.launch {
            connection.packets.filter { it.cmd == Command.Lamp.State }.collect { _stateFlow.value = (it.payload[number] == 1.toByte());println("lamp Change detected. $number#${(it.payload[number] == 1.toByte())}") }
        }
    }
    suspend fun setState(state: Boolean) : Boolean{
        //IDK whether should I check the state
        try {
            connection.requestInfo(Packet.create(Command.Lamp.Control,number.toByte(),state.toByte()))
                .fetch(Command.Lamp.Control, ack = false)
                .execute()
        } catch (e : FetchException) {
        println("set state failed : "+e.message)
        return false
    }

    _stateFlow.value = state
        return true
    }
    suspend fun flipState()=
        setState(!isOn.value)

}