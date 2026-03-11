# Cloudflare + Caddy 443 적용 가이드

이 문서는 외부 진입 포트를 `443`으로 고정하고, 내부 애플리케이션은 기존 포트(`frontend:5173`, `backend:8080`)를 유지하는 배포 절차를 정리합니다.

## 1) 목표 구조

- 사용자 -> `https://app.your-domain.com` (443)
- Cloudflare -> Caddy (원본 서버 80/443)
- Caddy -> 프론트 `127.0.0.1:5173`
- Caddy -> 백엔드 `127.0.0.1:8080` (`/api`, `/oauth2`, `/login`, `/logout`)

## 2) Cloudflare 설정

- DNS `A` 레코드에 `app.your-domain.com -> 서버 공인 IP` 추가
- 최초 인증서 발급 시에는 `DNS only`(회색 구름) 권장
- 원본 서버 적용 확인 뒤 `Proxied`(주황 구름) 전환
- SSL/TLS 모드 `Full (strict)` 설정

## 3) 서버 준비

```bash
sudo apt update
sudo apt install -y caddy
```

방화벽 사용 중이면 80/443 허용:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

## 4) Caddy 적용

프로젝트 루트에서 실행:

```bash
./apps/infra/caddy/setup.sh app.your-domain.com you@example.com
```

포트를 바꿔야 하면 환경변수로 지정:

```bash
FRONTEND_PORT=5173 BACKEND_PORT=8080 ./apps/infra/caddy/setup.sh app.your-domain.com you@example.com
```

스크립트 동작:

- `/etc/caddy/Caddyfile` 백업 생성
- 새 Caddyfile 적용
- 설정 검증 후 `caddy.service` reload

## 5) 애플리케이션 환경변수

`.env`에서 아래 값 확인:

```dotenv
APP_AUTH_DYNAMIC_REDIRECT_ENABLED=true
APP_AUTH_FRONTEND_PORT=443
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://app.your-domain.com/login/oauth2/code/google
```

`APP_AUTH_FRONTEND_PORT=443`는 OAuth 로그인 후 프론트 리다이렉트 포트를 HTTPS 표준 포트에 맞추기 위한 값입니다.

## 6) 앱 재빌드/재시작

```bash
./scripts/deploy/deploy_app.sh
```

필요 시 상태 확인:

```bash
sudo systemctl status caddy.service --no-pager -l
sudo systemctl status btc-backend.service btc-frontend.service --no-pager -l
```

## 7) 동작 검증

```bash
curl -I https://app.your-domain.com
curl -I "https://app.your-domain.com/api/market/price?coin=BTC"
```

체크 포인트:

- 사이트가 HTTPS로 열리는지
- API 요청이 Caddy를 통해 백엔드로 전달되는지
- OAuth 로그인/리다이렉트가 `https://app.your-domain.com` 기준으로 동작하는지

## 8) 장애 대응

- 인증서 발급 실패: Cloudflare 프록시를 잠시 `DNS only`로 전환 후 재시도
- 502/504: `btc-frontend.service`, `btc-backend.service`가 실행 중인지 확인
- 로그인 리다이렉트 포트 오류: `.env`의 `APP_AUTH_FRONTEND_PORT=443` 재확인
- `/api/*` 요청이 `400`이면: Caddy `reverse_proxy`의 수동 `header_up` 오버라이드를 제거하고 `./apps/infra/caddy/setup.sh ...`로 재적용
