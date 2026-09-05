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
python3 scripts/research/strategy_lab_daemon.py --single-run
```

### 지속 실행(포그라운드)
```bash
python3 scripts/research/strategy_lab_daemon.py --interval-minutes 60
```

### systemd 설치/시작
```bash
./scripts/systemd/install_services.sh --strategy-only
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

각 프로필은 `scripts/research/backtest.py`를 호출해 walk-forward(`train/test`) + optimize를 수행합니다.

## 3-1) 과적합 방지 (중요)

이 루프는 한때 **전략을 스스로 악화시키고 있었습니다.** 7일 창을 매시간, 프로필 3개 × 60조합으로
재채굴하면서 `--min-sell-trades 1`(거래 1건짜리 결과도 채택)로 합의값을 만들었습니다. 그 결과가
익절 기본값을 2.4% → 1.92% → 1.44%로 계속 깎아온 흐름입니다.

바뀐 기본값과 이유:

| 항목 | 이전 | 현재 | 이유 |
|---|---|---|---|
| `--days` | 7 | 180 | 1시간봉 7일 = 168봉, 거래 몇 건. 신호와 잡음을 구분할 수 없음 |
| `--interval-minutes` | 60 | 1440 | 거의 같은 창을 매시간 재최적화하면 합의 표본이 서로 독립이 아님 |
| `--short-unit` / `--mid-unit` | 3 / 15 | 60 / 240 | 엔진 타임프레임과 일치. 3분/15분은 수수료를 넘길 수 없음 |
| `--min-sell-trades` | 1 | 20 | 거래 1건짜리 추천은 소수점 붙은 잡음 |
| `--min-trades-per-day` | 0.15 | 0.02 | 1시간봉 추세 전략은 연 10~25회(≈0.03/일). 기존 하한이 정상 동작을 페널티 |

추가로 `backtest.py`:
- `score_metrics`가 체결 20건 미만 결과를 **순위에서 강등**합니다 (`MIN_TRADES_FOR_SCORING`)
- 최적화 격자가 **실제로 효과가 있는 축만** 탐색합니다. ATR 기반 청산이 켜져 있으면
  `stop_loss_pct`/`trailing_stop_pct`는 엔진에 도달하지 못하므로, 그 축을 넣으면 동일한 백테스트를
  반복할 뿐입니다(5축 중 2축이 no-op → 실효 탐색 공간이 9배 작았음). 대신 ATR 배수를 탐색합니다.

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
python3 scripts/research/strategy_lab_daemon.py \
  --market KRW-BTC \
  --days 90 \
  --profiles BALANCED,CONSERVATIVE,AGGRESSIVE \
  --short-unit 3 \
  --mid-unit 15 \
  --max-combos 60 \
  --interval-minutes 60 \
  --consensus-lookback 12
```
