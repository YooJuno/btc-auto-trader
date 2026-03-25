# Backtest Guide

`./scripts/research/backtest.py`는 현재 자동매매 로직과 동일한 핵심 규칙(진입/청산/쿨다운/가드/비용)을 기준으로 전략을 검증하는 도구입니다.

## 1) 빠른 실행

```bash
python3 scripts/research/backtest.py --days 30
```

기본 동작:
- 타임프레임 2개 비교: `short=3m`, `mid=15m`
- `train/test` 분할: `70/30`
- 출력: 전략 성능 + Buy&Hold 비교 + 추천 타임프레임

## 2) 파라미터 탐색 포함 검증

```bash
python3 scripts/research/backtest.py --days 30 --optimize --max-combos 120
```

- 탐색은 기본 파라미터 주변만 제한적으로 수행합니다.
- 점수는 `ROI`, `MDD`, `Sharpe`, 거래빈도 범위를 함께 고려합니다.
- 탐색은 **train 구간**에서만 수행하고, 최종 판단은 **test 구간**으로 확인합니다.

## 3) 결과 저장

```bash
python3 scripts/research/backtest.py --days 30 --optimize --export data/backtest/report_30d.json
```

## 4) 주요 옵션

- `--market KRW-BTC` 대상 마켓
- `--days 30` 조회 기간(일)
- `--short-unit 3 --mid-unit 15` 비교 타임프레임
- `--split-ratio 0.7` train/test 비율
- `--profile BALANCED|AGGRESSIVE|CONSERVATIVE`
- `--refresh-cache` 캐시 무시 후 재수집
- `--show-trades` 체결 로그 일부 미리보기

## 5) 지표 해석 우선순위

`test` 구간 기준으로 아래 순서로 보세요.

1. `alpha_test_pct`가 0보다 큰가 (Buy&Hold 대비 초과수익)
2. `max_drawdown_pct`가 허용 범위 안인가
3. `trades_per_day`가 너무 과소/과다하지 않은가 (권장 0.2~5)
4. `profit_factor`, `win_rate_pct`, `expectancy_krw`가 일관적인가

## 6) 검증 시 주의사항

- 백테스트는 미래를 보장하지 않습니다.
- 단일 기간/단일 마켓만 보면 과최적화 위험이 큽니다.
- 최소한 아래를 함께 확인하세요:
  - 기간 확장: `30d`, `90d`, `180d`
  - 타임프레임 교차: `3m`, `5m`, `15m`
  - train/test 분할 변경: `0.6`, `0.7`, `0.8`

## 7) 권장 루틴

1. `--optimize --max-combos 120`으로 후보 1차 선정
2. `--optimize` 없이 동일 파라미터 재실행(재현성 확인)
3. 기간 늘려 재검증 (`--days 90`, `--days 180`)
4. 결과가 흔들리면 파라미터를 원복하고 재탐색 범위를 줄여 재실행
