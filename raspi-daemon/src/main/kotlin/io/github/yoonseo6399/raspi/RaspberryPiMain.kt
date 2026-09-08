package io.github.yoonseo6399.raspi

import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

class RaspberryPiHub(private val configurationPath: Path) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registry = DeviceRegistry(configurationPath)
    private lateinit var mqtt: MqttGateway
    private lateinit var switchManager: SwitchManager

    suspend fun start() {
        val configuration = registry.load()
        mqtt = MqttGateway(configuration.mqtt, scope)
        switchManager = SwitchManager(mqtt, scope)
        mqtt.start()
        mqtt.subscribe("${configuration.topicRoot}/+/+/+/set")
        mqtt.subscribe("${configuration.topicRoot}/registry/devices/upsert")
        mqtt.subscribe("${configuration.topicRoot}/registry/devices/remove")
        scope.launch {
            registry.configuration.collect { updated ->
                switchManager.reconcile(updated)
                mqtt.publish(
                    "${updated.topicRoot}/registry/devices",
                    hubJson.encodeToString(updated.devices),
                    retained = true
                )
            }
        }
        scope.launch {
            mqtt.messages.collect(::handleMqttMessage)
        }
    }

    suspend fun stop() {
        if (::switchManager.isInitialized) {
            switchManager.stop()
        }
        scope.cancel()
        if (::mqtt.isInitialized) {
            mqtt.close()
        }
    }

    private suspend fun handleMqttMessage(message: MqttEnvelope) {
        val configuration = registry.configuration.value
        val root = configuration.topicRoot
        when (message.topic) {
            "$root/registry/devices/upsert" -> {
                registry.upsert(hubJson.decodeFromString<SwitchConfiguration>(message.payload))
            }
            "$root/registry/devices/remove" -> registry.remove(message.payload.trim())
            else -> {
                runCatching {
                    switchManager.handleSetCommand(root, message.topic, message.payload)
                }.onFailure { error ->
                    System.err.println("MQTT command ${message.topic}: ${error.message}")
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    runBlocking {
        val configurationPath = parseConfigurationPath(args)
        val hub = RaspberryPiHub(configurationPath)
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking { hub.stop() }
        })
        try {
            hub.start()
            awaitCancellation()
        } finally {
            hub.stop()
        }
    }
}

private fun parseConfigurationPath(args: Array<String>): Path = when {
    args.isEmpty() -> Path.of("config", "devices.json")
    args.size == 2 && args[0] == "--config" -> Path.of(args[1])
    else -> error("Usage: IoTReversed [--config /path/to/devices.json]")
}
