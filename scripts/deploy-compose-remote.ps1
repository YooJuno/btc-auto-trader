param(
  [Parameter(Mandatory = $true)]
  [string]$Host,
  [string]$User = "ubuntu",
  [int]$Port = 22,
  [string]$RemoteDir = "/opt/btc-auto-trader",
  [string]$EnvFile = "infra/.env.prod",
  [string]$ComposeFile = "infra/docker-compose.prod.registry.yml",
  [string]$BackendImage = "",
  [string]$FrontendImage = "",
  [switch]$CopyEnv
)

if (!(Test-Path $ComposeFile)) {
  Write-Error "Compose file not found: $ComposeFile"
  exit 1
}

$remote = "$User@$Host"
$portOpt = "-p $Port"

ssh $portOpt $remote "mkdir -p $RemoteDir/infra"

scp $portOpt infra/Caddyfile $remote:"$RemoteDir/infra/"
scp $portOpt infra/docker-compose.prod.yml $remote:"$RemoteDir/infra/"
scp $portOpt infra/docker-compose.prod.registry.yml $remote:"$RemoteDir/infra/"
scp $portOpt infra/.env.prod.example $remote:"$RemoteDir/infra/"

if ($CopyEnv.IsPresent) {
  scp $portOpt $EnvFile $remote:"$RemoteDir/infra/.env.prod"
}

$remoteCmd = "cd $RemoteDir/infra"
if ($BackendImage) {
  $remoteCmd += " && export BACKEND_IMAGE=$BackendImage"
}
if ($FrontendImage) {
  $remoteCmd += " && export FRONTEND_IMAGE=$FrontendImage"
}
$remoteCmd += " && docker compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d"

ssh $portOpt $remote $remoteCmd
Write-Output "Deploy complete."
