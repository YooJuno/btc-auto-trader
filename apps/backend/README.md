# Backend Summary (Auto Trader)

이 문서는 백엔드 자동매매/주문 파트의 현재 구조와 동작을 요약합니다.

## 주요 엔드포인트
- `POST /api/order`: 주문 생성 (Upbit 실제 주문 호출)
- `GET /api/order/history`: 최근 주문 로그 조회
- `GET /api/portfolio/summary`: Upbit 계좌/시세 기반 포트폴리오 요약
- `GET /api/market/price`: 단일 마켓 현재가 조회
- `POST /api/engine/start`: 자동매매 엔진 시작
- `POST /api/engine/stop`: 자동매매 엔진 중지
- `GET /api/engine/status`: 자동매매 엔진 상태 조회
- `GET /api/engine/decisions`: 최근 매매 의사결정 로그 조회
- `POST /api/engine/tick`: 자동매매 1회 실행 (수동 트리거)
- `GET /api/strategy`: 전략 설정 조회
- `PUT /api/strategy`: 전략 설정 업데이트
- `PATCH /api/strategy/ratios`: 익절/손절/부분매도 비율 업데이트
- `GET /api/strategy/market-overrides`: 마켓별 cap/profile override 조회
- `PUT /api/strategy/market-overrides`: 마켓별 cap/profile override 전체 저장(교체)

## 주문 처리 흐름
1. `POST /api/order` 수신
2. 입력 검증 및 정규화
3. `clientOrderId`(없으면 자동 생성)를 Upbit `identifier`로 전달
4. DB에 `REQUESTED` 상태로 먼저 저장
5. Upbit 주문 호출
6. 응답 성공 시 Upbit 상태/체결 정보를 반영해 `SUBMITTED` 또는 `FILLED`로 저장
7. `state=cancel`이어도 체결 수량(`executed_volume`)이 있으면 내부 상태를 `FILLED`로 보정
8. 응답에는 Upbit 주문 ID, 내부 상태(`requestStatus`), 에러 메시지 포함

## 주문 복구(리컨실)
- 스케줄러가 `REQUESTED/PENDING/SUBMITTED` 주문을 주기적으로 조회
- `identifier` 기준으로 Upbit 주문 상태를 재조회해 `FILLED/CANCELED/SUBMITTED`로 갱신
- `state=cancel` + 체결 있음(`executed_volume > 0`)은 `FILLED`로 보정
- 오래된 미확정 주문은 타임아웃 처리

설정:
- `orders.reconcile.enabled`
- `orders.reconcile.delay-ms`
- `orders.reconcile.lookback-minutes`
- `orders.reconcile.stale-minutes`
- `orders.pending-window-minutes`

## 자동매매 엔진
### 동작 요약
- `engine.tick-ms` 주기로 동작 (엔진 ON 상태일 때만)
- `/api/engine/tick`은 기본적으로 엔진 ON 상태에서만 실행됨 (`?force=true`로 강제 가능)
- 캔들 기반 MA(단기/장기) + RSI/MACD/돌파 신호로 매수 판단
- 매수: `MA_SHORT > MA_LONG` + 확인 신호(기본 2개 이상) 충족 시 시장가 매수
- 매도: 손절/트레일링 스탑/모멘텀 약화/MA 이탈/익절(부분 익절 가능)
- 프로필(`AGGRESSIVE/BALANCED/CONSERVATIVE`)에 따라 신호 민감도 자동 조정
- 마켓별 프로필 override 지원 (`trading.market-profile`)
- 마켓별 최대 주문 금액 cap 지원 (`trading.market-max-order-krw`)
- 매매 결정(매수/매도/스킵)과 지표 스냅샷을 `trade_decisions` 테이블에 기록
- 변동성 타깃이 설정되어 있으면 주문 금액을 축소
- 최근 주문/대기 중 주문은 재주문 방지
- 장애 발생 시 마켓별 지수 백오프 적용 (한 마켓 장애가 전체를 멈추지 않음)
- tick당 처리할 마켓 수 제한 가능 (`engine.max-markets-per-tick`, 라운드로빈 처리)
- Upbit API 전역 rate-limit 보호(최소 호출 간격/초당/분당 요청량) 적용

### 관련 설정
- `engine.tick-ms`
- `engine.order-cooldown-seconds`
- `engine.failure-backoff-base-seconds`
- `engine.failure-backoff-max-seconds`
- `engine.max-markets-per-tick`
- `trading.markets`
- `trading.market-max-order-krw` (예: `KRW-BTC:12000,KRW-ETH:8000`)
- `trading.market-profile` (예: `KRW-BTC:CONSERVATIVE,KRW-ETH:AGGRESSIVE`)
- `trading.min-krw`
- `signal.timeframe-unit`
- `signal.ma-short`
- `signal.ma-long`
- `signal.rsi-period`
- `signal.rsi-buy-threshold`
- `signal.rsi-sell-threshold`
- `signal.rsi-overbought`
- `signal.macd-fast`
- `signal.macd-slow`
- `signal.macd-signal`
- `signal.breakout-lookback`
- `signal.breakout-pct`
- `signal.max-extension-pct`
- `signal.ma-long-slope-lookback`
- `signal.min-confirmations`
- `risk.trailing-window`
- `risk.partial-take-profit-cooldown-minutes`
- `risk.stop-loss-cooldown-minutes`
- `risk.volatility-window`
- `risk.target-vol-pct`
- `upbit.rate-limit.enabled`
- `upbit.rate-limit.min-interval-ms`
- `upbit.rate-limit.max-requests-per-second`
- `upbit.rate-limit.max-requests-per-minute`

전략 API 값:
- `enabled`, `maxOrderKrw`, `takeProfitPct`, `stopLossPct`, `trailingStopPct`, `partialTakeProfitPct`, `profile`
- `stopExitPct`, `trendExitPct`, `momentumExitPct`

마켓 override API 값:
- `markets`
- `maxOrderKrwByMarket`
- `profileByMarket`

프로필 강제:
- 제거됨. 현재는 전략 API 및 마켓별 override 값이 그대로 적용됩니다.

## 데이터베이스
### 주문 테이블
`orders` 테이블이 주문 상태를 기록합니다.
- `client_order_id`: Upbit `identifier`
- `status`: `REQUESTED`, `PENDING`, `SUBMITTED`, `FILLED`, `CANCELED`, `FAILED`
- `state`: Upbit 상태값
- `raw_request`, `raw_response`, `error_message` 포함

스키마: `infra/db/schema.sql`

### 매매 결정 테이블
`trade_decisions` 테이블에 매매/스킵 사유와 지표 스냅샷을 저장합니다.
스키마: `infra/db/schema.sql`

### 마켓 override 테이블
`strategy_market_overrides` 테이블에 마켓별 cap/profile 설정을 저장합니다.
스키마: `infra/db/schema.sql`

## 실행/운영 메모
- 기본 Upbit API Key(`UPBIT_ACCESS_KEY`, `UPBIT_SECRET_KEY`)는 owner 계정 fallback 용도입니다.
- 일반 로그인 사용자는 `/api/me/exchange-credentials`로 API Key를 저장하며, DB에는 암호화되어 저장됩니다.
- OAuth2 로그인 사용 시 아래 설정 필요
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_<PROVIDER>_CLIENT_ID`
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_<PROVIDER>_CLIENT_SECRET`
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_<PROVIDER>_SCOPE=openid,profile,email`
  - `APP_AUTH_SUCCESS_REDIRECT_URL`
  - `APP_AUTH_FAILURE_REDIRECT_URL`
  - `APP_AUTH_DYNAMIC_REDIRECT_ENABLED` (기본 `true`, 로그인 콜백 요청 호스트 기준 리다이렉트)
  - `APP_AUTH_FRONTEND_PORT` (기본 `5173`, 동적 리다이렉트 시 프론트 포트)
  - `APP_OWNER_EMAIL` (기존 메인 DB 소유자 이메일, 기본 `juno980220@gmail.com`)
  - `APP_TRADING_OWNER_ONLY_MODE` (기본 `true`, owner 계정만 주문/엔진 실행 허용)
  - `APP_EXCHANGE_KEY_ENCRYPTION_KEY` (사용자별 거래소 키 암호화 키)
- `APP_OWNER_EMAIL` 계정은 기본 DB를 사용하고, 신규 로그인 계정은 `btc_user_<user_id>` 데이터베이스를 자동 생성해 분리 저장합니다.
- 엔진 제어/틱(`/api/engine/*`)과 주문/전략/포트폴리오 API는 tenant DB로 라우팅되어 사용자별로 분리됩니다.
