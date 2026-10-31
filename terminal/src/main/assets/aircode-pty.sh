#!/bin/sh
if command -v script >/dev/null 2>&1; then
  exec script -qfc "/usr/local/bin/aircode-inner $*" /dev/null
fi
exec "$@"
