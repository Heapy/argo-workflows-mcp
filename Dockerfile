# Runtime stage - assumes application is built locally
FROM bellsoft/liberica-openjre-alpine:25

ARG MCP_SERVER_VERSION=unknown

WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 mcp && \
    adduser -D -u 1000 -G mcp mcp

# Copy the built application from local build
COPY build/install/argo-workflows-mcp /app

# Create data directory for SQLite database
RUN mkdir -p /app/data && \
    chown -R mcp:mcp /app && \
    chmod +x /app/bin/argo-workflows-mcp

# Switch to non-root user
USER mcp

# Environment variables with defaults
ENV ARGO_MCP_HOST="0.0.0.0" \
    ARGO_MCP_PORT="8080" \
    ARGO_MCP_DB_PATH="/app/data/argo-workflows-mcp.db"

EXPOSE 8080

# Run the MCP server
ENTRYPOINT ["/app/bin/argo-workflows-mcp"]
