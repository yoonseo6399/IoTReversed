---
name: iot-rpi-deploy
description: Build, install, validate, or update the IoTReversed Raspberry Pi daemon and its loopback HTTP API while preserving the Pi's private device/MQTT configuration and native BLE library. Use for deployments to the project's Raspberry Pi, not for ordinary local development.
---

# IoTReversed Raspberry Pi deployment

Use `raspi-daemon/scripts/deploy-rpi.sh` from the repository root. Keep deployment mechanics in that script instead of reconstructing SSH commands.

Before applying a deployment:

- Inspect `git status --short`; preserve unrelated changes, especially `.idea` and `devices.json`.
- Run the script without `--apply` to display the target and checks. A user's explicit request to install or deploy authorizes `--apply`; otherwise obtain confirmation before changing the Pi.
- Never print `/etc/iot-reversed/devices.json`, MQTT credentials, or `/etc/iot-reversed/http-token`.

Run:

```bash
bash raspi-daemon/scripts/deploy-rpi.sh --apply
```

Override the target only when requested:

```bash
bash raspi-daemon/scripts/deploy-rpi.sh --host pi@<address> --apply
```

The script builds and tests locally, uploads a JVM distribution, preserves the Pi-built `libbtleplug_ffi.so`, creates the private HTTP token only when absent, installs the systemd unit, and exposes the loopback API with private Tailscale Serve.

After it finishes, report only the result, service status, API health, and relevant newest journal errors. Do not publish MQTT `set` topics or send HTTP `POST` requests during verification. A cached HTTP `GET` is sufficient. If logs show `availability=panicked`, `PROTOCOL_HALTED`, or an endlessly repeated packet during connection, stop the service and end device testing. Do not automatically restart a panicked device.
