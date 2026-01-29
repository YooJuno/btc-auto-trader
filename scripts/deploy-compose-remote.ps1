param(
  [Parameter(Mandatory = $true)]
  [string]$TargetHost,
  [string]$User = "ubuntu",
  [int]$Port = 22,
  [string]$RemoteDir = "/opt/btc-auto-trader",
  [string]$EnvFile = "infra/.env.prod",
  [string]$ComposeFile = "infra/docker-compose.prod.registry.yml",
  [string]$BackendImage = "",
  [string]$FrontendImage = "",
  [switch]$CopyEnv,
  [string]$HealthUrl = "http://localhost/api/actuator/health",
  [string]$FrontendUrl = "http://localhost/",
  [int]$HealthRetries = 12,
  [int]$HealthInterval = 5,
  [switch]$RollbackOnFail,
  [string]$DockerCmd = "docker",
  [string]$ComposeProject = "btc-trader",
  [switch]$SkipSync
)

function Escape-ForBashSingleQuotes {
  param([string]$Value)
  return $Value -replace "'", "'\"'\"'"
}

function Invoke-RemoteBash {
  param([string]$Script)
  $escaped = Escape-ForBashSingleQuotes $Script
  ssh $portOpt $remote "bash -lc '$escaped'"
}

if (!(Test-Path $ComposeFile)) {
  Write-Error "Compose file not found: $ComposeFile"
  exit 1
}

$remote = "$User@$TargetHost"
$portOpt = "-p $Port"

ssh $portOpt $remote "mkdir -p $RemoteDir/infra"

if (-not $SkipSync.IsPresent) {
  scp $portOpt infra/Caddyfile $remote:"$RemoteDir/infra/"
  scp $portOpt infra/docker-compose.prod.yml $remote:"$RemoteDir/infra/"
  scp $portOpt infra/docker-compose.prod.registry.yml $remote:"$RemoteDir/infra/"
  scp $portOpt infra/.env.prod.example $remote:"$RemoteDir/infra/"
}

if ($CopyEnv.IsPresent) {
  scp $portOpt $EnvFile $remote:"$RemoteDir/infra/.env.prod"
}

$storePrev = @'
cd __REMOTE_DIR__/infra
export COMPOSE_PROJECT_NAME=__COMPOSE_PROJECT__
backend_id=$(__DOCKER_CMD__ compose -f docker-compose.prod.registry.yml --env-file .env.prod ps -q backend || true)
frontend_id=$(__DOCKER_CMD__ compose -f docker-compose.prod.registry.yml --env-file .env.prod ps -q frontend || true)
backend_img=""
frontend_img=""
if [ -n "$backend_id" ]; then
  backend_img=$(__DOCKER_CMD__ inspect -f "{{.Config.Image}}" "$backend_id")
fi
if [ -n "$frontend_id" ]; then
  frontend_img=$(__DOCKER_CMD__ inspect -f "{{.Config.Image}}" "$frontend_id")
fi
printf "BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n" "$backend_img" "$frontend_img" > .last_images
'@
$storePrev = $storePrev.Replace('__REMOTE_DIR__', $RemoteDir).Replace('__COMPOSE_PROJECT__', $ComposeProject).Replace('__DOCKER_CMD__', $DockerCmd)
Invoke-RemoteBash $storePrev

$deploy = @'
cd __REMOTE_DIR__/infra
export COMPOSE_PROJECT_NAME=__COMPOSE_PROJECT__
__BACKEND_EXPORT____FRONTEND_EXPORT____DOCKER_CMD__ compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d
'@
$backendExport = ""
$frontendExport = ""
if ($BackendImage) { $backendExport = "export BACKEND_IMAGE=$BackendImage`n" }
if ($FrontendImage) { $frontendExport = "export FRONTEND_IMAGE=$FrontendImage`n" }
$deploy = $deploy.Replace('__REMOTE_DIR__', $RemoteDir)
$deploy = $deploy.Replace('__COMPOSE_PROJECT__', $ComposeProject)
$deploy = $deploy.Replace('__BACKEND_EXPORT__', $backendExport)
$deploy = $deploy.Replace('__FRONTEND_EXPORT__', $frontendExport)
$deploy = $deploy.Replace('__DOCKER_CMD__', $DockerCmd)
Invoke-RemoteBash $deploy

$health = @'
attempts=0
while [ $attempts -lt __HEALTH_RETRIES__ ]; do
  if curl -fsS '__HEALTH_URL__' >/dev/null 2>&1 && curl -fsS '__FRONTEND_URL__' >/dev/null 2>&1; then
    exit 0
  fi
  attempts=$((attempts+1))
  sleep __HEALTH_INTERVAL__
done
exit 1
'@
$health = $health.Replace('__HEALTH_RETRIES__', $HealthRetries)
$health = $health.Replace('__HEALTH_INTERVAL__', $HealthInterval)
$health = $health.Replace('__HEALTH_URL__', $HealthUrl)
$health = $health.Replace('__FRONTEND_URL__', $FrontendUrl)
Invoke-RemoteBash $health
if ($LASTEXITCODE -ne 0) {
  Write-Error "Healthcheck failed."
  if ($RollbackOnFail.IsPresent) {
    $rollback = @'
cd __REMOTE_DIR__/infra
export COMPOSE_PROJECT_NAME=__COMPOSE_PROJECT__
if [ -f .last_images ]; then
  set -a
  source .last_images
  set +a
fi
if [ -n "$BACKEND_IMAGE" ] || [ -n "$FRONTEND_IMAGE" ]; then
  __DOCKER_CMD__ compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d
fi
'@
    $rollback = $rollback.Replace('__REMOTE_DIR__', $RemoteDir).Replace('__COMPOSE_PROJECT__', $ComposeProject).Replace('__DOCKER_CMD__', $DockerCmd)
    Invoke-RemoteBash $rollback
  }
  exit 1
}

Write-Output "Deploy complete."
