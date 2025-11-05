# Implementation Plan

## Phase 0 – Baseline (Complete)
- MCP server boots over STDIO, registers tool schemas, and uses `ArgoWorkflowsHttpClient`.
- `list_workflows`, `get_workflow`, and `get_workflow_logs` execute against the live Argo API.

## Phase 1 – Read Coverage
- Extend `ArgoWorkflowsClient` and generated HTTP client for CronWorkflow list/get/history APIs.
- Replace `CronWorkflowOperations` mocks with real `list`, `get`, and `getCronHistory` implementations.
- Extend the client for WorkflowTemplate and ClusterWorkflowTemplate endpoints.
- Replace `TemplateOperations` mocks with real list/get implementations for both scopes.
- Implement `watch_workflow` streaming using the controller watch API and expose it as a tool.
- Add cluster insights: implement `list_namespaces`, `get_cluster_info`, and `health_check` tools.
- Write unit tests for all read-only operations (workflow, cron, template, cluster) including error paths.

## Phase 2 – Confirmation & Mutations
- Build confirmation token generation, persistence with expiry, and validation utilities.
- Populate dry-run previews with live Argo details (pods, statuses, timestamps, impact summary).
- Enforce namespace allow/deny rules and permission flags before any destructive action.
- Implement real `terminate_workflow` using the Argo terminate endpoint with confirmation enforcement.
- Implement real `retry_workflow`, honoring `restartSuccessful`.
- Implement real `toggle_cron_suspension` that patches CronWorkflow suspension state.
- Write audit log entries to `logging.auditFile` for every destructive or mutation action.
- Add unit tests covering happy paths, invalid/expired tokens, permission denials, and audit failures.

## Phase 3 – Workflow Management
- Implement `submit_workflow` supporting manifest upload and template reference execution with confirmation.
- Implement `delete_workflow` with confirmation, audit logging, and namespace protections.
- Implement `update_cron_schedule` using confirmation tokens and validation of the new schedule.
- Implement `create_template` (namespaced and cluster) with dry-run previews and audit logging.
- Integrate MCP prompt-based confirmation as an optional path alongside tokens.
- Document required environment variables for auth/TLS and destructive-operation configuration.
- Add targeted integration-style tests validating mutation flows against a simulated Argo API.

## Phase 4 – Diagnostics & Insights
- Implement `analyze_failure` that inspects failed nodes/logs and surfaces AI-generated guidance.
- Implement `compare_workflows` to diff workflow executions and highlight key differences.
- Implement `get_workflow_metrics` exposing resource usage and timing from the Argo API or metrics backend.
- Enhance `watch_workflow` with optional notification hooks or streaming summaries.
- Backfill scenario tests ensuring diagnostic tools handle large workflows and error conditions gracefully.
