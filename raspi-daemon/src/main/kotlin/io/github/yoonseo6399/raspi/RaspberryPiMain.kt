package io.github.yoonseo6399.raspi

import java.nio.file.Path
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
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
    private lateinit var registration: DeviceRegistration
    private val discovery = BluetoothDiscovery()

    suspend fun start() {
        val configuration = registry.load()
        mqtt = MqttGateway(configuration.mqtt, scope, configuration.topicRoot)
        switchManager = SwitchManager(mqtt, scope, discovery)
        registration = DeviceRegistration(registry, discovery, mqtt)
        mqtt.start()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mqtt.messages.collect { message ->
                if (!message.retained) scope.launch { handleMqttMessage(message) }
            }
        }
        mqtt.subscribe("${configuration.topicRoot}/+/+/+/set")
        mqtt.subscribe("${configuration.topicRoot}/+/status/get")
        mqtt.subscribe("${configuration.topicRoot}/registry/devices/register")
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
    }

    suspend fun stop() {
        if (::mqtt.isInitialized) {
            runCatching { mqtt.publish("${registry.configuration.value.topicRoot}/availability", "offline", retained = true) }
        }
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
        try {
          when (message.topic) {
            "$root/registry/devices/register" -> registration.register(
                hubJson.decodeFromString<RegistrationRequest>(message.payload)
            )
            "$root/registry/devices/upsert" -> {
                registry.upsert(hubJson.decodeFromString<SwitchConfiguration>(message.payload))
            }
            "$root/registry/devices/remove" -> registry.remove(message.payload.trim())
            else -> if (!switchManager.handleGetCommand(root, message.topic)) {
                switchManager.handleSetCommand(root, message.topic, message.payload)
            }
          }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            System.err.println("MQTT command ${message.topic}: ${error.message}")
        }
    }
}

fun main(args: Array<String>) {
    runBlocking {
        configureKableFfiOverride()
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

private fun configureKableFfiOverride() {
    if (System.getProperty("uniffi.component.btleplug_ffi.libraryOverride") != null) {
        return
    }
    val libraryPath = System.getenv("KABLE_FFI_LIBRARY")?.let(Path::of) ?: return
    require(Files.isRegularFile(libraryPath)) {
        "KABLE_FFI_LIBRARY does not point to a regular file: $libraryPath"
    }
    System.setProperty("uniffi.component.btleplug_ffi.libraryOverride", libraryPath.toAbsolutePath().toString())
}

private fun parseConfigurationPath(args: Array<String>): Path = when {
    args.isEmpty() -> Path.of("config", "devices.json")
    args.size == 2 && args[0] == "--config" -> Path.of(args[1])
    else -> error("Usage: IoTReversed [--config /path/to/devices.json]")
}
