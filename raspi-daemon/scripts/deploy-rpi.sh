#!/usr/bin/env bash
set -euo pipefail

pi_host="${IOT_RPI_HOST:-pi@192.168.45.150}"
apply=false
serve=true

while (($#)); do
    case "$1" in
        --host)
            [[ $# -ge 2 ]] || { echo "--host requires user@host" >&2; exit 2; }
            pi_host="$2"
            shift 2
            ;;
        --apply)
            apply=true
            shift
            ;;
        --skip-serve)
            serve=false
            shift
            ;;
        *)
            echo "Usage: $0 [--host user@host] [--apply] [--skip-serve]" >&2
            exit 2
            ;;
    esac
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
distribution="$repo_root/raspi-daemon/build/install/raspi-daemon"
ssh_control_path="${IOT_SSH_CONTROL_PATH:-/tmp/iot-reversed-ssh-%C}"
ssh_options=(-o ControlMaster=auto -o ControlPersist=600 -o "ControlPath=$ssh_control_path")
rsync_ssh="ssh -o ControlMaster=auto -o ControlPersist=600 -o ControlPath=$ssh_control_path"

if [[ "$apply" != true ]]; then
    printf 'Target: %s\n' "$pi_host"
    printf '%s\n' 'Dry run only. Re-run with --apply to build, install, and restart the daemon.'
    exit 0
fi

command -v ssh >/dev/null
command -v rsync >/dev/null

cd "$repo_root"
bash ./gradlew :raspi-daemon:test :raspi-daemon:installDist --console=plain

ssh "${ssh_options[@]}" "$pi_host" 'sudo test -f /etc/iot-reversed/devices.json && sudo test -f /opt/iot-reversed/lib/libbtleplug_ffi.so'
remote_stage="$(ssh "${ssh_options[@]}" "$pi_host" 'mktemp -d /home/pi/iot-reversed-upload-XXXXXX')"
[[ "$remote_stage" =~ ^/home/pi/iot-reversed-upload-[a-zA-Z0-9]+$ ]] || exit 2

cleanup() {
    ssh "${ssh_options[@]}" "$pi_host" "rm -rf -- '$remote_stage'" >/dev/null 2>&1 || true
}
trap cleanup EXIT

rsync -e "$rsync_ssh" -a --delete "$distribution/" "$pi_host:$remote_stage/distribution/"
rsync -e "$rsync_ssh" -a "$repo_root/raspi-daemon/iot-reversed.service" "$pi_host:$remote_stage/iot-reversed.service"

ssh "${ssh_options[@]}" "$pi_host" "sudo bash -s -- '$remote_stage' '$serve'" <<'REMOTE'
set -euo pipefail
stage="$1"
serve="$2"
install_root=/opt/iot-reversed
next_root=/opt/iot-reversed.next
previous_root="/opt/iot-reversed.previous.$(date -u +%Y%m%dT%H%M%S)-$$"
token_file=/etc/iot-reversed/http-token
dropin_dir=/etc/systemd/system/iot-reversed.service.d

test -f /etc/iot-reversed/devices.json
test -f "$install_root/lib/libbtleplug_ffi.so"
test ! -e "$next_root"
test ! -e "$previous_root"
install -d -m 0755 "$next_root"
cp -a "$stage/distribution/." "$next_root/"
install -D -m 0755 "$install_root/lib/libbtleplug_ffi.so" "$next_root/lib/libbtleplug_ffi.so"

if [[ ! -s "$token_file" ]]; then
    umask 077
    openssl rand -hex 32 > "$token_file"
fi
chown iotbridge:iotbridge "$token_file"
chmod 0600 "$token_file"

install -d -m 0755 "$dropin_dir"
printf '%s\n' \
    '[Service]' \
    'Environment=IOT_HTTP_TOKEN_FILE=/etc/iot-reversed/http-token' \
    'Environment=IOT_HTTP_PORT=8080' > "$dropin_dir/http.conf"
install -m 0644 "$stage/iot-reversed.service" /etc/systemd/system/iot-reversed.service

systemctl stop iot-reversed
mv "$install_root" "$previous_root"
mv "$next_root" "$install_root"
systemctl daemon-reload
systemctl enable iot-reversed
systemctl start iot-reversed
attempt_invocation=$(systemctl show iot-reversed -p InvocationID --value)

check_attempt() {
    if journalctl "_SYSTEMD_INVOCATION_ID=$attempt_invocation" --no-pager |
        grep -E 'PROTOCOL_HALTED|availability=panicked|NotConnectedException' ; then
        systemctl stop iot-reversed
        echo 'ATTEMPT_STOPPED: inspect the failure before another deployment or connection attempt.' >&2
        exit 2
    fi
}

if ! systemctl is-active --quiet iot-reversed; then
    systemctl stop iot-reversed || true
    mv "$install_root" "$previous_root.failed"
    mv "$previous_root" "$install_root"
    systemctl start iot-reversed
    echo 'Deployment failed; previous distribution was restored.' >&2
    exit 1
fi

authorization="Authorization: Bearer $(<"$token_file")"
for _ in {1..20}; do
    check_attempt
    if curl --max-time 3 --silent --fail --header "$authorization" http://127.0.0.1:8080/v1/devices >/dev/null; then
        break
    fi
    sleep 1
done
curl --max-time 3 --silent --fail --header "$authorization" http://127.0.0.1:8080/v1/devices >/dev/null
for _ in {1..30}; do
    check_attempt
    sleep 1
done
check_attempt

if [[ "$serve" == true ]] && command -v tailscale >/dev/null; then
    if ! timeout 15s tailscale serve --bg http://127.0.0.1:8080; then
        echo 'Tailscale Serve is awaiting tailnet approval; use the URL printed above, then run this deployment again.' >&2
    fi
fi

systemctl is-active iot-reversed
journalctl "_SYSTEMD_INVOCATION_ID=$attempt_invocation" --no-pager | grep -E 'HTTP API|PROTOCOL_HALTED|panicked|NotConnected|Timeout|cleanup complete|RX resynchronized|link=Connected' || true
REMOTE

printf '%s\n' 'Deployment and cached HTTP health check completed.'
