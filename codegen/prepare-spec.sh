#!/bin/sh
# Preprocess the upstream Argo OpenAPI spec before handing it to
# openapi-generator.
#
# Two transforms:
#  1. Drop `items` from the `required` array of the four `*List` definitions.
#     Argo's server contradicts the spec by returning `{"items": null}` for
#     empty lists; making the field optional lets the generator emit
#     nullable list properties so deserialization succeeds.
#  2. Shorten verbose Go-style package prefixes in definition names so the
#     generated Kotlin classes get readable names.
set -e

INPUT="${1:-/workspace/swagger-input.json}"
OUTPUT="${2:-/workspace/swagger.json}"

jq '
  ( .definitions["io.argoproj.workflow.v1alpha1.WorkflowList"].required
  , .definitions["io.argoproj.workflow.v1alpha1.CronWorkflowList"].required
  , .definitions["io.argoproj.workflow.v1alpha1.WorkflowTemplateList"].required
  , .definitions["io.argoproj.workflow.v1alpha1.ClusterWorkflowTemplateList"].required
  ) |= map(select(. != "items"))
' "$INPUT" \
  | sed 's/io.k8s.api.core.v1./Kubernetes/' \
  | sed 's/io.argoproj.workflow.v1alpha1.//' \
  | sed 's/github.com.argoproj.argo_events.pkg.apis.events.v1alpha1./ArgoEvents/' \
  | sed 's/io.k8s.apimachinery.pkg.apis.meta.v1./ApiMachinery/' \
  > "$OUTPUT"