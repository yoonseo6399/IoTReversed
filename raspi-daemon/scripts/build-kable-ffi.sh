#!/usr/bin/env bash
set -euo pipefail

kable_version=0.42.0
destination=${1:-./libbtleplug_ffi.so}
build_directory=$(mktemp -d)

cleanup() {
  rm -rf "$build_directory"
}

trap cleanup EXIT
git clone --depth 1 --branch "$kable_version" https://github.com/JuulLabs/kable.git "$build_directory/kable"
cargo build --manifest-path "$build_directory/kable/kable-btleplug-ffi/Cargo.toml" --release --locked
install -D -m 0755 "$build_directory/kable/kable-btleplug-ffi/target/release/libbtleplug_ffi.so" "$destination"
