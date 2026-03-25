# Scripts

운영 스크립트는 역할별로 분리합니다.

- `deploy/`: 배포와 재시작
  - `deploy_app.sh`: backend/frontend 빌드 후 systemd 유닛 반영 및 재시작
- `research/`: 백테스트와 전략 탐색
  - `backtest.py`
  - `strategy_lab_daemon.py`
- `systemd/`: 서비스 정의와 설치
  - `btc-backend.service`
  - `btc-frontend.service`
  - `btc-strategy-lab.service`
  - `install_services.sh`

권장 진입점:

- 배포: `./scripts/deploy/deploy_app.sh`
- 앱 서비스 설치: `./scripts/systemd/install_services.sh`
- 전략 연구 서비스 포함 설치: `./scripts/systemd/install_services.sh --with-strategy-lab`
- 전략 연구 서비스만 설치: `./scripts/systemd/install_services.sh --strategy-only`
