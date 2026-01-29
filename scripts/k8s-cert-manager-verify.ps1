param(
  [string]$Namespace = "cert-manager",
  [string]$Timeout = "120s"
)

kubectl get ns $Namespace | Out-Null
kubectl rollout status deployment/cert-manager -n $Namespace --timeout=$Timeout
kubectl rollout status deployment/cert-manager-cainjector -n $Namespace --timeout=$Timeout
kubectl rollout status deployment/cert-manager-webhook -n $Namespace --timeout=$Timeout
kubectl get pods -n $Namespace
