# Architecture

## Overview
Single-server MVP designed to split into services later. The trading engine is isolated from the UI by a REST API and a DB boundary.

## Components
- Frontend (React): UI for login, strategy, risk, market selection, monitoring.
- Backend (Spring Boot): Auth, config, risk rules, exchange integration, job orchestration.
- Postgres: Users, tenants, bot configs, trading logs, API keys (encrypted).

## Data flow
1) User logs in -> receives JWT.
2) UI reads defaults and submits bot config.
3) Backend stores config per user/tenant.
4) Strategy engine selects market + strategy based on conditions.
5) Execution engine sends orders to Upbit (later phase).

## Scaling path
- Split UI and API: host frontend separately, keep backend internal.
- Split strategy engine: separate worker service for market data + execution.
- Move DB to managed Postgres for HA.
- Add queue (Redis/Kafka) when order volume grows.
