#!/bin/sh
SIZE_FILE="${AIRCODE_SIZE_FILE:-/tmp/aircode-size}"
stty cols "${AIRCODE_COLS:-80}" rows "${AIRCODE_ROWS:-24}" 2>/dev/null
(
  last=""
  while :; do
    size=""
    read -r size < "$SIZE_FILE" 2>/dev/null
    if [ -n "$size" ] && [ "$size" != "$last" ]; then
      stty -F /dev/tty $size 2>/dev/null && last="$size"
    fi
    sleep 1
  done
) >/dev/null 2>&1 &
exec "$@"
