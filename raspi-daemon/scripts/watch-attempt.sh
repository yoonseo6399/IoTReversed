#!/usr/bin/env bash
set -eu

sudo systemctl start iot-reversed
attempt_invocation=$(systemctl show iot-reversed -p InvocationID --value)
timeout 65s journalctl "_SYSTEMD_INVOCATION_ID=$attempt_invocation" --no-pager -f |
while IFS= read -r line; do
    printf '%s\n' "$line"
    case "$line" in
        *"availability=panicked"*|*NotConnectedException*|*"Could not connect"*)
            sudo systemctl stop iot-reversed
            printf '%s\n' 'ATTEMPT_STOPPED: inspect the failure before another branch or device connection'
            exit 2
            ;;
    esac
done
