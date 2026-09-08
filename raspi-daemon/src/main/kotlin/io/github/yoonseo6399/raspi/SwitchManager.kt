package io.github.yoonseo6399.raspi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SwitchManager(
    private val mqtt: MqttGateway,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val controllers = mutableMapOf<String, BluetoothSwitchController>()

    suspend fun reconcile(configuration: HubConfiguration) {
        val toStop = mutableListOf<BluetoothSwitchController>()
        val toStart = mutableListOf<BluetoothSwitchController>()
        mutex.withLock {
            val desired = configuration.devices.associateBy(SwitchConfiguration::id)
            controllers.entries.removeIf { (id, controller) ->
                val replacement = desired[id]
                if (replacement == controller.configuration) {
                    false
                } else {
                    toStop += controller
                    true
                }
            }
            desired.values.filter { it.id !in controllers }.forEach { device ->
                BluetoothSwitchController(
                    configuration = device,
                    topicRoot = configuration.topicRoot,
                    defaultPollIntervalSeconds = configuration.defaultPollIntervalSeconds,
                    mqtt = mqtt,
                    scope = scope
                ).also {
                    controllers[device.id] = it
                    toStart += it
                }
            }
        }
        for (controller in toStop) {
            controller.stop()
        }
        for (controller in toStart) {
            controller.start()
        }
    }

    suspend fun handleSetCommand(topicRoot: String, topic: String, payload: String): Boolean {
        val prefix = "${topicRoot.trimEnd('/')}/"
        if (!topic.startsWith(prefix)) {
            return false
        }
        val relativeTopic = topic.removePrefix(prefix)
        val segments = relativeTopic.split('/')
        if (segments.size != 4 || segments[3] != "set") {
            return false
        }
        val moduleType = ModuleType.fromTopicSegment(segments[1]) ?: return false
        val number = segments[2].toIntOrNull() ?: return false
        val state = payload.toSwitchState() ?: return false
        val controller = mutex.withLock { controllers[segments[0]] } ?: return false
        controller.setState(moduleType, number, state)
        return true
    }

    suspend fun stop() {
        val existing = mutex.withLock {
            controllers.values.toList().also { controllers.clear() }
        }
        for (controller in existing) {
            controller.stop()
        }
    }
}

fun String.toSwitchState(): Boolean? = when (trim().uppercase()) {
    "ON", "1", "TRUE" -> true
    "OFF", "0", "FALSE" -> false
    else -> null
}
