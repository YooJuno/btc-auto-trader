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
  - body: `{ name, baseMarket, selectionMode, strategyMode, riskPreset, maxPositions, maxDailyDrawdownPct, maxWeeklyDrawdownPct, autoPickTopN }`

- `GET /api/bot-configs/active`
  - auth: Bearer token
  - returns: latest config for user
