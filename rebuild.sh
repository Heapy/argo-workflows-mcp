#!/bin/bash

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 25.0.1-amzn
./gradlew installDist
docker build -t ghcr.io/heapy/argo-workflows-mcp:main .
