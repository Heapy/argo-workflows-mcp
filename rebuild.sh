#!/bin/bash

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 25.0.1-amzn
./gradlew installDist
VERSION="$(./version.sh)"
docker build --build-arg MCP_SERVER_VERSION="$VERSION" \
  -t ghcr.io/heapy/argo-workflows-mcp:main \
  -t ghcr.io/heapy/argo-workflows-mcp:"$VERSION" \
  .
