# API (draft)

## Auth
- `POST /api/auth/register`
  - body: `{ tenantName, email, password }`
  - returns: `{ token, user }`

- `POST /api/auth/login`
  - body: `{ email, password }`
  - returns: `{ token, user }`

## Users
- `GET /api/users/me`
  - auth: Bearer token
  - returns: user profile

## Bot configs
- `GET /api/bot-configs/defaults`
  - public
  - returns: default presets and available options

- `POST /api/bot-configs`
  - auth: Bearer token
  - body: `{ name, baseMarket, selectionMode, strategyMode, riskPreset, maxPositions, maxDailyDrawdownPct, maxWeeklyDrawdownPct, autoPickTopN, manualMarkets }`
  - `manualMarkets` format: `KRW-BTC,KRW-ETH` or `BTC,ETH` (prefix auto-applied)

- `GET /api/bot-configs/active`
  - auth: Bearer token
  - returns: latest config for user

## Market
- `GET /api/market/recommendations?topN=5`
  - auth: Bearer token
  - returns: list of recommended markets with scores

## Paper trading
- `GET /api/paper/summary`
  - auth: Bearer token
  - returns: cash, equity, positions
- `POST /api/paper/reset`
  - auth: Bearer token
  - body: `{ initialCash }`
- `GET /api/paper/performance?days=7&weeks=4`
  - auth: Bearer token
  - returns: daily/weekly equity + returns
