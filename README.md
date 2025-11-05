# Argo Workflows MCP Server

Model Context Protocol (MCP) server for interacting with Argo Workflows. This server enables Claude Desktop and Claude Code to manage and monitor Argo Workflows directly.

## Features

- List and monitor workflows
- Manage workflow templates
- Handle cron workflows
- Query workflow status and logs
- Execute workflow operations with configurable permissions

## Building

```bash
# Build the application
./gradlew installDist

# Run tests
./gradlew test
```

## Configuration with Claude Desktop

Add this to your Claude Desktop MCP configuration file:

**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows**: `%APPDATA%/Claude/claude_desktop_config.json`

### Using the `claude` CLI

```bash
claude mcp add --transport stdio argo-workflows \
  -- docker run -i --rm \
    -e ARGO_BASE_URL=http://host.docker.internal:2746 \
    -e ARGO_NAMESPACE=default \
    -e MCP_ALLOW_MUTATIONS=true \
    -e MCP_AUDIT_FILE=/app/logs/mcp-audit.log \
    -v "$HOME/.kube/config:/home/mcp/.kube/config:ro" \
    -v "$HOME/.argo-mcp-logs:/app/logs" \
    ghcr.io/heapy/argo-workflows-mcp:main
```

After running the command restart Claude Desktop so it reloads the MCP list.

### Using Docker

```json
{
  "mcpServers": {
    "argo-workflows": {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e", "ARGO_BASE_URL=http://host.docker.internal:2746",
        "-e", "ARGO_NAMESPACE=default",
        "-e", "MCP_ALLOW_MUTATIONS=true",
        "-e", "MCP_AUDIT_FILE=/app/logs/mcp-audit.log",
        "-v", "/Users/your-user/.kube/config:/home/mcp/.kube/config:ro",
        "-v", "/Users/your-user/.argo-mcp-logs:/app/logs",
        "ghcr.io/heapy/argo-workflows-mcp:main"
      ]
    }
  }
}
```

## Configuration with Claude Code

Add this to your Claude Code MCP configuration file:

**Location**: `~/.config/claude-code/mcp_config.json`

### Using Docker

```json
{
  "mcpServers": {
    "argo-workflows": {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e", "ARGO_BASE_URL=http://host.docker.internal:2746",
        "-e", "ARGO_NAMESPACE=default",
        "-e", "MCP_ALLOW_MUTATIONS=true",
        "-e", "MCP_AUDIT_FILE=/app/logs/mcp-audit.log",
        "-v", "/Users/your-user/.kube/config:/home/mcp/.kube/config:ro",
        "-v", "/Users/your-user/.argo-mcp-logs:/app/logs",
        "ghcr.io/heapy/argo-workflows-mcp:main"
      ]
    }
  }
}
```

## Docker Usage

### Using the published image

Images are published to GitHub Container Registry by CI:

- `ghcr.io/heapy/argo-workflows-mcp:main` — latest build from `main`
- `ghcr.io/heapy/argo-workflows-mcp:<git-sha>` — immutable build per commit

```bash
docker run -it --rm \
  -e ARGO_BASE_URL=https://your-argo-server:2746 \
  -e ARGO_NAMESPACE=default \
  -e ARGO_TOKEN=$(cat ~/secrets/argo-token) \
  -e MCP_AUDIT_FILE=/app/logs/mcp-audit.log \
  -v /Users/your-user/.kube/config:/home/mcp/.kube/config:ro \
  -v /Users/your-user/.argo-mcp-logs:/app/logs \
  ghcr.io/heapy/argo-workflows-mcp:main
```

### Building locally

```bash
# Build the application first
./gradlew installDist

# Build Docker image
docker build -t argo-workflows-mcp:latest .

# Run with docker-compose
docker-compose up

# Or run directly
docker run -it --rm \
  -e ARGO_BASE_URL=http://your-argo-server:2746 \
  -e ARGO_NAMESPACE=default \
  -e ARGO_TOKEN=your-token \
  -e MCP_AUDIT_FILE=/app/logs/mcp-audit.log \
  -v $(pwd)/logs:/app/logs \
  argo-workflows-mcp:latest
```

## Environment Variables

### Server Configuration

- `MCP_SERVER_NAME` - Server name (default: `argo-workflows-mcp`)
- `MCP_SERVER_VERSION` - Server version (default: `0.1.0`)

### Argo Workflows Connection

- `ARGO_BASE_URL` - Argo Workflows API URL (default: `http://localhost:2746`)
- `ARGO_NAMESPACE` - Default Kubernetes namespace (default: `default`)
- `ARGO_TOKEN` - Bearer token for authentication
- `ARGO_USERNAME` - Username for basic auth
- `ARGO_PASSWORD` - Password for basic auth
- `ARGO_INSECURE_SKIP_TLS_VERIFY` - Skip TLS verification (default: `false`)
- `ARGO_REQUEST_TIMEOUT_SECONDS` - Request timeout (default: `30`)

### Kubernetes Configuration

- `KUBECONFIG` - Path to kubeconfig file
- `KUBE_CONTEXT` - Kubernetes context to use

### Permissions

- `MCP_ALLOW_DESTRUCTIVE` - Allow destructive operations like delete (default: `false`)
- `MCP_ALLOW_MUTATIONS` - Allow mutations like create/update (default: `false`)
- `MCP_REQUIRE_CONFIRMATION` - Require confirmation for sensitive operations (default: `true`)
- `MCP_NAMESPACES_ALLOW` - Comma-separated list of allowed namespaces (default: `*`)
- `MCP_NAMESPACES_DENY` - Comma-separated list of denied namespaces (default: empty)

### Logging

- `MCP_AUDIT_ENABLED` - Enable audit logging (default: `true`)
- `MCP_AUDIT_FILE` - Audit log file path (default: `./mcp-audit.log`)
- `MCP_LOG_LEVEL` - Log level: debug, info, warn, error (default: `info`)

## Security Notes

- By default, the server runs in read-only mode
- Set `MCP_ALLOW_MUTATIONS=true` to enable create/update operations
- Set `MCP_ALLOW_DESTRUCTIVE=true` to enable delete operations
- Use `MCP_NAMESPACES_ALLOW` to restrict access to specific namespaces
- All operations are logged when `MCP_AUDIT_ENABLED=true`

## Development

This project uses:
- Kotlin with coroutines
- Gradle 9.2.0
- Java 25
- MCP Kotlin SDK
- Generated Argo Workflows client from OpenAPI spec

See [AGENTS.md](AGENTS.md) for development guidelines.
