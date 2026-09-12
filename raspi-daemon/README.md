# Raspberry Pi BLE–MQTT daemon

`raspi-daemon` keeps Kable BLE connections under a coroutine-based controller and bridges each lamp or outlet to MQTT. The root project remains the shared BLE protocol library, so Android-specific code is not included in the Raspberry Pi process.

## Build and run

Build the Linux distribution on the Pi (or build an `aarch64`-compatible distribution in an equivalent environment):

```bash
bash ./gradlew :raspi-daemon:installDist
cp raspi-daemon/config/devices.json.example /etc/iot-reversed/devices.json
raspi-daemon/build/install/raspi-daemon/bin/raspi-daemon --config /etc/iot-reversed/devices.json
```

Use a MAC address such as `E0:C7:39:F7:C8:9D` in `bluetoothIdentifier`. BlueZ object-path identifiers are also accepted; connection setup uses the actual Kable advertisement identifier. Keep the configuration readable only by the service account when it contains an MQTT password. The service account must own the configuration directory as well as the file so atomic registry updates can be saved.

Install `iot-reversed.service` after copying the distribution to `/opt/iot-reversed`, creating the `iotbridge` account, and placing the configuration at `/etc/iot-reversed/devices.json`.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now iot-reversed
```

The service account must be allowed to use the BlueZ system D-Bus service. On Raspberry Pi OS, add it to the distribution's Bluetooth access group if BlueZ access is denied.

### Raspberry Pi OS native BLE library

Kable 0.42.0 publishes its ARM Linux FFI binary from Ubuntu 24.04, which requires glibc 2.38. Raspberry Pi OS Bookworm provides glibc 2.36, so the packaged binary cannot load there. Build the exact same FFI source on the Pi once; this links against the Pi's glibc and does not require an operating-system upgrade.

```bash
sudo apt install -y build-essential pkg-config libdbus-1-dev git curl
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
source "$HOME/.cargo/env"
bash raspi-daemon/scripts/build-kable-ffi.sh "$HOME/libbtleplug_ffi.so"
sudo install -D -m 0755 "$HOME/libbtleplug_ffi.so" /opt/iot-reversed/lib/libbtleplug_ffi.so
sudo systemctl restart iot-reversed
```

The supplied systemd unit sets `KABLE_FFI_LIBRARY` to that path. The launcher resolves it before Kable initializes and passes it to UniFFI/JNA. Confirm the resulting native library is compatible before restarting the service:

```bash
ldd /opt/iot-reversed/lib/libbtleplug_ffi.so
sudo journalctl -u iot-reversed -n 100 --no-pager
```

## MQTT contract

For a device ID `living-room`, lamp 1 uses these retained state and command topics:

| Purpose | Topic | Payload |
| --- | --- | --- |
| Set lamp state | `iot-hub/living-room/lamp/1/set` | `ON`, `OFF`, `1`, `0`, `true`, or `false` |
| Lamp state | `iot-hub/living-room/lamp/1/state` | `ON` or `OFF` |
| Set outlet state | `iot-hub/living-room/outlet/1/set` | Same as lamp set |
| Outlet state | `iot-hub/living-room/outlet/1/state` | `ON` or `OFF` |
| BLE availability | `iot-hub/living-room/availability` | `online`, `offline`, or `panicked` |
| Request fresh status | `iot-hub/living-room/status/get` | Empty payload; never changes outputs |
| Discovered modules | `iot-hub/living-room/discovery` | Retained JSON with `id`, `displayName`, `modules` |
| Daemon availability | `iot-hub/availability` | Retained `online` / `offline`, with MQTT last will |

The daemon polls each connected device at `pollIntervalSeconds`, updates the existing Kable `StateFlow`s from the authoritative BLE status response, and publishes those states. Commands are serialized per BLE connection so a status request cannot consume another command's response.

Executable MQTT requests must not be retained. Retained commands are ignored; clean MQTT sessions prevent commands from being queued while the daemon is offline. Module states, discovery, registry and availability are retained.

### Protocol safety and connection recovery

One adapter mutex serializes registration scanning and device scan/connect. A short settling interval follows native scan cancellation. Every failed candidate is disconnected and closed, releasing native callbacks and module collectors. Notification subscription must finish before the first request is sent.

The receiver owns ACKs and acknowledges duplicates before exposing packets to callers. `PacketFetchBuilder` subscribes before transmitting, does not replay previous transactions, serializes requests, and finishes on the final expected packet without needing another packet. Full-status requests are not automatically resent on timeout: an incomplete exchange stops the controller pending inspection. Ordinary connection failures are limited to three consecutive failures per controller run.

Logs include device identifier, connection phase, RX command, payload hex, consecutive repetition count and ACK completion. Six identical packets in a row with less than two seconds between packets trigger a panic. The conservative transaction duplicate limit also triggers a panic if too many duplicates prevent completing a response. Ordinary two-packet retransmissions that progress through the status sequence are accepted.

A panic publishes retained `panicked` to the device availability topic and blocks further reads, writes and reconnects. Shutdown preserves this state instead of overwriting it with `offline`. Inspect the physical device and reboot it if necessary before explicitly restarting the daemon. The in-process panic latch is reset by daemon restart; do not restart automatically to clear a suspected device loop.

### Register a pairing device using its room name

Subscribe to `iot-hub/registry/devices/register/result`, then send a non-retained JSON request:

```json
{"room":"침실","requestId":"bedroom-001","id":"bedroom"}
```

Publish this to `iot-hub/registry/devices/register`. `id` is optional; omission generates a stable `switch-<mac>` ID. `room` and `requestId` are required. Use a new request ID for each intentional attempt. Up to 100 completed request IDs are remembered during a daemon run to avoid repeating a QoS redelivery.

The Android `IotHub/RegisterActivity.kt` registration flow finds names beginning with `Clio_UART.` or `Clio_UART [Clio_UART.]` and stores the identifier with a room name; it does not send a separate BLE pairing or room-programming command. The daemon follows the same procedure, excluding already registered MACs. Put only the intended new device in registration mode. Scanning expires after 15 seconds once the adapter is available.

Results include the request ID and `waiting`, `registered`, `not_found`, `busy` or `error`. `registered` means the room/MAC was persisted; successful BLE initialization is separately reported by `online` and the module discovery topic. MQTT credentials remain intact when `devices.json` is atomically updated. Duplicate MACs and duplicate registration IDs cannot overwrite another device.

### Automatic Homebridge accessories

The bundled [homebridge-iot-reversed](../homebridge-iot-reversed/README.md) dynamic platform creates lamp and outlet accessories from registry plus BLE discovery, and removes them when the registry entry is removed. No per-device Homebridge JSON edit or restart is required. Room text becomes the accessory name; Apple Home room assignment is still managed in the Home app.

The plugin combines daemon and device availability. `offline` and `panicked` make characteristics return HomeKit communication errors, and block HomeKit control publishing. Panic is additionally logged explicitly in Homebridge.

### Read-only hardware verification

After installing the plugin's Node dependencies, run on the Pi:

```bash
sudo /opt/homebridge/bin/node raspi-daemon/scripts/verify-mqtt.cjs my-room
```

The script loads credentials directly from `/etc/iot-reversed/devices.json`, subscribes to status, publishes only `status/get`, and requires a new non-retained `OFF` response. It never publishes to a `set` topic. `scripts/watch-attempt.sh` starts an already installed service and observes one invocation for 65 seconds; a `NotConnectedException` or panic stops the service immediately so a failed debugging attempt cannot silently retry.

## Homebridge mqttthing example

Install and configure `homebridge-mqttthing` against the same broker. One lamp accessory can use:

```json
{
  "accessory": "mqttthing",
  "type": "lightbulb",
  "name": "Living room lamp 1",
  "url": "mqtt://127.0.0.1:1883",
  "qos": 1,
  "onValue": "ON",
  "offValue": "OFF",
  "topics": {
    "getOn": "iot-hub/living-room/lamp/1/state",
    "setOn": "iot-hub/living-room/lamp/1/set"
  }
}
```

Create equivalent Homebridge accessories for the lamp and outlet module numbers reported by the switch. Retained state lets Homebridge recover the current visible state when it restarts.

## Add or remove a device at runtime

The daemon subscribes to registry topics, persists successful changes to `devices.json`, and reconciles controllers without restarting. Device IDs are restricted to letters, digits, `_`, and `-` so they are safe MQTT topic segments.

```bash
mosquitto_pub -t iot-hub/registry/devices/upsert -m '{"id":"bedroom","bluetoothIdentifier":"00000000-0000-0000-0000-000000000000","displayName":"Bedroom switch"}'
mosquitto_pub -t iot-hub/registry/devices/remove -m 'bedroom'
```

The current registry is retained at `iot-hub/registry/devices`. Adding a device starts its BLE monitor immediately; removing it disconnects its controller and publishes `offline`.
