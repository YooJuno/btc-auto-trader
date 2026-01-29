# Operations

## Local dev
- Start Postgres via `infra/docker-compose.yml`
- Backend: `./gradlew bootRun`
- Frontend: `npm run dev`

## Environment
- `JWT_SECRET` must be long and random for production.
- `APP_ENC_KEY` must be a Base64-encoded 32-byte key (AES-256-GCM).

## Single-server to multi-server
When you move from home PC to public access:
- Place Nginx in front, enable HTTPS (Let's Encrypt).
- Separate frontend hosting and backend API domain.
- Restrict backend to private network if possible.

## Docker Compose (production)
```bash
cd infra
cp .env.prod.example .env.prod
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

## HTTPS (Caddy)
- `infra/Caddyfile` handles HTTPS automatically with Let's Encrypt.
- Set `DOMAIN` in `infra/.env.prod` to a real DNS name pointing to the server.
- Ports 80/443 must be open on the server firewall.

## Docker Compose (registry images)
```bash
cd infra
cp .env.prod.example .env.prod
# set BACKEND_IMAGE / FRONTEND_IMAGE to your registry images
docker compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d
```

## Remote deploy script (SSH + Compose)
Use the helper script to sync infra files and deploy via SSH.

```bash
./scripts/deploy-compose-remote.sh --host 1.2.3.4 \
  --backend-image ghcr.io/you/btc-backend:latest \
  --frontend-image ghcr.io/you/btc-frontend:latest
```

PowerShell example:

```powershell
.\scripts\deploy-compose-remote.ps1 -TargetHost 1.2.3.4 `
  -BackendImage ghcr.io/you/btc-backend:latest `
  -FrontendImage ghcr.io/you/btc-frontend:latest
```

Optional flags:
- `--copy-env` or `-CopyEnv` to upload `.env.prod`
- `--rollback-on-fail` or `-RollbackOnFail` for auto rollback
- `--health-url`, `--frontend-url`, `--health-retries`, `--health-interval`

## Jenkins + Kubernetes (future)
- Jenkinsfile is included at repo root for build/test and Docker image builds.
- Kubernetes manifests use Kustomize under `k8s/` with base + overlays.

### Jenkins deploy parameters
- `DEPLOY_TARGET`: `none` | `compose` | `k8s`
- `REGISTRY`: container registry (e.g. `ghcr.io/owner`)
- `IMAGE_TAG`: image tag to build/push/deploy
- Compose deploy: set `SSH_HOST`, `SSH_USER`, `SSH_PORT`, `SSH_CREDENTIALS_ID`, `REMOTE_APP_DIR`
- Compose env: set `ENV_FILE_CREDENTIALS_ID` to upload `.env.prod`
- Health/rollback: `HEALTHCHECK_URL`, `FRONTEND_URL`, `HEALTHCHECK_RETRIES`, `HEALTHCHECK_INTERVAL`, `ROLLBACK_ON_FAIL`
- K8s deploy: set `KUBE_CONTEXT`, `K8S_NAMESPACE`, `KUSTOMIZE_OVERLAY`
- K8s secrets: set `K8S_SECRETS_CREDENTIALS_ID` to provide `secrets.env`

### Kubernetes TLS
- Install cert-manager before applying the prod overlay.
- Update `k8s/overlays/prod/cluster-issuer.yaml` with your email.

## cert-manager scripts
```bash
./scripts/k8s-cert-manager-install.sh vX.Y.Z
./scripts/k8s-cert-manager-verify.sh
```

PowerShell:
```powershell
.\scripts\k8s-cert-manager-install.ps1 -Version vX.Y.Z
.\scripts\k8s-cert-manager-verify.ps1
```

## Security checklist
- Never store exchange keys in frontend.
- Rotate JWT secret and encryption key.
- Limit IPs in Upbit API key whitelist.
