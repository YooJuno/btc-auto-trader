# Infrastructure

### DB
PostgreSQL is used as the primary database for storing application data.

### Caddy
- Deployment guide: `apps/infra/caddy/README.md`
- Script: `apps/infra/caddy/setup.sh`
- Example config: `apps/infra/caddy/Caddyfile.example`

### Caching
Redis is employed for caching frequently accessed data to improve performance.

### Docker
The application is containerized using Docker to ensure consistency across different environments.

### Orchestration
Kubernetes is used for orchestrating containerized applications, managing deployments, scaling, and ensuring high availability.
