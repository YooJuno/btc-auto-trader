# BTC Auto Trader

Upbit 기반 KRW 중심 자동매매 콘솔.  
Spring Boot 백엔드 + React 프론트 + PostgreSQL로 구성된 모노레포입니다.

---

## ✅ 핵심 기능
- 실시간 추천(거래대금/추세/변동성 기반)
- 모의계좌(Paper Trading) 포트폴리오/손익 표시
- 자동매매 설정(전략/리스크/선정 방식)

## 🧰 Docker 기본 명령어 모음

### 컨테이너 상태 확인
```bash
docker ps
docker ps -a
```

### 이미지 확인
```bash
docker images
```

### Docker Compose (실행/중지)
```bash
docker compose up -d
docker compose down
```

### Docker Compose 상태/로그
```bash
docker compose ps
docker compose logs -f
```

### 단일 컨테이너 로그 확인
```bash
docker logs -f <container_name_or_id>
```

### 컨테이너 내부 접속
```bash
docker exec -it <container_name_or_id> /bin/bash
```

### 컨테이너 시작/중지
```bash
docker start <container_name_or_id>
docker stop <container_name_or_id>
```

### 사용하지 않는 리소스 정리 (주의)
```bash
docker system prune
```
