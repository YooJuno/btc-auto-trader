import { buildMarketOverridePayload } from './tradingUi.js'
import { requestJson } from './apiClient.js'

export async function startEngineRequest() {
  const data = await requestJson('/api/engine/start', { method: 'POST' }, '엔진 시작 실패')
  return Boolean(data?.running)
}

export async function stopEngineRequest() {
  const data = await requestJson('/api/engine/stop', { method: 'POST' }, '엔진 중지 실패')
  return Boolean(data?.running)
}

// Stops the engine and market-sells every position. Backed by POST /api/engine/panic.
export async function panicExitRequest() {
  return requestJson('/api/engine/panic', { method: 'POST' }, '긴급 청산 실패')
}

export async function saveMarketOverridesRequest(rows) {
  const payload = buildMarketOverridePayload(rows)
  return requestJson('/api/strategy/market-overrides', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }, '마켓 설정 저장 실패')
}
