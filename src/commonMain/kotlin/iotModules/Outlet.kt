package io.github.yoonseo6399.iotModules

import io.github.yoonseo6399.communication.Command
import io.github.yoonseo6399.communication.DeviceConnection
import io.github.yoonseo6399.communication.FetchException
import io.github.yoonseo6399.communication.Packet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class Outlet(connection: DeviceConnection, override val number: Int, state: Boolean) : IoTModule(connection){
    private val _stateFlow = MutableStateFlow(state)
    val powerFlowState = _stateFlow.asStateFlow()

    suspend fun setState(state: Boolean) : Boolean{
        //IDK whether should I check the state
        val packet = if(state) Packet.Companion.create(Command.Conc.Control,number.toByte())
            else Packet.create(Command.Conc.Control,number.toByte(),0)
        try {
            connection.requestInfo(packet)
                .fetch(Command.Conc.Control, ack = false)
                .execute()
        }catch (e : FetchException) {
            println("set state failed : "+e.message)
            return false
        }

        _stateFlow.value = state
        return true
    }
    suspend fun flipState() : Boolean{
        return setState(!powerFlowState.value)
    }
}