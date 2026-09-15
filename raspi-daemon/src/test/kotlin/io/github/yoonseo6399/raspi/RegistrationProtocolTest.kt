package io.github.yoonseo6399.raspi

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.encodeToString
import kotlin.test.*

class RegistrationProtocolTest {
    private val token = "test-registration-token-at-least-32-characters"

    private class Fixture : AutoCloseable {
        val directory = Files.createTempDirectory("registration-protocol-test")
        val path = directory.resolve("devices.json")
        val original = HubConfiguration(mqtt = MqttConfiguration(password = "test-only-secret"), topicRoot = "custom/hub")
        val registry = DeviceRegistry(path)
        val messages = mutableListOf<Pair<String, RegistrationResult>>()
        var brokerOffline = false
        val publisher = object : MqttPublisher {
            /** Captures correlated MQTT results or simulates a disconnected broker. */
            override suspend fun publish(topic: String, payload: String, retained: Boolean) {
                check(!brokerOffline) { "offline" }
                assertFalse(retained)
                messages += topic to hubJson.decodeFromString<RegistrationResult>(payload)
            }
        }
        val devices = object : DeviceApi {
            /** Reads registrations dynamically without starting a physical BLE monitor. */
            override suspend fun devices() = registry.configuration.value.devices.map { DeviceSnapshot(it.id, it.displayName) }
            /** Resolves a newly registered device through the same HTTP route. */
            override suspend fun status(id: String, fresh: Boolean) = devices().first { it.id == id }
            /** Prevents physical-control behavior from entering registration tests. */
            override suspend fun set(id: String, type: ModuleType, number: Int, on: Boolean): StateResponse = error("Unexpected control")
        }

        /** Initializes a temporary configuration with credentials that must survive registration. */
        suspend fun load() {
            Files.writeString(path, hubJson.encodeToString(original))
            registry.load()
        }

        /** Removes only the fixture's files. */
        override fun close() {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    /** Verifies HTTP authentication, shared MQTT deduplication, settings persistence and live route lookup. */
    @Test fun httpAndMqttShareRegistration() = testApplication {
        Fixture().use { f ->
            f.load()
            var scans = 0
            val registration = DeviceRegistration(f.registry, BluetoothDiscovery(), f.publisher) { known ->
                assertTrue(known.isEmpty())
                scans++
                "aa:bb:cc:dd:ee:ff"
            }
            application { deviceRoutes(f.devices, token, registration, "custom/hub") }
            val body = """{"requestId":"new-room-1","id":"bedroom","name":"침실 전등","pollIntervalSeconds":30}"""
            assertEquals(HttpStatusCode.Unauthorized, client.post("/custom/hub/$REGISTRATION_PATH") {
                contentType(ContentType.Application.Json); setBody(body)
            }.status)
            assertEquals(0, scans)
            val response = client.post("/custom/hub/$REGISTRATION_PATH") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(body)
            }
            assertEquals(HttpStatusCode.Created, response.status)
            val registered = hubJson.decodeFromString<RegistrationResult>(response.bodyAsText())
            assertEquals("registered", registered.status)
            assertEquals("침실 전등", registered.device?.displayName)
            assertEquals(30L, registered.device?.pollIntervalSeconds)
            assertFalse(response.bodyAsText().contains("test-only-secret"))
            val mqttRetry = registration.register(RegistrationRequest("침실 전등", "new-room-1", "bedroom", pollIntervalSeconds = 30))
            assertEquals(registered, mqttRetry)
            assertEquals(1, scans)
            assertTrue(f.messages.all { it.first == "custom/hub/$REGISTRATION_PATH/result" })
            assertEquals(listOf("waiting", "registered", "registered"), f.messages.map { it.second.status })
            val persisted = hubJson.decodeFromString<HubConfiguration>(Files.readString(f.path))
            assertEquals(f.original.mqtt, persisted.mqtt)
            assertEquals(registered.device, persisted.devices.single())
            assertEquals(HttpStatusCode.OK, client.get("/v1/devices/bedroom") { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.Conflict, client.post("/v1/$REGISTRATION_PATH") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(body.replace("침실 전등", "다른 전등"))
            }.status)
            assertEquals(HttpStatusCode.BadRequest, client.post("/v1/$REGISTRATION_PATH") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody("""{"requestId":"bad","name":"침실","pollIntervalSeconds":0}""")
            }.status)
            assertEquals(1, scans)
        }
    }

    /** Concurrent registrations report busy, and a missing device does not mutate configuration. */
    @Test fun serializesDiscoveryAndHandlesNoDevice() = runTest {
        Fixture().use { f ->
            f.load()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val registration = DeviceRegistration(f.registry, BluetoothDiscovery(), f.publisher) {
                started.complete(Unit)
                release.await()
                null
            }
            val pending = async { registration.register(RegistrationRequest(name = "one", requestId = "one")) }
            started.await()
            assertEquals("busy", registration.register(RegistrationRequest(name = "two", requestId = "two")).status)
            release.complete(Unit)
            assertEquals("not_found", pending.await().status)
            assertTrue(f.registry.configuration.value.devices.isEmpty())
        }
    }

    /** HTTP can persist a device while MQTT publication is unavailable; duplicate MACs remain protected. */
    @Test fun brokerFailureDoesNotLoseRegistration() = runTest {
        Fixture().use { f ->
            f.load()
            f.brokerOffline = true
            var knownMacs = emptySet<String>()
            val registration = DeviceRegistration(f.registry, BluetoothDiscovery(), f.publisher) { known ->
                knownMacs = known
                "AA:BB:CC:DD:EE:FF"
            }
            assertEquals("registered", registration.register(RegistrationRequest(name = "one", requestId = "one")).status)
            assertEquals("conflict", registration.register(RegistrationRequest(name = "two", requestId = "two")).status)
            assertEquals(setOf("AA:BB:CC:DD:EE:FF"), knownMacs)
            assertEquals(1, f.registry.configuration.value.devices.size)
        }
    }

    /** Caller cancellation releases discovery ownership instead of caching a false not-found result. */
    @Test fun cancellationReleasesRegistrationLock() = runTest {
        Fixture().use { f ->
            f.load()
            var scans = 0
            val registration = DeviceRegistration(f.registry, BluetoothDiscovery(), f.publisher) {
                scans++
                if (scans == 1) awaitCancellation()
                "AA:BB:CC:DD:EE:FF"
            }
            val request = RegistrationRequest(name = "one", requestId = "one")
            assertFailsWith<TimeoutCancellationException> { withTimeout(50) { registration.register(request) } }
            assertEquals("registered", registration.register(request).status)
            assertEquals(2, scans)
        }
    }
}
