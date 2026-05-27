# Local Argo MCP Testing Guide

This guide is for an LLM agent testing `argo-workflows-mcp` against a local Argo Workflows installation in Docker Desktop Kubernetes.

Use this as an end-to-end smoke plan. Keep each step small and verify before moving on.

## Current Scope

The MCP server currently exposes these tools:

| Tool                              | Backing behavior                                                                                     |
|-----------------------------------|------------------------------------------------------------------------------------------------------|
| `add_connection`                  | Real. Stores a connection in the local SQLite DB and optionally activates it.                        |
| `list_workflows`                  | Real Argo API call.                                                                                  |
| `get_workflow`                    | Real Argo API call.                                                                                  |
| `get_workflow_logs`               | Real Argo API call.                                                                                  |
| `terminate_workflow`              | Real Argo API call after destructive settings and confirmation checks. `dry_run=true` only previews. |
| `retry_workflow`                  | Real Argo API call guarded by mutation settings.                                                     |
| `list_cron_workflows`             | Real Argo API call.                                                                                  |
| `get_cron_workflow`               | Real Argo API call.                                                                                  |
| `get_cron_history`                | Real Argo workflow-list API call using the CronWorkflow label selector.                              |
| `toggle_cron_suspension`          | Real Argo API call guarded by mutation settings.                                                     |
| `list_workflow_templates`         | Real Argo API call.                                                                                  |
| `get_workflow_template`           | Real Argo API call.                                                                                  |
| `list_cluster_workflow_templates` | Real Argo API call.                                                                                  |
| `get_cluster_workflow_template`   | Real Argo API call.                                                                                  |

There is no MCP tool for submitting workflows yet. Create Kubernetes workflow fixtures with `kubectl` or the `argo` CLI, then inspect them through MCP.

## Prerequisites

1. Docker Desktop Kubernetes is running.
2. Argo Workflows is installed in namespace `argo`.
3. The local shell can access the cluster with `kubectl`.
4. Java is selected with SDKMAN when available:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk list java | grep "25\."
```

Pick an installed (marked `>>>` or `*`) Java 25 distribution and activate it:

```bash
sdk use java <version-from-list>
```

If no Java 25 is installed locally, install one first:

```bash
sdk install java 25.0.3-librca
sdk use java 25.0.3-librca
```

Gradle may resolve a toolchain on its own if SDKMAN is unavailable, but note the mismatch in the test report.

## 1. Check Local Argo

```bash
kubectl config current-context
kubectl get ns argo
kubectl -n argo get pods
kubectl -n argo get svc argo-server -o wide
```

Expected:

- Current context is usually `docker-desktop`.
- `argo-server` and `workflow-controller` pods are running.
- `argo-server` is often `ClusterIP` on port `2746`.

If `argo-server` is `ClusterIP`, start a port-forward in a dedicated terminal:

```bash
kubectl -n argo port-forward svc/argo-server 2746:2746
```

Verify Argo is reachable:

```bash
curl -k -sS https://localhost:2746/api/v1/info | head
```

Expected: JSON response with Argo server info. If plain HTTP returns `Client sent an HTTP request to an HTTPS server`, use `https://` and `insecure_skip_tls_verify=true` in MCP connection tests.

## 2. Start MCP Server on 8080

Use a throwaway DB so test connections/settings do not pollute a developer DB:

```bash
rm -f /tmp/argo-workflows-mcp-local-test.db
ARGO_MCP_HOST=127.0.0.1 \
ARGO_MCP_PORT=8080 \
ARGO_MCP_DB_PATH=/tmp/argo-workflows-mcp-local-test.db \
./gradlew run
```

In another terminal:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
curl -i --max-time 5 -H 'Accept: text/event-stream' http://localhost:8080/
```

Expected:

- A Java process listens on `8080`.
- The MCP SSE endpoint is `/`.
- The first SSE response includes an `event: endpoint` with `?sessionId=...`.
- `/sse` is not the configured MCP endpoint in this app.

## 3. Minimal MCP SSE Client

Use the committed `mcp-smoke.main.kts` script when the agent does not have a native MCP client. It opens the SSE stream, performs the JSON-RPC handshake (`initialize` -> `notifications/initialized` -> `tools/list`), then calls `add_connection` and `list_workflows`.

The script needs the `kotlin` compiler on PATH (`sdk install kotlin`). First run downloads `kotlinx-serialization-json` and compiles; subsequent runs are cached.

```bash
# Defaults to http://localhost:8080/
./mcp-smoke.main.kts

# Or target a different host/port
./mcp-smoke.main.kts http://localhost:8080/
```

The script source lives at [`mcp-smoke.main.kts`](mcp-smoke.main.kts). Use it as a reference for the JSON-RPC envelope when issuing the tool calls in the matrix below — each `call_tool(name, arguments)` in the examples maps to a `tools/call` request with `{"name": ..., "arguments": ...}`.

Expected:

- `tools/list` includes every tool listed in Current Scope.
- `add_connection` returns `isError=false`.
- `list_workflows` returns existing workflows or an empty-list message.

## 4. Create Non-Docker-Hub Workflow Fixtures

Use `registry.k8s.io/e2e-test-images/busybox:1.29-4` to avoid Docker Hub.

Create a succeeded workflow:

```bash
kubectl -n argo create -f - <<'YAML'
apiVersion: argoproj.io/v1alpha1
kind: Workflow
metadata:
  generateName: mcp-non-dockerhub-hello-
  labels:
    test-suite: argo-mcp-local
spec:
  entrypoint: hello
  templates:
    - name: hello
      container:
        image: registry.k8s.io/e2e-test-images/busybox:1.29-4
        command: [sh, -c]
        args:
          - echo hello from non-docker-hub image; uname -m
YAML
```

Create a failed workflow for status filtering and retry checks:

```bash
kubectl -n argo create -f - <<'YAML'
apiVersion: argoproj.io/v1alpha1
kind: Workflow
metadata:
  generateName: mcp-non-dockerhub-fail-
  labels:
    test-suite: argo-mcp-local
spec:
  entrypoint: fail
  templates:
    - name: fail
      container:
        image: registry.k8s.io/e2e-test-images/busybox:1.29-4
        command: [sh, -c]
        args:
          - echo failing intentionally; exit 42
YAML
```

Wait for fixture completion:

```bash
kubectl -n argo wait --for=condition=Completed workflow -l test-suite=argo-mcp-local --timeout=180s
kubectl -n argo get workflows -l test-suite=argo-mcp-local
```

Create real Argo resources for CronWorkflow and template checks. These names are expected in MCP output:

```bash
kubectl -n argo apply -f - <<'YAML'
apiVersion: argoproj.io/v1alpha1
kind: CronWorkflow
metadata:
  name: mcp-test-cron
  labels:
    test-suite: argo-mcp-local
spec:
  schedule: "0 0 31 2 *"
  suspend: true
  workflowSpec:
    entrypoint: hello
    templates:
      - name: hello
        container:
          image: registry.k8s.io/e2e-test-images/busybox:1.29-4
          command: [sh, -c]
          args: ["echo cron fixture"]
---
apiVersion: argoproj.io/v1alpha1
kind: WorkflowTemplate
metadata:
  name: mcp-test-template
  labels:
    test-suite: argo-mcp-local
spec:
  entrypoint: hello
  templates:
    - name: hello
      container:
        image: registry.k8s.io/e2e-test-images/busybox:1.29-4
        command: [sh, -c]
        args: ["echo template fixture"]
YAML
```

Only create a `ClusterWorkflowTemplate` if the local user has cluster-scoped permissions:

```bash
kubectl apply -f - <<'YAML'
apiVersion: argoproj.io/v1alpha1
kind: ClusterWorkflowTemplate
metadata:
  name: mcp-test-cluster-template
  labels:
    test-suite: argo-mcp-local
spec:
  entrypoint: hello
  templates:
    - name: hello
      container:
        image: registry.k8s.io/e2e-test-images/busybox:1.29-4
        command: [sh, -c]
        args: ["echo cluster template fixture"]
YAML
```

## 5. MCP Tool Test Matrix

Run the following through a native MCP client or the `mcp-smoke.main.kts` Kotlin script above.

### Connection

Call:

```json
{
  "name": "add_connection",
  "arguments": {
    "name": "docker-desktop-local",
    "base_url": "https://localhost:2746",
    "default_namespace": "argo",
    "auth_type": "none",
    "insecure_skip_tls_verify": true,
    "activate": true
  }
}
```

Expected:

- First run creates and activates the connection.
- Re-running with the same name should return an error that the connection name already exists.
- Audit log should redact sensitive arguments if token/password fields are used.

### No Active Connection

This check only works while no connection exists, so run it on a fresh DB **before** the step-3
smoke script (which creates and activates a connection). If `add_connection` has already run, skip
this check or restart the server with a clean `ARGO_MCP_DB_PATH`.

Before adding a connection, call any tool to verify the no-connection path:

```json
{"name": "list_workflows", "arguments": {"namespace": "argo", "limit": 5}}
```

Expected:

- Returns an informative error — not a crash. The exact message is:
  `No active Argo connection configured. Please add and activate a connection via the add_connection tool or web UI.`

### Workflow Read Operations

Call:

```json
{"name": "list_workflows", "arguments": {"namespace": "argo", "limit": 20}}
```

Expected:

- Shows the succeeded and failed fixture workflows.
- Includes phase, progress, start time, and duration.

Call without `namespace` (all namespaces):

```json
{"name": "list_workflows", "arguments": {"limit": 20}}
```

Expected:

- Omitting (or blanking) `namespace` lists workflows across **all** namespaces the connection can
  access (Argo `GET /api/v1/workflows/`).
- The response header reads `Found N workflow(s) in all namespaces`, the `namespace` field is `*`, and
  each workflow line is prefixed with its namespace, e.g. `argo/<name> [Succeeded] progress=1/1 ...`.
- Namespace allow/deny settings are applied per result: workflows in denied (or non-allowed)
  namespaces are dropped from the list rather than failing the whole call.
- Passing an explicit `"namespace": "argo"` instead scopes to that single namespace (header
  `Found N workflow(s) in namespace 'argo'`, no per-workflow namespace prefix).

Call:

```json
{"name": "list_workflows", "arguments": {"namespace": "argo", "status": "Succeeded", "limit": 20}}
```

Expected:

- Shows only succeeded workflows.

Call `get_workflow` with a real fixture name:

```json
{"name": "get_workflow", "arguments": {"namespace": "argo", "name": "REPLACE_WITH_WORKFLOW_NAME"}}
```

Expected:

- `status`, `progress`, `started_at`, `finished_at`, labels, and annotations are returned.

Call `get_workflow_logs`:

```json
{
  "name": "get_workflow_logs",
  "arguments": {
    "namespace": "argo",
    "workflow_name": "REPLACE_WITH_SUCCEEDED_WORKFLOW_NAME",
    "search": "non-docker-hub",
    "max_lines": 20
  }
}
```

Expected:

- Logs include `hello from non-docker-hub image`.
- `matching_lines` and `returned_lines` are non-zero.

Call `get_workflow_logs` with an explicit `pod_name`:

```bash
# Get pod name first
kubectl -n argo get pods -l workflows.argoproj.io/workflow=REPLACE_WITH_WORKFLOW_NAME
```

```json
{
  "name": "get_workflow_logs",
  "arguments": {
    "namespace": "argo",
    "workflow_name": "REPLACE_WITH_WORKFLOW_NAME",
    "pod_name": "REPLACE_WITH_POD_NAME",
    "container": "main"
  }
}
```

Expected:

- Returns logs only from the specified pod.
- `returned_lines` is non-zero.

### Workflow Mutation/Destructive Guards

Defaults should deny mutation/destructive operations.

Call:

```json
{"name": "retry_workflow", "arguments": {"namespace": "argo", "name": "REPLACE_WITH_FAILED_WORKFLOW_NAME"}}
```

Expected by default:

- Error with `PERMISSION_DENIED`.

Enable mutations through UI Settings or API:

```bash
curl -sS -X PUT \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'value=true' \
  http://localhost:8080/api/settings/allow_mutations
```

Call `retry_workflow` again.

Expected:

- Real Argo retry success with the workflow status returned by Argo.
- Do not assume a new workflow name. Argo retries the existing workflow resource.
- The failed fixture may move back to `Running` and then to its final phase.

Call `retry_workflow` with `restart_successful=true` (if the failed fixture ran partially):

```json
{
  "name": "retry_workflow",
  "arguments": {
    "namespace": "argo",
    "name": "REPLACE_WITH_FAILED_WORKFLOW_NAME",
    "restart_successful": true
  }
}
```

Expected:

- Argo retries all steps including any that previously succeeded.

Call:

```json
{
  "name": "terminate_workflow",
  "arguments": {
    "namespace": "argo",
    "name": "REPLACE_WITH_WORKFLOW_NAME",
    "reason": "local smoke test",
    "dry_run": true
  }
}
```

Expected by default:

- Error with `PERMISSION_DENIED`.

Enable destructive operations:

```bash
curl -sS -X PUT \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'value=true' \
  http://localhost:8080/api/settings/allow_destructive
```

Call `terminate_workflow` with `dry_run=true` again.

Expected:

- Dry-run preview.
- No Kubernetes workflow is terminated.

Call with confirmation path:

```json
{
  "name": "terminate_workflow",
  "arguments": {
    "namespace": "argo",
    "name": "REPLACE_WITH_WORKFLOW_NAME",
    "reason": "local smoke test",
    "dry_run": false
  }
}
```

Expected:

- If `require_confirmation=true` (default), returns a confirmation token in the form `terminate:<namespace>:<workflow-name>`.

Disable confirmation requirement and call again:

```bash
curl -sS -X PUT \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'value=false' \
  http://localhost:8080/api/settings/require_confirmation
```

```json
{
  "name": "terminate_workflow",
  "arguments": {
    "namespace": "argo",
    "name": "REPLACE_WITH_WORKFLOW_NAME",
    "reason": "local smoke test — require_confirmation=false path",
    "dry_run": true
  }
}
```

Expected:

- A dry-run preview. Note that with `dry_run=true` the preview is **identical** regardless of
  `require_confirmation` — it always includes the `Call again with dryRun=false and confirmationToken=...`
  instruction. `require_confirmation` only changes behavior when `dry_run=false`: with
  `require_confirmation=true` a `dry_run=false` call returns a confirmation token first, whereas with
  `require_confirmation=false` a `dry_run=false` call terminates immediately with no token. The
  `dry_run=true` call above therefore does not by itself demonstrate the setting; to observe the
  difference you must call `dry_run=false` (which actually terminates).

Reset:

```bash
curl -sS -X PUT \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'value=true' \
  http://localhost:8080/api/settings/require_confirmation
```

Only call with the returned token when you intentionally want to terminate the target workflow:

```json
{
  "name": "terminate_workflow",
  "arguments": {
    "namespace": "argo",
    "name": "REPLACE_WITH_WORKFLOW_NAME",
    "reason": "local smoke test",
    "dry_run": false,
    "confirmation_token": "terminate:argo:REPLACE_WITH_WORKFLOW_NAME"
  }
}
```

Expected:

- Real Argo terminate success.
- The Kubernetes workflow is actually terminated. Prefer a disposable running workflow for this check, or skip the valid-token call during a non-destructive smoke run.

### Cron Workflow Tools

Call:

```json
{"name": "list_cron_workflows", "arguments": {"namespace": "argo"}}
```

Expected:

- Real Argo response containing `mcp-test-cron` when the fixture was created.
- Empty-list success if no CronWorkflows exist in the namespace.

Call:

```json
{"name": "get_cron_workflow", "arguments": {"namespace": "argo", "name": "mcp-test-cron"}}
```

Expected:

- Real schedule/details for `mcp-test-cron`, including schedule and suspension state.

Call:

```json
{"name": "get_cron_history", "arguments": {"namespace": "argo", "name": "mcp-test-cron", "limit": 5}}
```

Expected:

- Real workflow history for workflows labeled with `workflows.argoproj.io/cron-workflow=mcp-test-cron`.
- Empty-list success is valid when the fixture has never run.

Call:

```json
{"name": "toggle_cron_suspension", "arguments": {"namespace": "argo", "name": "mcp-test-cron", "suspend": false}}
```

Expected:

- With `allow_mutations=false`, returns `PERMISSION_DENIED`.
- With `allow_mutations=true`, calls Argo and changes the real `CronWorkflow/mcp-test-cron`.
- Reset the fixture to the desired state before cleanup if later checks depend on it.

### Template Tools

Call:

```json
{"name": "list_workflow_templates", "arguments": {"namespace": "argo"}}
```

Expected:

- Real Argo response containing `mcp-test-template` when the fixture was created.

Call:

```json
{"name": "get_workflow_template", "arguments": {"namespace": "argo", "name": "mcp-test-template"}}
```

Expected:

- Real template details for `mcp-test-template`.

Call:

```json
{"name": "list_cluster_workflow_templates", "arguments": {}}
```

Expected:

- Real Argo response containing `mcp-test-cluster-template` when the fixture was created and the local user has cluster-scoped permissions.

Call:

```json
{"name": "get_cluster_workflow_template", "arguments": {"name": "mcp-test-cluster-template"}}
```

Expected:

- Real cluster template details for `mcp-test-cluster-template`.

### Namespace Filter Check

Set allow-list to only `argo`:

```bash
curl -sS -X PUT \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'value=argo' \
  http://localhost:8080/api/settings/namespaces_allow
```

Call:

```json
{"name": "list_workflows", "arguments": {"namespace": "default", "limit": 5}}
```

Expected:

- Error with `NAMESPACE_DENIED`.

Reset:

```bash
curl -sS -X PUT \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'value=*' \
  http://localhost:8080/api/settings/namespaces_allow
```

### Namespace Deny-List Check

Set deny-list to block `kube-system`:

```bash
curl -sS -X PUT \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'value=kube-system' \
  http://localhost:8080/api/settings/namespaces_deny
```

Call:

```json
{"name": "list_workflows", "arguments": {"namespace": "kube-system", "limit": 5}}
```

Expected:

- Error with `NAMESPACE_DENIED`.

`argo` namespace must still work while `kube-system` is denied:

```json
{"name": "list_workflows", "arguments": {"namespace": "argo", "limit": 5}}
```

Expected:

- Returns workflows from `argo` without error.

Reset:

```bash
curl -sS -X PUT \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'value=' \
  http://localhost:8080/api/settings/namespaces_deny
```

### Connection Name Conflict

Call `add_connection` a second time with the same name:

```json
{
  "name": "add_connection",
  "arguments": {
    "name": "docker-desktop-local",
    "base_url": "https://localhost:2746",
    "default_namespace": "argo",
    "auth_type": "none",
    "activate": false
  }
}
```

Expected:

- Returns an error indicating the connection name already exists.
- No duplicate is created.

### Bearer Token Connection

Add a second connection using `bearer` auth (use a dummy token for format validation). Note: the
`auth_type` value is `bearer`, not `bearer_token` — valid values are `none`, `bearer`, and `basic`.
The bearer token itself is passed in the separate `bearer_token` field.

```json
{
  "name": "add_connection",
  "arguments": {
    "name": "docker-desktop-bearer",
    "base_url": "https://localhost:2746",
    "default_namespace": "argo",
    "auth_type": "bearer",
    "bearer_token": "test-token-value",
    "insecure_skip_tls_verify": true,
    "activate": false
  }
}
```

Expected:

- Connection is created successfully. (Passing `"auth_type": "bearer_token"` instead fails with
  `auth_type must be one of: none, bearer, basic`.)
- Navigate to `/audit` and verify the raw token value `test-token-value` is **not** visible in the audit log row — it must be redacted.

## 6. UI Checks

Use Playwright MCP, Browser Use, or equivalent browser automation. Do not use macOS `open` as a substitute when an in-app/browser automation tool is available.

Base URL:

```text
http://localhost:8080
```

### Navigation

1. Navigate to `/connections`.
2. Assert page title/nav includes `Argo MCP`.
3. Assert nav links exist: `Audit Log`, `Connections`, `Settings`.
4. Assert `Connections` is the active page.

### Connections Page

1. Navigate to `/connections`.
2. Wait for `#connections-list` to finish loading.
3. If the MCP `add_connection` step already ran, assert row text includes:
   - `docker-desktop-local`
   - `https://localhost:2746`
   - `argo`
   - `none`
   - `Active`
4. Click `Test` on the active connection while the Argo port-forward is running.
5. Assert a success alert appears: `Connection successful! Argo server is reachable.`

Also test UI creation with a separate name:

1. Click `Add Connection`.
2. Fill:
   - `Name`: `ui-docker-desktop-local`
   - `Base URL`: `https://localhost:2746`
   - `Default Namespace`: `argo`
   - `Auth Type`: `none`
   - check `Skip TLS Verification`
3. Click `Create`.
4. Assert the new row appears.
5. Click `Activate` if it is not active.
6. Click `Test` and assert success.

### Settings Page

1. Navigate to `/settings`.
2. Assert sections exist:
   - `Permissions`
   - `Namespace Filtering`
3. Toggle `Allow Mutations (retry, suspend/resume)`.
4. Toggle `Allow Destructive Operations (terminate workflows)`.
5. Edit `Allowed Namespaces (comma-separated, * for all)` to `argo`.
6. Reload `/settings` and assert values persisted.
7. Reset values if the test DB is shared:
   - `allow_mutations=false`
   - `allow_destructive=false`
   - `require_confirmation=true`
   - `namespaces_allow=*`
   - `namespaces_deny=`

### Audit Log Page

1. Navigate to `/audit`.
2. Wait for `#audit-log` to load.
3. Assert rows include recent MCP calls such as:
   - `add_connection`
   - `list_workflows`
   - `get_workflow_logs`
4. Assert status cells include `SUCCESS` for successful calls.
5. If a bearer-token connection was tested, assert the raw token value is not visible in the audit UI.
   The Arguments column is truncated to ~80 characters, so the `bearer_token=[REDACTED]` segment is
   usually cut off entirely rather than shown — both outcomes are acceptable. The key assertion is that
   the literal `test-token-value` never appears. (Redaction is applied when the audit row is stored;
   the raw token is kept only in the `connections` table for authentication.)

## 7. Cleanup

Delete namespaced test resources:

```bash
kubectl -n argo delete workflow -l test-suite=argo-mcp-local --ignore-not-found
kubectl -n argo delete cronworkflow -l test-suite=argo-mcp-local --ignore-not-found
kubectl -n argo delete workflowtemplate -l test-suite=argo-mcp-local --ignore-not-found
```

Delete optional cluster-scoped fixture:

```bash
kubectl delete clusterworkflowtemplate -l test-suite=argo-mcp-local --ignore-not-found
```

Stop background processes started for the test:

```bash
lsof -nP -iTCP:2746 -sTCP:LISTEN
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Terminate only processes that this test started. Do not kill a developer's existing MCP server unless explicitly asked.

Remove the throwaway DB if used:

```bash
rm -f /tmp/argo-workflows-mcp-local-test.db
```

## 8. Report Template

Use this summary format:

```text
Local Argo MCP smoke test
- Kubernetes context:
- Argo namespace status:
- MCP endpoint:
- Connection added through MCP:
- Workflow fixture image:
- Workflow list/get/logs:
- Mutation/destructive guard behavior:
- Cron/template real Argo behavior:
- UI checks:
- Cleanup:
- Known gaps:
```

Known gaps to call out:

- MCP cannot submit workflows yet; fixtures are created outside MCP.
- Mutating tools call the real Argo API. Keep `dry_run=true` for terminate smoke checks unless a disposable running workflow is intentionally targeted.
- Local `ClusterIP` Argo server requires a port-forward unless exposed another way.
