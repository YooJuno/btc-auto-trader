import { buildApiErrorMessage } from './tradingUi.js'

export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

async function parsePayload(response) {
  const raw = await response.text().catch(() => '')
  if (!raw || raw.trim() === '') {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch {
    return { error: raw.trim() }
  }
}

export async function requestJson(url, options = {}, fallbackMessage = '요청 실패') {
  const response = await fetch(url, options)
  const payload = await parsePayload(response)
  if (!response.ok) {
    const message = buildApiErrorMessage(payload, `${fallbackMessage} ${response.status}`)
    throw new ApiError(message, response.status, payload)
  }
  return payload
}
