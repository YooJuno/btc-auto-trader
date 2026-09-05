import {
  ADMIN_USERS_PATH,
  ADMIN_USERS_ROUTE,
  DASHBOARD_ROUTE,
  DEFAULT_MARKET_MAX_ORDER_KRW,
  DEFAULT_MARKET_PROFILE,
  MARKET_CODE_PATTERN,
  PROFILE_PATH,
  PROFILE_ROUTE,
  PROFILE_VALUES,
  RATIO_FIELDS,
  RATIO_FIELD_LABELS,
  SETTINGS_PATH,
  SETTINGS_ROUTE,
  UI_DENSITY_COMFORTABLE,
  UI_DENSITY_COMPACT,
  UI_REFRESH_MAX_SEC,
  UI_REFRESH_MIN_SEC,
  UI_SCOPE_COMMON,
  UI_SCOPE_DESKTOP,
  UI_SCOPE_MOBILE,
} from '../constants/tradingUi.js'

export const resolveAppRoute = (pathname) => {
  const normalizedPath = normalizePathname(pathname)
  if (normalizedPath === ADMIN_USERS_PATH) {
    return ADMIN_USERS_ROUTE
  }
  if (normalizedPath === PROFILE_PATH) {
    return PROFILE_ROUTE
  }
  if (normalizedPath === SETTINGS_PATH) {
    return SETTINGS_ROUTE
  }
  return DASHBOARD_ROUTE
}

export const resolveAppPath = (route) => {
  if (route === ADMIN_USERS_ROUTE) {
    return ADMIN_USERS_PATH
  }
  if (route === PROFILE_ROUTE) {
    return PROFILE_PATH
  }
  if (route === SETTINGS_ROUTE) {
    return SETTINGS_PATH
  }
  return '/'
}

const createEmptyRatioFields = () => ({
  takeProfitPct: '',
  stopLossPct: '',
  trailingStopPct: '',
  partialTakeProfitPct: '',
  stopExitPct: '',
  trendExitPct: '',
  momentumExitPct: '',
})

const normalizeRatioInputOrNull = (market, field, value) => {
  const raw = `${value ?? ''}`.trim()
  if (raw === '') {
    return null
  }
  const numeric = Number(raw)
  if (Number.isNaN(numeric) || numeric < 0 || numeric > 100) {
    const label = RATIO_FIELD_LABELS[field] ?? field
    throw new Error(`${market} ${label} 값은 0~100 사이여야 합니다.`)
  }
  return numeric
}

export const resolvePresetDisplayName = (presets, code) => {
  if (!code) {
    return ''
  }
  const found = Array.isArray(presets) ? presets.find((preset) => preset.code === code) : null
  return found?.displayName ?? code
}

export const updateMarketOverrideInput = (setMarketRows, market, field, value) => {
  setMarketRows((prev) => prev.map((row) => {
    if (row.market !== market) {
      return row
    }
    return {
      ...row,
      [field]: value,
    }
  }))
}

export const applyRatioPresetToMarket = (
  preset,
  market,
  setMarketRows,
  setSelectedRatioPresetByMarket,
  setRatioError
) => {
  if (!preset || !preset.code || !market) {
    return
  }
  const normalized = normalizeMarket(market)
  if (!normalized) {
    return
  }
  setMarketRows((prev) => prev.map((row) => {
    if (row.market !== normalized) {
      return row
    }
    return {
      ...row,
      takeProfitPct: toInputValue(preset.takeProfitPct),
      stopLossPct: toInputValue(preset.stopLossPct),
      trailingStopPct: toInputValue(preset.trailingStopPct),
      partialTakeProfitPct: toInputValue(preset.partialTakeProfitPct),
      stopExitPct: toInputValue(preset.stopExitPct),
      trendExitPct: toInputValue(preset.trendExitPct),
      momentumExitPct: toInputValue(preset.momentumExitPct),
    }
  }))
  setSelectedRatioPresetByMarket((prev) => ({
    ...prev,
    [normalized]: preset.code,
  }))
  setRatioError(null)
}

export const clearMarketRatioOverrides = (
  setMarketRows,
  market,
  setSelectedRatioPresetByMarket,
  setRatioError
) => {
  const normalized = normalizeMarket(market)
  if (!normalized) {
    return
  }
  setMarketRows((prev) => prev.map((row) => {
    if (row.market !== normalized) {
      return row
    }
    return {
      ...row,
      ...createEmptyRatioFields(),
    }
  }))
  setSelectedRatioPresetByMarket((prev) => {
    if (!prev || !Object.prototype.hasOwnProperty.call(prev, normalized)) {
      return prev
    }
    const next = { ...prev }
    delete next[normalized]
    return next
  })
  setRatioError(null)
}

export const addMarketRow = (
  input,
  rows,
  setNewMarketInput,
  setMarketRows,
  setMarketConfigError,
  setMarketConfigNotice,
  defaults = {}
) => {
  const market = normalizeMarket(input)
  if (!market) {
    setMarketConfigError('마켓 코드를 입력해주세요. 예: KRW-ETH')
    return
  }
  if (!isValidMarketCode(market)) {
    setMarketConfigError('마켓 코드 형식이 올바르지 않습니다. 예: KRW-BTC')
    return
  }
  if (Array.isArray(rows) && rows.some((row) => normalizeMarket(row?.market) === market)) {
    setMarketConfigError(`${market} 는 이미 추가되어 있습니다.`)
    return
  }

  const defaultMaxOrderKrw = Number.isFinite(Number(defaults?.maxOrderKrw))
    ? String(Number(defaults.maxOrderKrw))
    : DEFAULT_MARKET_MAX_ORDER_KRW
  const defaultProfile = normalizeProfileValue(defaults?.profile) || DEFAULT_MARKET_PROFILE

  setMarketRows((prev) => [...prev, {
    market,
    maxOrderKrw: defaultMaxOrderKrw,
    profile: defaultProfile,
    tradePaused: false,
    ...createEmptyRatioFields(),
  }])
  setNewMarketInput('')
  setMarketConfigError(null)
  setMarketConfigNotice(null)
}

export const removeMarketRow = (
  setMarketRows,
  market,
  setMarketConfigNotice,
  setMarketConfigError,
  setSelectedRatioPresetByMarket
) => {
  const normalized = normalizeMarket(market)
  if (!normalized) {
    return
  }
  setMarketRows((prev) => prev.filter((row) => normalizeMarket(row?.market) !== normalized))
  if (typeof setSelectedRatioPresetByMarket === 'function') {
    setSelectedRatioPresetByMarket((prev) => {
      if (!prev || !Object.prototype.hasOwnProperty.call(prev, normalized)) {
        return prev
      }
      const next = { ...prev }
      delete next[normalized]
      return next
    })
  }
  setMarketConfigNotice(null)
  setMarketConfigError(null)
}

export const buildManualOrderPayload = ({ market, side, type, price, volume, funds }) => {
  const normalizedMarket = normalizeMarket(market)
  if (!normalizedMarket) {
    throw new Error('마켓 정보가 없습니다.')
  }
  const normalizedSide = String(side ?? '').trim().toUpperCase()
  if (normalizedSide !== 'BUY' && normalizedSide !== 'SELL') {
    throw new Error('매수/매도 구분을 확인해주세요.')
  }
  const normalizedType = String(type ?? '').trim().toUpperCase()
  if (normalizedType !== 'MARKET' && normalizedType !== 'LIMIT') {
    throw new Error('주문 방식을 확인해주세요.')
  }

  const payload = {
    market: normalizedMarket,
    side: normalizedSide,
    type: normalizedType,
  }

  if (normalizedType === 'MARKET' && normalizedSide === 'BUY') {
    payload.funds = parseRequiredPositiveNumber(funds, '매수 금액')
    return payload
  }
  if (normalizedType === 'MARKET' && normalizedSide === 'SELL') {
    payload.volume = parseRequiredPositiveNumber(volume, '매도 수량')
    return payload
  }

  payload.price = parseRequiredPositiveNumber(price, '지정가')
  payload.volume = parseRequiredPositiveNumber(volume, '수량')
  return payload
}

export const normalizeRatioPresets = (payload) => {
  if (!Array.isArray(payload)) {
    return []
  }
  return payload
    .map((item) => {
      const code = normalizePresetCode(item?.code)
      const displayName = typeof item?.displayName === 'string' && item.displayName.trim() ? item.displayName.trim() : code
      if (!code) {
        return null
      }
      return {
        code,
        displayName,
        takeProfitPct: item?.takeProfitPct,
        stopLossPct: item?.stopLossPct,
        trailingStopPct: item?.trailingStopPct,
        partialTakeProfitPct: item?.partialTakeProfitPct,
        stopExitPct: item?.stopExitPct,
        trendExitPct: item?.trendExitPct,
        momentumExitPct: item?.momentumExitPct,
      }
    })
    .filter(Boolean)
}

export const normalizeMarketCatalog = (payload) => {
  if (!Array.isArray(payload)) {
    return []
  }
  return payload
    .map((item) => {
      const market = normalizeMarket(item?.market)
      if (!market || !isValidMarketCode(market)) {
        return null
      }
      const ticker = market.includes('-') ? market.split('-')[1] : market
      const koreanName = typeof item?.koreanName === 'string' ? item.koreanName.trim() : ''
      const englishName = typeof item?.englishName === 'string' ? item.englishName.trim() : ''
      return {
        market,
        ticker,
        koreanName,
        englishName,
      }
    })
    .filter(Boolean)
}

export const buildMarketSuggestions = (input, catalog, rows, limit = 8) => {
  const keyword = `${input ?? ''}`.trim()
  if (keyword === '' || !Array.isArray(catalog) || catalog.length === 0) {
    return []
  }

  const lowerKeyword = keyword.toLowerCase()
  const existing = new Set(
    Array.isArray(rows)
      ? rows.map((row) => normalizeMarket(row?.market)).filter(Boolean)
      : []
  )

  const scored = []
  catalog.forEach((item) => {
    if (!item?.market || existing.has(item.market)) {
      return
    }
    const marketLower = item.market.toLowerCase()
    const tickerLower = `${item.ticker ?? ''}`.toLowerCase()
    const englishLower = `${item.englishName ?? ''}`.toLowerCase()
    const koreanRaw = `${item.koreanName ?? ''}`

    let score = null
    if (marketLower === lowerKeyword || tickerLower === lowerKeyword) {
      score = 0
    } else if (marketLower.startsWith(lowerKeyword) || tickerLower.startsWith(lowerKeyword)) {
      score = 1
    } else if (marketLower.includes(lowerKeyword) || tickerLower.includes(lowerKeyword)) {
      score = 2
    } else if (englishLower.includes(lowerKeyword)) {
      score = 3
    } else if (koreanRaw.includes(keyword)) {
      score = 4
    }

    if (score === null) {
      return
    }

    scored.push({ ...item, score })
  })

  return scored
    .sort((a, b) => {
      if (a.score !== b.score) {
        return a.score - b.score
      }
      return a.market.localeCompare(b.market)
    })
    .slice(0, Math.max(1, limit))
}

export const buildMarketOverrideRows = (payload) => {
  const configuredMarkets = Array.isArray(payload?.markets) ? payload.markets : []
  const maxOrderKrwByMarket = payload?.maxOrderKrwByMarket ?? {}
  const profileByMarket = payload?.profileByMarket ?? {}
  const tradePausedByMarket = payload?.tradePausedByMarket ?? {}
  const ratiosByMarket = payload?.ratiosByMarket ?? {}

  const orderedMarkets = []
  const seen = new Set()
  configuredMarkets.forEach((market) => {
    const normalized = normalizeMarket(market)
    if (!normalized || seen.has(normalized)) {
      return
    }
    seen.add(normalized)
    orderedMarkets.push(normalized)
  })
  Object.keys(maxOrderKrwByMarket).forEach((market) => {
    const normalized = normalizeMarket(market)
    if (!normalized || seen.has(normalized)) {
      return
    }
    seen.add(normalized)
    orderedMarkets.push(normalized)
  })
  Object.keys(profileByMarket).forEach((market) => {
    const normalized = normalizeMarket(market)
    if (!normalized || seen.has(normalized)) {
      return
    }
    seen.add(normalized)
    orderedMarkets.push(normalized)
  })
  Object.keys(tradePausedByMarket).forEach((market) => {
    const normalized = normalizeMarket(market)
    if (!normalized || seen.has(normalized)) {
      return
    }
    seen.add(normalized)
    orderedMarkets.push(normalized)
  })
  Object.keys(ratiosByMarket).forEach((market) => {
    const normalized = normalizeMarket(market)
    if (!normalized || seen.has(normalized)) {
      return
    }
    seen.add(normalized)
    orderedMarkets.push(normalized)
  })

  return orderedMarkets.map((market) => ({
    market,
    maxOrderKrw: toInputValue(maxOrderKrwByMarket?.[market] ?? DEFAULT_MARKET_MAX_ORDER_KRW),
    profile: normalizeProfileValue(profileByMarket?.[market]) || DEFAULT_MARKET_PROFILE,
    tradePaused: Boolean(tradePausedByMarket?.[market]),
    takeProfitPct: toInputValue(ratiosByMarket?.[market]?.takeProfitPct),
    stopLossPct: toInputValue(ratiosByMarket?.[market]?.stopLossPct),
    trailingStopPct: toInputValue(ratiosByMarket?.[market]?.trailingStopPct),
    partialTakeProfitPct: toInputValue(ratiosByMarket?.[market]?.partialTakeProfitPct),
    stopExitPct: toInputValue(ratiosByMarket?.[market]?.stopExitPct),
    trendExitPct: toInputValue(ratiosByMarket?.[market]?.trendExitPct),
    momentumExitPct: toInputValue(ratiosByMarket?.[market]?.momentumExitPct),
  }))
}

export const buildMarketOverridePayload = (rows) => {
  const payload = {
    markets: [],
    maxOrderKrwByMarket: {},
    profileByMarket: {},
    tradePausedByMarket: {},
    ratiosByMarket: {},
  }
  if (!Array.isArray(rows)) {
    return payload
  }

  const seen = new Set()
  rows.forEach((row) => {
    const market = normalizeMarket(row?.market)
    if (!market || seen.has(market)) {
      return
    }
    seen.add(market)
    payload.markets.push(market)

    const maxOrderKrw = `${row?.maxOrderKrw ?? ''}`.trim()
    if (maxOrderKrw !== '') {
      const value = Number(maxOrderKrw)
      if (Number.isNaN(value) || value <= 0) {
        throw new Error(`${market} 최대 매수 KRW는 0보다 커야 합니다.`)
      }
      payload.maxOrderKrwByMarket[market] = value
    }

    const profile = normalizeProfileValue(row?.profile)
    if (profile !== '') {
      payload.profileByMarket[market] = profile
    }

    payload.tradePausedByMarket[market] = Boolean(row?.tradePaused)

    const ratioPayload = {}
    RATIO_FIELDS.forEach((field) => {
      const normalized = normalizeRatioInputOrNull(market, field, row?.[field])
      if (normalized !== null) {
        ratioPayload[field] = normalized
      }
    })
    if (Object.keys(ratioPayload).length > 0) {
      payload.ratiosByMarket[market] = ratioPayload
    }
  })

  return payload
}

export const parseUserMarketsInput = (value) => {
  if (typeof value !== 'string' || value.trim() === '') {
    return []
  }

  const parsed = []
  const seen = new Set()
  value.split(',').forEach((token) => {
    const market = normalizeMarket(token)
    if (!market) {
      return
    }
    if (!isValidMarketCode(market)) {
      throw new Error(`${token.trim()} 마켓 코드 형식이 올바르지 않습니다. 예: KRW-BTC`)
    }
    if (seen.has(market)) {
      return
    }
    seen.add(market)
    parsed.push(market)
  })
  return parsed
}

export const buildMarketOverrideSignature = (rows) => {
  if (!Array.isArray(rows)) {
    return ''
  }
  const normalized = rows
    .map((row) => {
      const market = normalizeMarket(row?.market)
      if (!market) {
        return null
      }
      return {
        market,
        maxOrderKrw: normalizeCapForSignature(row?.maxOrderKrw),
        profile: normalizeProfileValue(row?.profile),
        tradePaused: Boolean(row?.tradePaused),
        takeProfitPct: normalizeCapForSignature(row?.takeProfitPct),
        stopLossPct: normalizeCapForSignature(row?.stopLossPct),
        trailingStopPct: normalizeCapForSignature(row?.trailingStopPct),
        partialTakeProfitPct: normalizeCapForSignature(row?.partialTakeProfitPct),
        stopExitPct: normalizeCapForSignature(row?.stopExitPct),
        trendExitPct: normalizeCapForSignature(row?.trendExitPct),
        momentumExitPct: normalizeCapForSignature(row?.momentumExitPct),
      }
    })
    .filter((row) => row !== null)
  return JSON.stringify(normalized)
}

export const buildApiErrorMessage = (payload, fallback) => {
  if (!payload || typeof payload !== 'object') {
    return fallback
  }
  const base = typeof payload.error === 'string' && payload.error.trim() !== '' ? payload.error.trim() : fallback
  const fields = payload.fields
  if (!fields || typeof fields !== 'object') {
    return base
  }
  const details = Object.entries(fields)
    .map(([field, message]) => `${field}: ${String(message)}`)
    .join(', ')
  if (details === '') {
    return base
  }
  return `${base} (${details})`
}

export const formatOrderStatus = (requestStatus, state) => {
  const primary = normalizeOrderStatusToken(requestStatus)
  const secondary = normalizeOrderStatusToken(state)
  if (!primary && !secondary) {
    return '-'
  }
  if (!secondary) {
    return primary
  }
  if (!primary) {
    return secondary
  }
  if (primary === secondary) {
    return primary
  }
  if (primary === 'FILLED' && secondary === 'CANCEL') {
    return primary
  }
  return `${primary} (${secondary})`
}

export const detectDeviceKind = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return UI_SCOPE_DESKTOP
  }
  return window.matchMedia('(max-width: 860px)').matches ? UI_SCOPE_MOBILE : UI_SCOPE_DESKTOP
}

export const normalizePathname = (pathname) => {
  if (typeof pathname !== 'string') {
    return '/'
  }
  const trimmed = pathname.trim()
  if (trimmed === '') {
    return '/'
  }
  const withLeadingSlash = trimmed.startsWith('/') ? trimmed : `/${trimmed}`
  return withLeadingSlash.replace(/\/+$/, '') || '/'
}

export const normalizeRouteToken = (value, fallback = DASHBOARD_ROUTE) => {
  if (value === DASHBOARD_ROUTE || value === SETTINGS_ROUTE || value === PROFILE_ROUTE) {
    return value
  }
  if (value === null || value === undefined) {
    return fallback
  }
  const normalized = String(value).trim().toLowerCase()
  if (normalized === SETTINGS_ROUTE) {
    return SETTINGS_ROUTE
  }
  if (normalized === PROFILE_ROUTE) {
    return PROFILE_ROUTE
  }
  if (normalized === DASHBOARD_ROUTE) {
    return DASHBOARD_ROUTE
  }
  return fallback
}

export const normalizeTableDensity = (value, fallback = UI_DENSITY_COMFORTABLE) => {
  if (value === UI_DENSITY_COMFORTABLE || value === UI_DENSITY_COMPACT) {
    return value
  }
  if (value === null || value === undefined) {
    return fallback
  }
  const normalized = String(value).trim().toLowerCase()
  if (normalized === UI_DENSITY_COMPACT) {
    return UI_DENSITY_COMPACT
  }
  if (normalized === UI_DENSITY_COMFORTABLE) {
    return UI_DENSITY_COMFORTABLE
  }
  return fallback
}

export const normalizeRefreshSeconds = (value, fallback = UI_REFRESH_MIN_SEC) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return fallback
  }
  const rounded = Math.round(numeric)
  return Math.min(UI_REFRESH_MAX_SEC, Math.max(UI_REFRESH_MIN_SEC, rounded))
}

export const normalizePollingIntervalMs = (refreshSec) => normalizeRefreshSeconds(refreshSec, UI_REFRESH_MIN_SEC) * 1000

export const buildUiPrefsPayload = (source) => {
  const base = isPlainObject(source) ? { ...source } : {}
  const legacyRefreshSec = normalizeRefreshSeconds(base.refreshSec, null)
  const legacyDefaultRoute = normalizeRouteToken(base.defaultRoute, null)
  const legacyDensity = normalizeTableDensity(base.tableDensity, null)

  const common = { ...pickUiPrefScope(base, UI_SCOPE_COMMON) }
  const mobile = { ...pickUiPrefScope(base, UI_SCOPE_MOBILE) }
  const desktop = { ...pickUiPrefScope(base, UI_SCOPE_DESKTOP) }

  const normalizedCommonRoute = normalizeRouteToken(common.defaultRoute, legacyDefaultRoute)
  const normalizedCommonDensity = normalizeTableDensity(common.tableDensity, legacyDensity)

  common.refreshSec = normalizeRefreshSeconds(common.refreshSec ?? legacyRefreshSec, UI_REFRESH_MIN_SEC)
  common.defaultRoute = normalizeRouteToken(normalizedCommonRoute, DASHBOARD_ROUTE)
  common.tableDensity = normalizeTableDensity(normalizedCommonDensity, UI_DENSITY_COMFORTABLE)

  mobile.defaultRoute = normalizeRouteToken(mobile.defaultRoute ?? common.defaultRoute, DASHBOARD_ROUTE)
  mobile.tableDensity = normalizeTableDensity(
    mobile.tableDensity ?? common.tableDensity,
    UI_DENSITY_COMPACT
  )

  desktop.defaultRoute = normalizeRouteToken(desktop.defaultRoute ?? common.defaultRoute, DASHBOARD_ROUTE)
  desktop.tableDensity = normalizeTableDensity(
    desktop.tableDensity ?? common.tableDensity,
    UI_DENSITY_COMFORTABLE
  )

  delete base.refreshSec
  delete base.defaultRoute
  delete base.tableDensity

  return {
    ...base,
    [UI_SCOPE_COMMON]: common,
    [UI_SCOPE_MOBILE]: mobile,
    [UI_SCOPE_DESKTOP]: desktop,
  }
}

export const resolveEffectiveUiPrefs = (source, deviceKind) => {
  const normalized = buildUiPrefsPayload(source)
  const scope = deviceKind === UI_SCOPE_MOBILE ? UI_SCOPE_MOBILE : UI_SCOPE_DESKTOP
  const common = pickUiPrefScope(normalized, UI_SCOPE_COMMON)
  const scoped = pickUiPrefScope(normalized, scope)
  const densityFallback = scope === UI_SCOPE_MOBILE ? UI_DENSITY_COMPACT : UI_DENSITY_COMFORTABLE
  return {
    refreshSec: normalizeRefreshSeconds(scoped.refreshSec ?? common.refreshSec, UI_REFRESH_MIN_SEC),
    defaultRoute: normalizeRouteToken(scoped.defaultRoute ?? common.defaultRoute, DASHBOARD_ROUTE),
    tableDensity: normalizeTableDensity(scoped.tableDensity ?? common.tableDensity, densityFallback),
  }
}

export const updateUiPrefsSectionValue = (source, scope, key, value) => {
  const normalized = buildUiPrefsPayload(source)
  const safeScope = scope === UI_SCOPE_MOBILE || scope === UI_SCOPE_DESKTOP ? scope : UI_SCOPE_COMMON
  const next = {
    ...normalized,
    [safeScope]: {
      ...pickUiPrefScope(normalized, safeScope),
      [key]: value,
    },
  }
  return buildUiPrefsPayload(next)
}

const isPlainObject = (value) => {
  if (value === null || typeof value !== 'object') {
    return false
  }
  return !Array.isArray(value)
}

export const normalizeProfileValue = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === '') {
    return ''
  }
  return PROFILE_VALUES.includes(normalized) ? normalized : ''
}

const normalizePresetCode = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === '') {
    return ''
  }
  return normalized
}

export const normalizeMarket = (value) => {
  if (value === null || value === undefined) {
    return null
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === '') {
    return null
  }
  return normalized
}

export const isValidMarketCode = (value) => MARKET_CODE_PATTERN.test(value)

export const toInputValue = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
}

export const formatKRW = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 0 })
}

export const formatCoin = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toLocaleString('en-US', { maximumFractionDigits: 8 })
}

export const formatPercent = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return `${(Number(value) * 100).toFixed(2)}%`
}


export const formatDateTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleString('ko-KR', { hour12: false })
}

// Time-only form for the decision feed, where the date is almost always today and the column is narrow.
export const formatTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleTimeString('ko-KR', { hour12: false })
}


export const pnlClass = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'neutral'
  }
  if (Number(value) > 0) {
    return 'positive'
  }
  if (Number(value) < 0) {
    return 'negative'
  }
  return 'neutral'
}

const parseRequiredPositiveNumber = (rawValue, label) => {
  const raw = `${rawValue ?? ''}`.trim()
  if (raw === '') {
    throw new Error(`${label} 값을 입력해주세요.`)
  }
  const numeric = Number(raw)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    throw new Error(`${label} 값은 0보다 커야 합니다.`)
  }
  return numeric
}

const normalizeCapForSignature = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  const raw = String(value).trim()
  if (raw === '') {
    return ''
  }
  const numeric = Number(raw)
  return Number.isFinite(numeric) ? String(numeric) : raw
}

const normalizeOrderStatusToken = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  const token = String(value).trim().toUpperCase()
  if (!token) {
    return ''
  }
  if (token === 'WAIT') {
    return 'SUBMITTED'
  }
  if (token === 'DONE') {
    return 'FILLED'
  }
  if (token === 'CANCEL') {
    return 'CANCELED'
  }
  return token
}

const pickUiPrefScope = (source, scope) => {
  if (!isPlainObject(source)) {
    return {}
  }
  const candidate = source?.[scope]
  return isPlainObject(candidate) ? candidate : {}
}
