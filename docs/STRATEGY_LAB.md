# Strategy Lab (Continuous Backtest Loop)

실시간 자동매매(`btc-backend.service`)와 별개로, 백테스트를 주기적으로 반복 실행해
전략 추천값을 누적/요약하는 백그라운드 프로세스입니다.

핵심 목표:
- 라이브 매매는 계속 유지
- 별도 프로세스가 백테스트 반복
- 결과를 `data/strategy-lab/`에 누적
- 다음 Codex 수정 요청 시 누적 데이터 기반으로 코드/전략값 반영

## 1) 실행 방식

### 단발 실행(테스트)
```bash
python3 scripts/strategy_lab_daemon.py --single-run
```

### 지속 실행(포그라운드)
```bash
python3 scripts/strategy_lab_daemon.py --interval-minutes 60
```

### systemd 설치/시작
```bash
./scripts/strategy_lab_install.sh
```

상태 확인:
```bash
sudo systemctl status btc-strategy-lab.service --no-pager -l
journalctl -u btc-strategy-lab.service -f
```

## 2) 산출물

`data/strategy-lab/` 아래 파일이 생성됩니다.

- `latest.json`
  - 최근 1회 사이클의 상세 결과
- `history.jsonl`
  - 모든 사이클 누적 로그(JSONL)
- `consensus.json`
  - 최근 N회(`--consensus-lookback`)의 중앙값 기반 합의 추천
- `next_codex_request.json`
  - 다음 Codex 코드 수정 요청에 바로 사용 가능한 요약
- `cycles/<timestamp>/report_<profile>.json`
  - 프로필별 원본 백테스트 결과

## 3) 기본 전략

기본적으로 아래 프로필을 순회합니다.
- `BALANCED`
- `CONSERVATIVE`
- `AGGRESSIVE`

각 프로필은 `scripts/backtest.py`를 호출해 walk-forward(`train/test`) + optimize를 수행합니다.

## 4) 안전 원칙

이 프로세스는 **코드를 자동 수정/배포하지 않습니다.**

대신:
- 누적 추천값을 생성
- 사람이 확인 후 반영
- Codex 요청 시 `consensus.json`을 기준으로 코드 수정

이 방식이 라이브 환경에서 가장 안전합니다.

## 5) 추천 워크플로우

1. 밤새 `btc-strategy-lab.service` 실행
2. 아침에 `data/strategy-lab/consensus.json` 확인
3. Codex에 "consensus 기반 전략값 반영" 요청
4. 테스트 후 재배포

## 6) 주요 옵션

```bash
python3 scripts/strategy_lab_daemon.py \
  --market KRW-BTC \
  --days 90 \
  --profiles BALANCED,CONSERVATIVE,AGGRESSIVE \
  --short-unit 3 \
  --mid-unit 15 \
  --max-combos 60 \
  --interval-minutes 60 \
  --consensus-lookback 12
```

