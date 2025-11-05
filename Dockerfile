# Runtime stage - assumes application is built locally
FROM bellsoft/liberica-openjre-alpine:25

ARG MCP_SERVER_VERSION=unknown

WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 mcp && \
    adduser -D -u 1000 -G mcp mcp

# Copy the built application from local build
COPY build/install/argo-workflows-mcp /app

# Set ownership
RUN chown -R mcp:mcp /app && \
    chmod +x /app/bin/argo-workflows-mcp

# Switch to non-root user
USER mcp

# Environment variables with defaults
ENV MCP_SERVER_NAME="argo-workflows-mcp" \
    MCP_SERVER_VERSION="${MCP_SERVER_VERSION}"

# Run the MCP server
ENTRYPOINT ["/app/bin/argo-workflows-mcp"]
