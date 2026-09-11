# Raspberry Pi BLE–MQTT daemon

`raspi-daemon` keeps Kable BLE connections under a coroutine-based controller and bridges each lamp or outlet to MQTT. The root project remains the shared BLE protocol library, so Android-specific code is not included in the Raspberry Pi process.

## Build and run

Build the Linux distribution on the Pi (or build an `aarch64`-compatible distribution in an equivalent environment):

```bash
bash ./gradlew :raspi-daemon:installDist
cp raspi-daemon/config/devices.json.example /etc/iot-reversed/devices.json
raspi-daemon/build/install/raspi-daemon/bin/raspi-daemon --config /etc/iot-reversed/devices.json
```

Use the BLE identifier printed by the existing scanner in `bluetoothIdentifier`. Keep the configuration readable only by the service account when it contains an MQTT password.

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
| BLE availability | `iot-hub/living-room/availability` | `online` or `offline` |

The daemon polls each connected device at `pollIntervalSeconds`, updates the existing Kable `StateFlow`s from the authoritative BLE status response, and publishes those states. Commands are serialized per BLE connection so a status request cannot consume another command's response.

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
