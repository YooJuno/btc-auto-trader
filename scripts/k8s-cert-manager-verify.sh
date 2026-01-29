#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="cert-manager"
TIMEOUT="120s"

kubectl get ns "$NAMESPACE" >/dev/null
kubectl rollout status deployment/cert-manager -n "$NAMESPACE" --timeout="$TIMEOUT"
kubectl rollout status deployment/cert-manager-cainjector -n "$NAMESPACE" --timeout="$TIMEOUT"
kubectl rollout status deployment/cert-manager-webhook -n "$NAMESPACE" --timeout="$TIMEOUT"

kubectl get pods -n "$NAMESPACE"
