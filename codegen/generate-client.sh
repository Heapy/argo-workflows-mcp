#!/bin/bash
set -e

echo "🔧 Generating Argo Workflows Kotlin client from OpenAPI spec..."

# Create output directory
rm -rf generated-client
mkdir -p generated-client

# Build and run the generator
docker build -f Dockerfile.codegen -t argo-kotlin-codegen .

# Extract generated code
docker create --name temp-codegen argo-kotlin-codegen
docker cp temp-codegen:/workspace/generated/. generated-client/
docker rm temp-codegen

echo "✅ Client generated in ./generated-client/"
echo ""
echo "📦 To integrate into your project:"
echo "   1. Review generated code in ./generated-client/src/"
echo "   2. Copy to src/main/kotlin/ or use as separate module"
echo "   3. Add generated models to your operations"