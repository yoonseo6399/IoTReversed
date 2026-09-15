package io.github.yoonseo6399.raspi

import com.juul.kable.NotConnectedException
import io.github.yoonseo6399.communication.FetchException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ApiError(val error: String)

class HttpGateway(
    private val devices: DeviceApi, private val token: String, private val port: Int,
    private val registration: DeviceRegistration? = null, private val topicRoot: String = "iot-hub"
) {
    private val server = embeddedServer(CIO, host = "127.0.0.1", port = port) { deviceRoutes(devices, token, registration, topicRoot) }

    /** Starts a loopback-only API for Tailscale Serve; every endpoint requires a bearer token. */
    fun start() {
        require(token.length >= 32) { "HTTP token must contain at least 32 characters" }
        server.start(wait = false)
        println("HTTP API listening on 127.0.0.1:$port")
    }

    /** Lets active requests finish during orderly daemon shutdown. */
    fun stop() = server.stop(1000, 60000)

    companion object {
        /** Enables HTTP only when a private token file is explicitly configured. */
        fun configured(devices: DeviceApi, registration: DeviceRegistration? = null, topicRoot: String = "iot-hub"): HttpGateway? {
            val tokenFile = System.getenv("IOT_HTTP_TOKEN_FILE") ?: return null
            val port = System.getenv("IOT_HTTP_PORT")?.toInt() ?: 8080
            require(port in 1024..65535) { "IOT_HTTP_PORT must be between 1024 and 65535" }
            return HttpGateway(devices, Files.readString(Path.of(tokenFile)).trim(), port, registration, topicRoot)
        }
    }
}

class StateCommands {
    private data class Command(val id: String, val type: ModuleType, val number: Int, val on: Boolean)
    private data class Completed(val command: Command, val result: Result<StateResponse>, val completedAt: Long)
    private val mutex = Mutex()
    private val completed = linkedMapOf<String, Completed>()

    /** Deduplicates retries and keeps client disconnects from interrupting an ACK-driven control exchange. */
    suspend fun execute(api: DeviceApi, id: String, type: ModuleType, number: Int, request: StateRequest): StateResponse = mutex.withLock {
        request.requestId?.let { require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid requestId" } }
        val now = System.currentTimeMillis()
        completed.entries.removeIf { now - it.value.completedAt > 600000 }
        val command = Command(id, type, number, request.on)
        request.requestId?.let { key ->
            completed[key]?.let {
                if (it.command != command) throw DeviceApiException(409, "requestId was already used for another command")
                return@withLock it.result.getOrThrow()
            }
        }
        val result = runCatching {
            withContext(NonCancellable) { withTimeout(60.seconds) { api.set(id, type, number, request.on) } }
        }
        request.requestId?.let {
            completed[it] = Completed(command, result, System.currentTimeMillis())
            if (completed.size > 256) completed.remove(completed.keys.first())
        }
        result.getOrThrow()
    }
}

/** Maps transport failures to explicit API errors without exposing implementation stack traces. */
fun httpFailure(error: Throwable): Pair<HttpStatusCode, String> = when (error) {
    is DeviceApiException -> HttpStatusCode.fromValue(error.status) to (error.message ?: "Device error")
    is FetchException.DuplicationOverflow -> HttpStatusCode.Conflict to "Device is panicked"
    is FetchException.Timeout, is TimeoutCancellationException -> HttpStatusCode.GatewayTimeout to "BLE request timed out"
    is NotConnectedException, is FetchException.Disconnected -> HttpStatusCode.ServiceUnavailable to "Device disconnected"
    is FetchException -> HttpStatusCode.ServiceUnavailable to "Protocol exchange stopped; inspect device"
    is IllegalArgumentException, is SerializationException, is BadRequestException -> HttpStatusCode.BadRequest to "Invalid request"
    else -> HttpStatusCode.ServiceUnavailable to "Device request failed; inspect daemon status"
}

/** Exposes stable parameterized routes backed by the current controller registry, never per-device handlers. */
fun Application.deviceRoutes(devices: DeviceApi, token: String, registration: DeviceRegistration? = null, topicRoot: String = "iot-hub") {
    val json = kotlinx.serialization.json.Json { encodeDefaults = true }
    val commands = StateCommands()
    install(ContentNegotiation) { json(json) }
    install(StatusPages) {
        exception<Throwable> { call, error ->
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            val (status, message) = httpFailure(error)
            call.respond(status, ApiError(message))
        }
    }
    intercept(ApplicationCallPipeline.Call) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val supplied = call.request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ") ?: ""
        if (!MessageDigest.isEqual(supplied.encodeToByteArray(), token.encodeToByteArray())) {
            call.response.headers.append(HttpHeaders.WWWAuthenticate, "Bearer")
            call.respond(HttpStatusCode.Unauthorized, ApiError("Bearer token required"))
            finish()
        }
    }
    routing {
        if (registration != null) {
            setOf("/$topicRoot/$REGISTRATION_PATH", "/v1/$REGISTRATION_PATH").forEach { path ->
                post(path) {
                    val result = registration.register(call.receive<RegistrationRequest>())
                    val status = when (result.status) {
                        "registered" -> HttpStatusCode.Created
                        "not_found" -> HttpStatusCode.NotFound
                        "busy", "conflict" -> HttpStatusCode.Conflict
                        "invalid" -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.ServiceUnavailable
                    }
                    call.respond(status, result)
                }
            }
        }
        get("/v1/devices") { call.respond(devices.devices()) }
        get("/v1/devices/{id}") { call.respond(devices.status(call.deviceId(), false)) }
        route("/v1/devices/{id}/{type}/{number}/state") {
            get {
                val type = call.moduleType()
                val number = call.moduleNumber()
                val fresh = when (call.request.queryParameters["fresh"]) {
                    null, "false" -> false
                    "true" -> true
                    else -> throw IllegalArgumentException("fresh must be true or false")
                }
                call.respond(devices.status(call.deviceId(), fresh).module(type, number))
            }
            post {
                val body = call.receive<JsonObject>()
                val on = body["on"] as? JsonPrimitive
                require(on != null && !on.isString && on.booleanOrNull != null) { "on must be a JSON boolean" }
                val request = json.decodeFromJsonElement<StateRequest>(body)
                call.respond(commands.execute(devices, call.deviceId(), call.moduleType(), call.moduleNumber(), request))
            }
        }
    }
}

/** Validates the same device ID grammar as MQTT registration. */
private fun ApplicationCall.deviceId(): String = parameters["id"]!!.also { validateTopicSegment(it, "device id") }

/** Accepts readable plural module paths while retaining MQTT's singular module identifiers internally. */
private fun ApplicationCall.moduleType(): ModuleType = when (parameters["type"]) {
    "lamps" -> ModuleType.LAMP
    "outlets" -> ModuleType.OUTLET
    else -> throw DeviceApiException(404, "Unknown module type")
}

/** Validates module numbers before entering the BLE request path. */
private fun ApplicationCall.moduleNumber(): Int = parameters["number"]?.toIntOrNull()?.takeIf { it > 0 }
    ?: throw IllegalArgumentException("Module number must be positive")
