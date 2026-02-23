import {
  buildApiErrorMessage,
  buildMarketListPayload,
  buildMarketOverridePayload,
  buildMarketOverrideRows,
} from './tradingUi.js'

export async function startEngineRequest() {
  const response = await fetch('/api/engine/start', { method: 'POST' })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    const message = buildApiErrorMessage(payload, `엔진 시작 실패 ${response.status}`)
    throw new Error(message)
  }
  const data = await response.json()
  return Boolean(data?.running)
}

export async function stopEngineRequest() {
  const response = await fetch('/api/engine/stop', { method: 'POST' })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    const message = buildApiErrorMessage(payload, `엔진 중지 실패 ${response.status}`)
    throw new Error(message)
  }
  const data = await response.json()
  return Boolean(data?.running)
}

export async function saveMarketOverridesRequest(rows) {
  const marketsPayload = buildMarketListPayload(rows)
  const marketResponse = await fetch('/api/strategy/markets', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(marketsPayload),
  })
  if (!marketResponse.ok) {
    const errorPayload = await marketResponse.json().catch(() => null)
    const message = buildApiErrorMessage(errorPayload, `마켓 저장 실패 ${marketResponse.status}`)
    throw new Error(message)
  }

  const payload = buildMarketOverridePayload(rows)
  const response = await fetch('/api/strategy/market-overrides', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    const errorPayload = await response.json().catch(() => null)
    const message = buildApiErrorMessage(errorPayload, `저장 실패 ${response.status}`)
    throw new Error(message)
  }

  const data = await response.json()
  return buildMarketOverrideRows(data)
}
