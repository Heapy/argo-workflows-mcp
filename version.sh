#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
VERSION="$(sed -n 's/^version=//p' "$ROOT_DIR/gradle.properties" | head -n 1)"

if [ -z "$VERSION" ]; then
  echo "Failed to determine project version" >&2
  exit 1
fi

printf '%s\n' "$VERSION"
