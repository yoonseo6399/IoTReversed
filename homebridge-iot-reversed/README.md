# Homebridge IoTReversed

Local dynamic platform for the Raspberry Pi BLE MQTT daemon. It creates a stable HomeKit accessory for each discovered lamp/outlet and reconciles removals using the retained device registry. Discovery and state can arrive in any order. Cached accessories are restored after Homebridge restart and remain unavailable until MQTT reports the daemon, device and state.

## Install on the Pi

With the plugin copied to `/opt/homebridge-iot-reversed`, install it into the existing Homebridge user storage:

```bash
cd /var/lib/homebridge
sudo env PATH=/opt/homebridge/bin:/usr/bin:/bin /opt/homebridge/bin/npm install --ignore-scripts /opt/homebridge-iot-reversed
sudo /opt/homebridge/bin/node /path/to/IoTReversed/raspi-daemon/scripts/configure-homebridge.cjs
sudo systemctl restart homebridge
```

The configuration helper backs up `/var/lib/homebridge/config.json`, preserves existing platforms, and adds `IoTReversed`. It copies only the MQTT connection settings from `/etc/iot-reversed/devices.json` to a Homebridge-readable `iot-mqtt.json` with mode `0600`. It does not print passwords or modify the daemon configuration. If broker credentials change, rerun the helper and restart Homebridge.

```json
{
  "platform": "IoTReversed",
  "name": "IoTReversed",
  "topicRoot": "iot-hub",
  "mqttConfigPath": "/var/lib/homebridge/iot-mqtt.json"
}
```

## Register a new room

Send non-retained `{"room":"침실","id":"bedroom","requestId":"bedroom-001"}` to `iot-hub/registry/devices/register` while the intended device is in registration mode. Subscribe to `iot-hub/registry/devices/register/result` for progress. After BLE initialization succeeds, its lamp/outlet accessories appear automatically in this Homebridge bridge. If the bridge has not been added to Apple Home yet, add it using Homebridge's existing pairing QR code.

The registration room becomes the accessory display name. Apple Home's room assignment is not changed by MQTT; assign accessories to rooms in the Home app.

## Availability and safety

`panicked` means repeated protocol packets were detected; HomeKit reads and writes return a communication fault. `offline` and daemon disconnection also make accessories unavailable. HomeKit reads consume MQTT status only. Only explicit HomeKit writes publish control commands, without retention or offline queuing. MQTT publication acknowledges transport, not physical switching; authoritative state arrives from the daemon after the BLE response.

## Tests

`npm ci && npm test` checks retained-message ordering, stable UUIDs, removal, stale discovery and read/write blocking for panic. These tests use a fake Homebridge API and do not connect to a physical switch.

The implementation uses Homebridge's [dynamic platform API](https://developers.homebridge.io/homebridge/interfaces/DynamicPlatformPlugin.html) and keeps [MQTT.js](https://github.com/mqttjs/MQTT.js) transport in `mqtt-gateway.js`.
