 # Installing Argo Locally: Docker Desktop, macOS

https://argo-workflows.readthedocs.io/en/latest/quick-start/

## Argo CLI

```
brew install argo
```

## Check if running

```
kubectl get svc -n argo
```

## Argo API Port

### Temporary Access

1. Port-forward the Argo server:
   kubectl -n argo port-forward svc/argo-server 2746:2746
2. Then access it at:
   - UI: http://localhost:2746
   - API: http://localhost:2746/api/v1/workflows/default

### For persistent access across restarts:

Change Argo service to NodePort (Recommended for local dev)

```
kubectl patch svc argo-server -n argo -p '{"spec":{"type":"NodePort","ports":[{"port":2746,"nodePort":30746}]}}'
```

This will make Argo accessible at http://localhost:30746 permanently.

### Configuration for local development

Use `host.docker.internal` as host if you're using local argo and running MCP in Docker container.

If the exposed certificate is issued for `localhost`, export `ARGO_TLS_SERVER_NAME=localhost` so the client presents the expected SNI value during the TLS handshake.
