# APK protocol findings and power diagnostic

## Scope and evidence

Evidence is the local decompilation under
`reverse-apk/app/src/main/java/com/iclio/iotswitch2/` (not a firmware specification).
The sample tests are **synthetic vectors**, not hardware captures. No Raspberry Pi
was deployed to or measured during this work.

`DeviceControlActivity` has two distinct wire formats:

- `MakeTxFramePass` (line 1859), `rxAnalyzerPass` (774): binary UART,
  TX `7e 20 0f cmd len payload xor sum+xor`, RX `7e 10 0f ...`.
  The existing Kotlin `Packet` / `UartFrameDecoder` support this format.
- `MakeTxFrame` (2119), `rxAnalyzer` (1391): `7a 31 cmd len` followed by
  character data. `Cmd2Command` catalogs this namespace separately; it does
  **not** implement the ASCII transport or permit sending it as a binary Packet.

Some CMD2-named extensions (temperature, air, fan, capabilities, setup) explicitly
use the binary pass-through path too. They remain in `Command` where supported.
A CMD2 name alone does not establish the wire format or payload encoding.

| Byte | Binary pass-through | ASCII CMD2 |
| --- | --- | --- |
| 0x33 | Consumer-power refresh | — |
| 0x41 | Lamp status | Lamp control |
| 0x42 | Outlet status | Outlet control |
| 0x43 | Outlet power payload | Power refresh |
| 0x44 | Outlet cutoff status | Device status request |
| 0x45 | Consumer-power status | Set-info request |
| 0x50–0x54 | Not established in binary handlers | Lamp/outlet/outlet-power/cutoff/power status |

The old binary `Lamp.DetailState`, `Conc.DetailState`, and `Power.DetailState`
entries were removed in favor of the explicitly separate ASCII catalog.
`Mode.Out` was mislabeled: byte 63 is now `Mode.Watch`, the clock/version
exchange (`CMD_REQ_SET_WATCH`). `Air.ControlMode` is now
`Air.ControlTemperature`: the APK sends `[0, BCD target]` at byte 104.
Command equality now has a matching hash code for transaction maps.

## Consumer power (live watts)

`CommThreadPass` initializes `cArr[0] = 1` (2237) and its `txDcommReqWatt`
branch (2294) sends **0x33 [01]**. The receive path clears the request flag on
0x33 (830), without acknowledging that receipt. The later **0x45** status
handler (1072) reads bytes **4 and 5 as four packed-decimal digits**, then ACKs
0x45 with `[01]`. `FragmentControlPower.powerDisplay` (68) parses those digits
as decimal and displays watts. There is no `/2` scaling on this path.

Example payload `00 00 00 00 00 55 00 00` means **55 W**; `00 83` at offsets
4–5 means **83 W**, not 131 W. Invalid decimal nibbles and truncated payloads
are rejected. The transaction waits for both the request receipt and status,
subscribes before transmitting, and makes only one attempt with an 8-second
timeout. ACKs remain the responsibility of `DeviceConnection`.

### Read it without HomeKit

Build and run the daemon with its existing device registry, MQTT configuration,
native BLE library, and HTTP bearer-token setup (see `raspi-daemon/README.md`).
On the Pi, with `IOT_HTTP_TOKEN_FILE` pointing to that private token file:

```sh
curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $(cat "$IOT_HTTP_TOKEN_FILE")" \
  http://127.0.0.1:8080/v1/devices/my-room/power
```

Replace `my-room` with a registered device ID and 8080 if the HTTP port differs.
The GET always reads fresh power, and returns e.g.:

```json
{"id":"my-room","watts":55,"rawPayloadHex":"0000000000550000","observedAt":"2026-09-16T00:00:00Z","source":"legacy-0x45-bcd"}
```

It shares the controller lock and panic guard with status/control requests;
unknown devices return 404, halted/panicked devices 409, timeouts 504, malformed
power responses 503. A disconnected device can undergo the normal initialization
sequence first. It does not change lamp/outlet outputs. Power is opt-in and is
not added to background polling or Homebridge characteristics.

For a Kotlin caller, use `device.connection.readConsumerPower()`. Homebridge or
other HTTP clients can use the endpoint without interpreting BLE payloads.

### Hardware validation still needed

Read several samples and compare with the wall-pad display at the same time.
The user's room normally reports **0–55 W**, with peaks a little above **80 W**.
This is a sanity check, not a clamp or a general device limit. Keep raw payloads
and timestamps for unusually large values; check BCD versus binary, field offsets,
scaling, or whether a different firmware reports cumulative energy. Do not
silently normalize values to fit the expected range. BLE RX logs already include
raw payloads when a malformed reading cannot be returned successfully.

## Other implemented binary helpers

| Operation | APK evidence | Implementation |
| --- | --- | --- |
| Additional devices 0x70 | `rxAnalyzerPass` 864–901; flags at 2/3/4, optional model at 6 | `readCapabilities`, `DeviceCapabilities` |
| Temperature request/status 0x61/0x65 | 1099–1143; current/target BCD at 2/4, half-degree flags 1/3, away 5, power 6 | `readTemperature`, `TemperatureStatus` |
| Temperature set 0x63 | `CommThreadPass` 2379–2390 | `temperatureTargetPacket`: half-degree flag + BCD target |
| Air request/status 0x66/0x6b | 1204–1230; integer BCD at 2/4, power 5, speed 6 | `readAir`, `AirStatus` |
| Fan request/status 0x71/0x75 | 1275–1295; power 1, speed 2, BCD minutes 3 | `readFan`, `FanStatus` |
| Fan timer 0x76 | 2426–2434; `FragmentControlFan` 46–72 (0/30/60/90 minutes) | `fanTimerPacket` |
| Cumulative 0x7d page | 1298–1344 | `EnergyPage`: six unsigned big-endian counters + page 0–3 |

Temperature/air error responses are distinguished from valid temperatures and
carry the APK's ASCII error code from offsets 2–5. Fan nonzero status is also
rejected; its error-code interpretation is not established by the APK. Target
packet builders validate encoding, not equipment-specific operating limits.
Use capability discovery and verify device limits before integrating controls.
These helpers are not automatically exposed as HomeKit accessories.

## Uncertain or incomplete areas

- **Outlet 0x43:** the APK ACKs but does not decode its payload. Existing
  `count + big-endian uint16 / 2` decoding remains a firmware inference. It now
  respects the declared unsigned count, ignores trailing padding, and rejects
  truncated pairs. Do not treat it as proof of live per-outlet watts.
- **0x7d:** APK labels the final sum cumulative. Counter units and time interval
  are unproven. The parser preserves counters and page index. There is no
  automatic multi-page reader: the ordinary transaction map is keyed by command
  and cannot represent four pages with the same byte safely.
- **Dimming and gas lock:** no concrete opcode or payload surfaced in the
  decompiled application sources. No guessed control commands were added.
- **Wall pad/weather:** `FragmenSetupSwitch` line 44 lists wall-pad vendors
  (Hyundai, Seoul, Kocom, Commax, other). Setup byte 4 stores that selection
  (`DeviceControlActivity` 1358/2284). This establishes a wall-pad configuration
  field, not weather data. Firmware or wall-pad bus captures are needed.
- **ASCII CMD2:** catalog only. Status 0x54 reads ASCII digits 8–11 (1640), which
  must not be confused with binary 0x45 offsets 4–5. Add a separate transport
  only after confirming which devices use it.
- ETC/function/dummy/test constants in the catalog are not proof of usable
  operations. No automatic probes or configuration writes are performed.
- The existing outlet-control checksum exception is preserved; it needs captured
  firmware evidence before changing it.

## Local verification

With JDK 21 installed, from the repository root:

```sh
bash ./gradlew :raspi-daemon:test :raspi-daemon:installDist --offline
# Narrow protocol/API checks:
bash ./gradlew :raspi-daemon:test --tests '*ApkProtocolTest' --tests '*HttpGatewayTest' --offline
```

Remove `--offline` only if dependencies have not been cached. Tests cover command
collisions, BCD decoding, malformed/truncated payloads, unsigned counters, control
encoding, fragmented synthetic frames, immediate replies, no-retry timeouts,
HTTP authentication/errors, and controller panic/cleanup without replay.
