# Raspberry Pi validation — 2026-09-12

## Reconnection follow-up — 2026-09-14

Attempt branch: `raspi-reconnect-fix-1`. The previous HTTP deployment remained alive with `availability=offline`, `halted=true`, and `lastError=Timeout: Fetch timeout`. At 13:28:47 Pi local time, no decoded command 52 appeared, although commands 65–68 continued to arrive and were acknowledged. Cleanup ended at 13:28:57. The original notification loss remains undetermined; it is not evidence of a hardware panic.

The controller now validates the cached session before reuse, replaces disconnected/cancelled sessions, and keeps monitoring after ordinary errors with backoff capped at 60 seconds. Only a repeated-packet panic latches recovery off and closes the connection. Request failure cleanup holds the request mutex, preventing an old failure from closing a newer session. Failed control commands are not replayed. Error logs precede cleanup so a cleanup-generated disconnection is distinguishable from the triggering failure.

- 19 local Kotlin tests passed, including five controller tests using fake BLE sessions: dead-session replacement, recovery after more than three connection failures, failed-write recovery without replay, session cancellation/timeout recovery, and panic shutdown without reconnect.
- Installed the tested JVM distribution on the Pi, retaining its native ARM library and private configuration. Previous distribution: `/opt/iot-reversed.previous.20260914T141907-6230`.
- At 15:19:23 Pi local time, the new process encountered a real 10-second notification-subscription timeout and logged `halted=false`, then `reconnect=true` after cleanup.
- At 15:20:06–07, the same process automatically reconnected and completed the full status sequence. PID stayed `6292`; systemd `NRestarts=0`.
- Cached authenticated HTTP GET returned `availability=online`, `halted=false`, `lastError=null`, and lamp 1 ON with a subsequent fresh polling timestamp. Verification sent no output-control commands.
- No panic occurred during this observation. Real mid-command radio disconnection was not deliberately induced; that path is covered by the fake-session tests. This is bounded validation, not proof that notification corruption cannot recur.
- Tailscale Serve was not changed by this follow-up deployment.

The remaining sections describe the earlier deployment, not the current recovery policy.

Attempt branch: `raspi-daemon-fix-1`. Target branch: `raspi-daemon`.

## Baseline and change

The existing Pi daemon's startup journal contained `NotConnectedException: Disconnected`, followed by eventual successful status polling. The exception alone did not establish the underlying radio/BlueZ cause. Code inspection identified failed Peripheral candidates that were not closed, unsynchronized adapter scanning, and notification startup based on fixed delays.

Attempt 1 adds serialized scan/connect, scan cancellation settling, notification subscription readiness, and disposal of failed sessions. The packet transaction fix removes replayed responses and an extra-packet completion dependency while preserving receiver-owned ACKs, including duplicate packets. A repeated-packet panic latches controller requests off and publishes retained `panicked`.

## Verified

- Kotlin protocol and registration tests: 7 passed on the Mac and Raspberry Pi.
- Node Homebridge platform tests: 2 passed on the Mac and Raspberry Pi.
- Native Raspberry Pi `:raspi-daemon:installDist` completed successfully.
- First deployed attempt connected successfully; no `NotConnectedException` or panic during the observed invocation. This is bounded live validation, not proof against every future radio failure.
- Pi journal: 2026-09-11 18:26:14, `link=Connected`; 18:26:15–16, status sequence 52 → 65 → 66 → 67 → 68. Required ACKs completed; occasional duplicates reached repeat=2 then progressed.
- MQTT status-only request to `iot-hub/my-room/status/get` received a new non-retained `iot-hub/my-room/lamp/1/state=OFF`. No verification control command was published.
- Homebridge loaded `homebridge-iot-reversed@1.0.0`, subscribed to MQTT discovery, and created/cached `my-room lamp 1` plus `my-room outlet 1`, `2`, `3` at 18:26:16 Pi local time.
- Both services remained active; the new daemon invocation reported zero systemd restarts.

No unregistered physical device was available for a live registration test. Registration room persistence, duplicate request handling and credential preservation were tested using a fake scanner/publisher. The real existing device was not removed or re-registered. Panic was tested synthetically; a hardware panic was not intentionally induced.

## Deployment notes

The first Pi test build and npm install failed because the root filesystem was full. APT download caches were cleaned and the user-requested 171 MiB JDK installer was deleted; installed Java and `devices.json` were preserved. A subsequent stale Gradle lock was resolved by stopping the Gradle daemon and rebuilding with a single-use compiler process.

Previous daemon distribution: `/opt/iot-reversed-before-fix-1`. Homebridge configuration was backed up before adding the platform. MQTT credentials are kept only in private Pi configuration files and are not committed. `.idea` changes are excluded from this work.
