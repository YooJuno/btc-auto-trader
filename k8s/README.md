# Kubernetes manifests

These manifests are a starting point for running the app on Kubernetes. They assume you will use a private registry and an Ingress controller (nginx).

## Apply order (example)
1) `kubectl apply -f k8s/secrets.example.yaml` (edit values first)
2) `kubectl apply -f k8s/postgres-statefulset.yaml`
3) `kubectl apply -f k8s/postgres-service.yaml`
4) `kubectl apply -f k8s/backend-deployment.yaml`
5) `kubectl apply -f k8s/backend-service.yaml`
6) `kubectl apply -f k8s/frontend-deployment.yaml`
7) `kubectl apply -f k8s/frontend-service.yaml`
8) `kubectl apply -f k8s/ingress.yaml`

## Notes
- In production, prefer a managed Postgres and remove the in-cluster StatefulSet.
- Update container image names to your registry.
- Configure TLS in the ingress once you have a domain.
