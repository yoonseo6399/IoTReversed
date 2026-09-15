package io.github.yoonseo6399.raspi

import io.github.yoonseo6399.communication.FetchException
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlin.test.*

class HttpGatewayTest {
    private val token = "test-http-token-with-at-least-32-characters"

    private class Devices : DeviceApi {
        val entries = mutableMapOf("my-room" to DeviceSnapshot("my-room", "내방", "online",
            listOf(ModuleState("lamp", 1, false)), "2026-09-12T14:00:00Z"))
        var writes = 0
        var reads = 0
        var failure: Throwable? = null

        /** Returns the mutable registry used to verify route synchronization. */
        override suspend fun devices() = entries.values.toList()

        /** Simulates a fresh read and rejects a device removed from the live registry. */
        override suspend fun status(id: String, fresh: Boolean): DeviceSnapshot {
            failure?.let { throw it }
            if (fresh) reads++
            return entries[id] ?: throw DeviceApiException(404, "Unknown device")
        }

        override suspend fun power(id: String): PowerResponse {
            status(id, true).module(ModuleType.LAMP, 1)
            return PowerResponse(id, 55, "0000000000550000", "2026-09-16T00:00:00Z")
        }

        /** Simulates one acknowledged output change without any BLE hardware. */
        override suspend fun set(id: String, type: ModuleType, number: Int, on: Boolean): StateResponse {
            val snapshot = status(id, false)
            snapshot.module(type, number)
            writes++
            entries[id] = snapshot.copy(modules = listOf(ModuleState(type.topicSegment, number, on)))
            return entries.getValue(id).module(type, number, true)
        }
    }

    /** Requires bearer authentication before exposing any registry or state data. */
    @Test fun authenticationAndReadOnlyStatus() = testApplication {
        val api = Devices()
        application { deviceRoutes(api, token) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/devices").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/devices") { header("Authorization", token) }.status)
        val response = client.get("/v1/devices/my-room/lamps/1/state?fresh=true") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(hubJson.decodeFromString<StateResponse>(response.bodyAsText()).on)
        assertEquals(1, api.reads)
        assertEquals(0, api.writes)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test fun powerDiagnosticIsAuthenticatedFreshAndReadOnly() = testApplication {
        val api = Devices()
        application { deviceRoutes(api, token) }
        val path = "/v1/devices/my-room/power"
        assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        assertEquals(0, api.reads)
        val response = client.get(path) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        val power = hubJson.decodeFromString<PowerResponse>(response.bodyAsText())
        assertEquals(55, power.watts)
        assertEquals("0000000000550000", power.rawPayloadHex)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(1, api.reads)
        assertEquals(0, api.writes)
        api.failure = FetchException.Timeout()
        assertEquals(HttpStatusCode.GatewayTimeout, client.get(path) { bearerAuth(token) }.status)
        api.failure = null
        api.entries["my-room"] = api.entries.getValue("my-room").copy(availability = "panicked")
        assertEquals(HttpStatusCode.Conflict, client.get(path) { bearerAuth(token) }.status)
    }

    /** Confirms room additions/removals are reflected without rebuilding the HTTP routing table. */
    @Test fun registryChangesAndErrorMapping() = testApplication {
        val api = Devices()
        application { deviceRoutes(api, token) }
        api.entries["second-room"] = api.entries.getValue("my-room").copy(id = "second-room")
        assertEquals(HttpStatusCode.OK, client.get("/v1/devices/second-room") { bearerAuth(token) }.status)
        api.entries.remove("second-room")
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/devices/second-room") { bearerAuth(token) }.status)
        api.entries["my-room"] = api.entries.getValue("my-room").copy(availability = "panicked")
        assertEquals(HttpStatusCode.Conflict, client.get("/v1/devices/my-room/lamps/1/state") { bearerAuth(token) }.status)
        api.failure = FetchException.Timeout()
        assertEquals(HttpStatusCode.GatewayTimeout, client.get("/v1/devices/my-room/lamps/1/state?fresh=true") { bearerAuth(token) }.status)
    }

    /** Retries one command once, rejects request-ID reuse for another command, and validates JSON. */
    @Test fun idempotentCommandsAndInvalidBodies() = testApplication {
        val api = Devices()
        application { deviceRoutes(api, token) }
        repeat(2) {
            val response = client.post("/v1/devices/my-room/lamps/1/state") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody("""{"on":true,"requestId":"shortcut-1"}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(hubJson.decodeFromString<StateResponse>(response.bodyAsText()).acknowledged)
        }
        assertEquals(1, api.writes)
        assertEquals(HttpStatusCode.Conflict, client.post("/v1/devices/my-room/lamps/1/state") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"on":false,"requestId":"shortcut-1"}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/devices/my-room/lamps/1/state") {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"on":"false"}""")
        }.status)
        assertEquals(1, api.writes)
    }
}
