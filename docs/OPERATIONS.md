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

## Security checklist
- Never store exchange keys in frontend.
- Rotate JWT secret and encryption key.
- Limit IPs in Upbit API key whitelist.
