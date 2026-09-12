package io.github.yoonseo6399.raspi

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.*

class RegistrationTest {
    /** Verifies Korean room registration, credential preservation and QoS duplicate idempotence. */
    @Test fun persistsRoomAndDeduplicatesRequest() = runTest {
        val directory = Files.createTempDirectory("iot-registry-test")
        val path = directory.resolve("devices.json")
        try {
            val original = HubConfiguration(mqtt = MqttConfiguration(password = "test-only-password"))
            Files.writeString(path, hubJson.encodeToString(original))
            val registry = DeviceRegistry(path)
            registry.load()
            val replies = mutableListOf<RegistrationResult>()
            val mqtt = object : MqttPublisher {
                /** Captures registry results without connecting to a broker. */
                override suspend fun publish(topic: String, payload: String, retained: Boolean) {
                    replies += hubJson.decodeFromString<RegistrationResult>(payload)
                }
            }
            var scans = 0
            val registration = DeviceRegistration(registry, BluetoothDiscovery(), mqtt) {
                scans++
                "AA:BB:CC:DD:EE:FF"
            }
            val request = RegistrationRequest("침실", "request-1")
            registration.register(request)
            registration.register(request)
            assertEquals(1, scans)
            assertEquals(listOf("waiting", "registered", "registered"), replies.map { it.status })
            val persisted = hubJson.decodeFromString<HubConfiguration>(Files.readString(path))
            assertEquals(original.mqtt, persisted.mqtt)
            assertEquals("침실", persisted.devices.single().displayName)
            assertEquals("switch-aabbccddeeff", persisted.devices.single().id)
            assertFailsWith<IllegalArgumentException> {
                registry.register(SwitchConfiguration("another-room", "aa:bb:cc:dd:ee:ff"))
            }
            assertEquals(1, registry.configuration.value.devices.size)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }
}
