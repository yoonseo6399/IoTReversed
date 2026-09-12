package io.github.yoonseo6399.raspi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

data class MqttEnvelope(val topic: String, val payload: String, val retained: Boolean)

interface MqttPublisher {
    suspend fun publish(topic: String, payload: String, retained: Boolean = false)
}

class MqttGateway(
    private val configuration: MqttConfiguration,
    private val scope: CoroutineScope,
    private val topicRoot: String = "iot-hub"
) : AutoCloseable, MqttPublisher {
    private val subscriptions = linkedSetOf<String>()
    private val subscriptionMutex = Mutex()
    private val _messages = MutableSharedFlow<MqttEnvelope>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val client = MqttAsyncClient(
        configuration.brokerUri,
        configuration.clientId,
        MemoryPersistence()
    )

    val messages: SharedFlow<MqttEnvelope> = _messages

    init {
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                scope.launch {
                    restoreSubscriptions()
                    publish("$topicRoot/availability", "online", retained = true)
                }
            }

            override fun connectionLost(cause: Throwable?) = Unit

            override fun messageArrived(topic: String, message: MqttMessage) {
                _messages.tryEmit(MqttEnvelope(topic, message.payload.decodeToString(), message.isRetained))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken) = Unit
        })
    }

    suspend fun start() {
        withContext(Dispatchers.IO) {
            if (!client.isConnected) {
                client.connect(connectOptions()).waitForCompletion()
            }
        }
    }

    suspend fun subscribe(topicFilter: String) {
        val added = subscriptionMutex.withLock { subscriptions.add(topicFilter) }
        if (added && client.isConnected) {
            subscribeNow(topicFilter)
        }
    }

    override suspend fun publish(topic: String, payload: String, retained: Boolean) {
        check(client.isConnected) { "MQTT client is not connected." }
        withContext(Dispatchers.IO) {
            val message = MqttMessage(payload.encodeToByteArray()).apply {
                qos = 1
                isRetained = retained
            }
            client.publish(topic, message).waitForCompletion()
        }
    }

    override fun close() {
        runCatching {
            if (client.isConnected) {
                client.disconnect().waitForCompletion()
            }
            client.close()
        }
    }

    private fun connectOptions() = MqttConnectOptions().apply {
        isAutomaticReconnect = true
        isCleanSession = true
        setWill("$topicRoot/availability", "offline".encodeToByteArray(), 1, true)
        userName = configuration.username
        password = configuration.password?.toCharArray()
    }

    private suspend fun restoreSubscriptions() {
        val filters = subscriptionMutex.withLock { subscriptions.toList() }
        for (filter in filters) {
            subscribeNow(filter)
        }
    }

    private suspend fun subscribeNow(topicFilter: String) {
        withContext(Dispatchers.IO) {
            client.subscribe(topicFilter, 1).waitForCompletion()
        }
    }
}
