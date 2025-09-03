#!/usr/bin/env bash
set -euo pipefail
DEVICE=${1:-$(adb devices | awk 'NR>1 && $1!=""{print $1; exit}')}
NAME=${2:-signal_preconfigured}

cmd_save() {
  echo "Saving snapshot '$NAME' on $DEVICE"
  adb -s "$DEVICE" emu avd snapshot save "$NAME"
}

cmd_load() {
  echo "Loading snapshot '$NAME' on $DEVICE"
  adb -s "$DEVICE" emu avd snapshot load "$NAME"
}

case "${3:-}" in
  save) cmd_save ;;
  load) cmd_load ;;
  *) echo "Usage: $0 [device] snapshot_name {save|load}"; exit 2 ;;
esac
