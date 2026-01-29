param(
  [Parameter(Mandatory = $true)]
  [string]$Version
)

if (-not $Version) {
  Write-Error "Version is required. Example: v1.14.5"
  exit 1
}

kubectl apply -f "https://github.com/cert-manager/cert-manager/releases/download/$Version/cert-manager.yaml"
