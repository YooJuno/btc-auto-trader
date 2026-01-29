# Kubernetes (Kustomize)

This folder is organized into a base and overlays for multiple environments.

## Structure
- `k8s/base`: core Deployments/Services/StatefulSet
- `k8s/overlays/dev`: dev overlay
- `k8s/overlays/prod`: prod overlay (includes ingress)

## Usage
1) Copy secrets file and edit values

```bash
cp k8s/overlays/dev/secrets.env.example k8s/overlays/dev/secrets.env
cp k8s/overlays/prod/secrets.env.example k8s/overlays/prod/secrets.env
```

2) Apply overlay

```bash
kubectl apply -k k8s/overlays/dev
# or
kubectl apply -k k8s/overlays/prod
```

## Images
Base uses image names `btc-backend:latest` and `btc-frontend:latest`.
Update them via:

```bash
kubectl set image deployment/btc-backend backend=<registry>/btc-backend:<tag>
kubectl set image deployment/btc-frontend frontend=<registry>/btc-frontend:<tag>
```

## Notes
- For production, consider managed Postgres and remove the in-cluster StatefulSet.
- Add TLS by using cert-manager and annotating `k8s/overlays/prod/ingress.yaml`.
