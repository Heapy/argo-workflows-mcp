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

## Running the MCP Server

This project runs as an HTTP/SSE MCP server. It is not a stdio server, so MCP
clients must connect to its URL after the process is started.

```bash
./gradlew installDist

ARGO_MCP_HOST=127.0.0.1 \
ARGO_MCP_PORT=8080 \
ARGO_MCP_DB_PATH=./argo-workflows-mcp.db \
./build/install/argo-workflows-mcp/bin/argo-workflows-mcp
```

Open the web UI at <http://localhost:8080/connections>. The MCP SSE endpoint is
available at <http://localhost:8080/>.

Add an Argo connection from the web UI, or connect the MCP server first and use
the `add_connection` tool. Connection details are stored in the SQLite database
selected by `ARGO_MCP_DB_PATH`.

## Configuration with Claude Code

Claude Code can connect directly to the server's SSE endpoint:

```bash
claude mcp add --transport sse argo-workflows http://localhost:8080/
```

For project configuration, use `.mcp.json`:

```json
{
  "mcpServers": {
    "argo-workflows": {
      "type": "sse",
      "url": "http://localhost:8080/"
    }
  }
}
```

## Configuration with Claude Desktop

Claude Desktop remote MCP support depends on your Desktop version and plan. If
your Desktop build supports remote connectors, add a custom connector pointing to
`http://localhost:8080/`.

For Desktop setups that only launch stdio commands, bridge the local SSE server
with `mcp-remote`:

```json
{
  "mcpServers": {
    "argo-workflows": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote@latest",
        "http://localhost:8080/"
      ]
    }
  }
}
```

After changing Claude Desktop configuration, fully quit and reopen Claude
Desktop so it reloads the MCP server list.

## Docker Usage

### Using the published image

Images are published to GitHub Container Registry by CI:

- `ghcr.io/heapy/argo-workflows-mcp:main` — latest build from `main`
- `ghcr.io/heapy/argo-workflows-mcp:<git-sha>` — immutable build per commit

```bash
docker run -it --rm \
  -p 127.0.0.1:8080:8080 \
  -e ARGO_MCP_DB_PATH=/app/data/argo-workflows-mcp.db \
  -v "$HOME/.argo-workflows-mcp:/app/data" \
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
  -p 127.0.0.1:8080:8080 \
  -e ARGO_MCP_DB_PATH=/app/data/argo-workflows-mcp.db \
  -v "$(pwd)/data:/app/data" \
  argo-workflows-mcp:latest
```

Then open <http://localhost:8080/connections> and add the Argo server URL,
namespace, token or basic credentials, and TLS options.

If the TLS certificate is issued for another host, set the connection's TLS
server name in the UI or `add_connection` tool so the client presents the
expected SNI value.

## Environment Variables

### Server Configuration

- `ARGO_MCP_HOST` - HTTP bind address (default: `0.0.0.0`)
- `ARGO_MCP_PORT` - HTTP port (default: `8080`)
- `ARGO_MCP_DB_PATH` - SQLite database path (default: `argo-workflows-mcp.db`)

### Argo Workflows Connection

Argo connection settings are not read from environment variables. Configure
them in the web UI at `/connections`, or by calling the `add_connection` MCP
tool. The stored connection includes:

- Argo Workflows API URL
- Default Kubernetes namespace
- Bearer token or basic auth credentials
- TLS verification and TLS server name options
- Request timeout

### Permissions

Permission settings are stored in the SQLite database and can be changed from
the web UI at `/settings`.

### Logging

Application logs are written to stdout. Tool audit records are stored in the
SQLite database and shown in the web UI at `/audit`.

## Security Notes

- By default, the server runs in read-only mode
- Enable mutation or destructive operations only when the MCP server is bound to
  a trusted interface.
- Use the Settings page to review permission flags before enabling mutations.
- Tool calls are written to the audit log table.

## Development

This project uses:
- Kotlin with coroutines
- Gradle 9.2.0
- Java 25
- MCP Kotlin SDK
- Generated Argo Workflows client from OpenAPI spec

See [AGENTS.md](AGENTS.md) for development guidelines.
