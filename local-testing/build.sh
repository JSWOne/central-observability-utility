#!/usr/bin/env bash
# Builds the library, then both demo services, then their images.
# The demos consume the starters from the local Maven repository exactly as a real service
# consumes them from Artifact Registry, so the library must be installed first.
set -euo pipefail
cd "$(dirname "$0")"

echo "==> installing central-observability-* into ~/.m2"
mvn -q -f ../pom.xml clean install -DskipTests

echo "==> packaging demo-boot3 (Spring Boot 3.4 / Java 21)"
mvn -q -f demo-boot3/pom.xml clean package

echo "==> packaging demo-boot2 (Spring Boot 2.6 / Java 11)"
mvn -q -f demo-boot2/pom.xml clean package

echo "==> building images"
docker compose build

echo "==> done. start with: docker compose up -d"
