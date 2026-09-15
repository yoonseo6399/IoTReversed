package io.github.yoonseo6399.raspi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SwitchManager(
    private val mqtt: MqttGateway,
    private val scope: CoroutineScope,
    private val discovery: BluetoothDiscovery
) : DeviceApi {
    private val mutex = Mutex()
    private val controllers = mutableMapOf<String, BluetoothSwitchController>()

    /** Uses the current registry-backed controllers for HTTP discovery and diagnostic status. */
    override suspend fun devices(): List<DeviceSnapshot> = mutex.withLock {
        controllers.values.map { it.snapshot() }.sortedBy { it.id }
    }

    /** Resolves the current device on each request so additions and removals need no route rebuild. */
    private suspend fun controller(id: String): BluetoothSwitchController = mutex.withLock {
        controllers[id] ?: throw DeviceApiException(404, "Unknown device: $id")
    }

    /** Optionally refreshes BLE status while preserving cached diagnostics for offline devices. */
    override suspend fun status(id: String, fresh: Boolean): DeviceSnapshot {
        val controller = controller(id)
        if (fresh) controller.readStatus()
        return controller.snapshot()
    }

    override suspend fun power(id: String): PowerResponse = controller(id).readPower()

    /** Shares the existing controller mutex and ACK protocol with MQTT control requests. */
    override suspend fun set(id: String, type: ModuleType, number: Int, on: Boolean): StateResponse =
        controller(id).setState(type, number, on)

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
                    scope = scope,
                    discovery = discovery
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

    /** Routes an explicit MQTT read request to a fresh, serialized BLE status transaction. */
    suspend fun handleGetCommand(topicRoot: String, topic: String): Boolean {
        val segments = topic.removePrefix("$topicRoot/").split('/')
        if (!topic.startsWith("$topicRoot/") || segments.size != 3 || segments.drop(1) != listOf("status", "get")) return false
        val controller = mutex.withLock { controllers[segments[0]] } ?: return false
        controller.readStatus()
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
