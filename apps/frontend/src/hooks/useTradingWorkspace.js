import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  DASHBOARD_ROUTE,
  SETTINGS_ROUTE,
} from '../constants/tradingUi.js'
import {
  addMarketRow,
  buildApiErrorMessage,
  buildManualOrderPayload,
  buildMarketOverrideRows,
  buildMarketOverrideSignature,
  buildMarketSuggestions,
  isFilledOrder,
  isValidMarketCode,
  normalizeMarket,
  normalizeMarketCatalog,
  normalizeRatioPresets,
} from '../utils/tradingUi.js'
import {
  panicExitRequest,
  saveMarketOverridesRequest,
  startEngineRequest,
  stopEngineRequest,
} from '../utils/tradingActions.js'
import { ApiError, requestJson } from '../utils/apiClient.js'

const INVENTORY_EPSILON = 1e-12
const FEED_POLL_MIN_MS = 6000
const DECISION_FEED_LIMIT = 60
const isServerReachable = (error) => error instanceof ApiError

const toPositiveNumber = (value) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return null
  }
  return numeric
}

const normalizeOrderSide = (value) => {
  if (value === null || value === undefined) {
    return null
  }
  const normalized = String(value).trim().toUpperCase()
  return normalized === '' ? null : normalized
}

const resolveOrderTimestamp = (order, fallbackIndex) => {
  const requestedAt = Date.parse(order?.requestedAt ?? '')
  if (Number.isFinite(requestedAt)) {
    return requestedAt
  }
  const createdAt = Date.parse(order?.createdAt ?? '')
  if (Number.isFinite(createdAt)) {
    return createdAt
  }
  return fallbackIndex
}

const resolveOrderTradeSnapshot = (order) => {
  const decision = order?.decision
  let quantity = toPositiveNumber(order?.volume) ?? toPositiveNumber(decision?.quantity)
  let funds = toPositiveNumber(order?.funds) ?? toPositiveNumber(decision?.funds)
  let unitPrice = toPositiveNumber(order?.price) ?? toPositiveNumber(decision?.price)

  if (!quantity && funds && unitPrice) {
    quantity = funds / unitPrice
  }
  if (!funds && quantity && unitPrice) {
    funds = quantity * unitPrice
  }
  if (!unitPrice && quantity && funds) {
    unitPrice = funds / quantity
  }

  return { quantity, funds, unitPrice }
}

const appendTradeProfit = (orders) => {
  if (!Array.isArray(orders) || orders.length === 0) {
    return []
  }

  const inventoryByMarket = new Map()
  // Only fills move inventory. Running rejected and cancelled orders through the FIFO invented
  // positions that never existed, which then corrupted the realised P&L of every genuine sell after
  // them — the hidden half of the fabricated 수익 column.
  const wrappedOrders = orders
    .map((order, index) => ({
      index,
      order,
      market: typeof order?.market === 'string' ? order.market : '',
      side: normalizeOrderSide(order?.side),
      sortTime: resolveOrderTimestamp(order, index),
    }))
    .filter(({ order }) => isFilledOrder(order))

  wrappedOrders.sort((left, right) => {
    if (left.sortTime !== right.sortTime) {
      return left.sortTime - right.sortTime
    }
    return left.index - right.index
  })

  const profitByIndex = new Map()

  wrappedOrders.forEach(({ index, order, market, side }) => {
    if (side === 'BUY') {
      const snapshot = resolveOrderTradeSnapshot(order)
      const state = inventoryByMarket.get(market) ?? { quantity: 0, cost: 0 }
      if (snapshot.quantity && snapshot.funds) {
        state.quantity += snapshot.quantity
        state.cost += snapshot.funds
      }
      inventoryByMarket.set(market, state)
      profitByIndex.set(index, 0)
      return
    }

    if (side === 'SELL') {
      const snapshot = resolveOrderTradeSnapshot(order)
      const state = inventoryByMarket.get(market) ?? { quantity: 0, cost: 0 }
      let realizedProfit = null

      if (snapshot.quantity && snapshot.funds && state.quantity > INVENTORY_EPSILON && state.cost > 0) {
        const matchedQuantity = Math.min(snapshot.quantity, state.quantity)
        if (matchedQuantity > INVENTORY_EPSILON) {
          const matchedRatio = matchedQuantity / snapshot.quantity
          const matchedNotional = snapshot.funds * matchedRatio
          const averageCost = state.cost / state.quantity
          const costBasis = averageCost * matchedQuantity
          realizedProfit = matchedNotional - costBasis

          state.quantity -= matchedQuantity
          state.cost -= costBasis

          if (state.quantity <= INVENTORY_EPSILON) {
            state.quantity = 0
            state.cost = 0
          } else if (state.cost < 0) {
            state.cost = 0
          }

          if (Number.isFinite(realizedProfit)) {
            profitByIndex.set(index, realizedProfit)
          } else {
            profitByIndex.set(index, null)
          }
          inventoryByMarket.set(market, state)
          return
        }
      }

      inventoryByMarket.set(market, state)
      profitByIndex.set(index, null)
      return
    }

    profitByIndex.set(index, null)
  })

  return orders.map((order, index) => ({
    ...order,
    tradeProfit: profitByIndex.has(index) ? profitByIndex.get(index) : null,
  }))
}

export function useTradingWorkspace({
  authUser,
  activeRoute,
  bootstrapLoading,
  bootstrapLoaded,
  pollingIntervalMs,
  syncUserMarkets,
}) {
  const [pageVisible, setPageVisible] = useState(() => (
    typeof document === 'undefined' ? true : document.visibilityState !== 'hidden'
  ))
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [serverConnected, setServerConnected] = useState(null)

  const [engineStatus, setEngineStatus] = useState(null)
  const [engineBusy, setEngineBusy] = useState(false)
  const [engineError, setEngineError] = useState(null)

  const [strategy, setStrategy] = useState(null)
  const [strategyError, setStrategyError] = useState(null)
  const [ratioError, setRatioError] = useState(null)
  const [presetError, setPresetError] = useState(null)
  const [ratioPresets, setRatioPresets] = useState([])
  const [selectedRatioPresetByMarket, setSelectedRatioPresetByMarket] = useState({})
  const [marketRows, setMarketRows] = useState([])
  const [marketConfigSaving, setMarketConfigSaving] = useState(false)
  const [marketConfigLoading, setMarketConfigLoading] = useState(false)
  const [marketConfigError, setMarketConfigError] = useState(null)
  const [marketConfigNotice, setMarketConfigNotice] = useState(null)
  const [marketRowsBaseline, setMarketRowsBaseline] = useState('')
  const [newMarketInput, setNewMarketInput] = useState('')
  const [marketCatalog, setMarketCatalog] = useState([])
  const [marketSuggestOpen, setMarketSuggestOpen] = useState(false)
  const [marketSuggestIndex, setMarketSuggestIndex] = useState(0)
  const [expandedMarket, setExpandedMarket] = useState(null)

  const [manualTradeOpen, setManualTradeOpen] = useState(false)
  const [manualTradeMarket, setManualTradeMarket] = useState('')
  const [manualTradeSide, setManualTradeSide] = useState('SELL')
  const [manualTradeType, setManualTradeType] = useState('MARKET')
  const [manualTradePrice, setManualTradePrice] = useState('')
  const [manualTradeVolume, setManualTradeVolume] = useState('')
  const [manualTradeFunds, setManualTradeFunds] = useState('')
  const [manualTradeBusy, setManualTradeBusy] = useState(false)
  const [manualTradeError, setManualTradeError] = useState(null)
  const [manualTradeNotice, setManualTradeNotice] = useState(null)

  const [orderHistory, setOrderHistory] = useState([])
  const [decisionHistory, setDecisionHistory] = useState([])
  const [decisionFeed, setDecisionFeed] = useState([])
  const [panicBusy, setPanicBusy] = useState(false)
  const [tradingMode, setTradingMode] = useState(null)
  const [summaryError, setSummaryError] = useState(null)
  const [orderHistoryError, setOrderHistoryError] = useState(null)
  const [decisionHistoryError, setDecisionHistoryError] = useState(null)
  const isDashboardRoute = activeRoute === DASHBOARD_ROUTE
  const isSettingsRoute = activeRoute === SETTINGS_ROUTE

  useEffect(() => {
    if (typeof document === 'undefined') {
      return undefined
    }
    const handleVisibilityChange = () => {
      setPageVisible(document.visibilityState !== 'hidden')
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [])

  useEffect(() => {
    if (authUser) {
      return
    }
    setLoading(false)
    setServerConnected(null)
    setSummary(null)
    setSummaryError(null)
    setEngineStatus(null)
    setEngineError(null)
    setStrategy(null)
    setStrategyError(null)
    setRatioError(null)
    setPresetError(null)
    setRatioPresets([])
    setSelectedRatioPresetByMarket({})
    setMarketRows([])
    setMarketConfigSaving(false)
    setMarketConfigLoading(false)
    setMarketConfigError(null)
    setMarketConfigNotice(null)
    setMarketRowsBaseline('')
    setNewMarketInput('')
    setMarketCatalog([])
    setMarketSuggestOpen(false)
    setMarketSuggestIndex(0)
    setExpandedMarket(null)
    setOrderHistoryError(null)
    setDecisionHistoryError(null)
    setManualTradeOpen(false)
  }, [authUser])

  const fetchSummary = useCallback(async (isRefresh = false) => {
    if (!isRefresh) {
      setLoading(true)
    }
    try {
      const data = await requestJson('/api/portfolio/summary', {}, '자산 요약 조회 실패')
      setSummary(data)
      setSummaryError(null)
      setServerConnected(true)
    } catch (err) {
      setServerConnected(isServerReachable(err))
      setSummaryError(err?.message ?? '자산 요약 조회 실패')
    } finally {
      if (!isRefresh) {
        setLoading(false)
      }
    }
  }, [])

  const fetchEngineStatus = useCallback(async () => {
    try {
      const data = await requestJson('/api/engine/status', {}, '엔진 상태 조회 실패')
      setEngineStatus(Boolean(data?.running))
      setTradingMode(data?.tradingMode === 'PAPER' ? 'PAPER' : 'LIVE')
      setEngineError(null)
    } catch (err) {
      setEngineError(err?.message ?? '엔진 상태 조회 실패')
    }
  }, [])

  const fetchStrategy = useCallback(async () => {
    setStrategyError(null)
    try {
      const data = await requestJson('/api/strategy', {}, '전략 조회 실패')
      setStrategy(data)
    } catch (err) {
      setStrategyError(err?.message ?? '전략 조회 실패')
    }
  }, [])

  const fetchRatioPresets = useCallback(async () => {
    setPresetError(null)
    try {
      const data = await requestJson('/api/strategy/presets', {}, '프리셋 조회 실패')
      setRatioPresets(normalizeRatioPresets(data))
    } catch (err) {
      setRatioPresets([])
      setPresetError(err?.message ?? '프리셋 조회 실패')
    }
  }, [])

  const fetchOrderHistory = useCallback(async () => {
    try {
      const data = await requestJson('/api/order/history?limit=200', {}, '주문 로그 조회 실패')
      setOrderHistory(Array.isArray(data) ? data : [])
      setOrderHistoryError(null)
    } catch (err) {
      setOrderHistoryError(err?.message ?? '주문 로그 조회 실패')
    }
  }, [])

  const fetchDecisionHistory = useCallback(async () => {
    try {
      // includeSkips=true: the SKIP rows ARE the answer to "the engine is on, why has nothing
      // happened for two hours". They were being discarded server-side by the query parameter and
      // again client-side by the BUY/SELL filter below.
      const data = await requestJson(
        `/api/engine/decisions?limit=${DECISION_FEED_LIMIT}&includeSkips=true`,
        {},
        '의사결정 로그 조회 실패'
      )
      const allItems = Array.isArray(data) ? data : []
      const tradeOnlyItems = allItems.filter((decision) => {
        const action = String(decision?.action ?? '').toUpperCase()
        return action === 'BUY' || action === 'SELL'
      })
      setDecisionFeed(allItems)
      setDecisionHistory(tradeOnlyItems)
      setDecisionHistoryError(null)
    } catch (err) {
      setDecisionHistoryError(err?.message ?? '의사결정 로그 조회 실패')
    }
  }, [])

  // Latest-value refs so the automatic refresh can check for unsaved edits without taking marketRows as
  // a dependency (which would rebuild the callback on every keystroke).
  const pollInFlightRef = useRef({})
  const marketRowsRef = useRef([])
  const marketRowsBaselineRef = useRef('')

  const fetchMarketOverrides = useCallback(async (force = false) => {
    setMarketConfigLoading(true)
    setMarketConfigError(null)
    setMarketConfigNotice(null)
    try {
      const data = await requestJson('/api/strategy/market-overrides', {}, '마켓 설정 조회 실패')
      // Never silently discard unsaved edits. This refresh runs on every route change and every
      // tab-visibility change, so switching browser tabs mid-edit used to wipe the form. The manual
      // reload button already asks before overwriting; the automatic path now respects the same rule.
      const dirty = buildMarketOverrideSignature(marketRowsRef.current) !== marketRowsBaselineRef.current
      if (!force && dirty) {
        return
      }
      const rows = buildMarketOverrideRows(data)
      setMarketRows(rows)
      setMarketRowsBaseline(buildMarketOverrideSignature(rows))
      setNewMarketInput('')
      setSelectedRatioPresetByMarket((prev) => {
        if (!prev || typeof prev !== 'object') {
          return {}
        }
        const next = {}
        rows.forEach((row) => {
          if (row?.market && prev[row.market]) {
            next[row.market] = prev[row.market]
          }
        })
        return next
      })
      setExpandedMarket((prev) => {
        if (prev && rows.some((row) => row.market === prev)) {
          return prev
        }
        return null
      })
      if (typeof syncUserMarkets === 'function') {
        syncUserMarkets(data?.markets)
      }
    } catch (err) {
      setMarketConfigError(err?.message ?? '마켓 설정 조회 실패')
    } finally {
      setMarketConfigLoading(false)
    }
  }, [syncUserMarkets])

  const fetchMarketCatalog = useCallback(async () => {
    try {
      const response = await fetch('/api/market/list?quote=KRW')
      if (!response.ok) {
        throw new Error(`마켓 목록 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setMarketCatalog(normalizeMarketCatalog(data?.markets))
    } catch {
      setMarketCatalog([])
    }
  }, [])

  useEffect(() => {
    const authenticated = Boolean(authUser)
    if (bootstrapLoading || (authenticated && !bootstrapLoaded)) {
      return undefined
    }
    if (!pageVisible) {
      return undefined
    }

    if (isSettingsRoute && authenticated) {
      fetchStrategy()
      fetchRatioPresets()
      fetchMarketOverrides()
      fetchMarketCatalog()
    }

    if (!authenticated) {
      return undefined
    }

    fetchSummary(false)
    fetchEngineStatus()

    if (isDashboardRoute) {
      fetchOrderHistory()
      fetchDecisionHistory()
    }

    // Skip a tick while the previous one is still in flight. The interval floor is 2s, so a slow
    // backend or a slow Upbit call used to queue requests faster than they completed, and a late
    // response could overwrite a newer one — showing a stale portfolio as current.
    const poll = (key, run) => () => {
      if (pollInFlightRef.current[key]) {
        return
      }
      pollInFlightRef.current[key] = true
      Promise.resolve(run()).finally(() => {
        pollInFlightRef.current[key] = false
      })
    }

    const summaryTimer = setInterval(poll('summary', () => fetchSummary(true)), pollingIntervalMs)
    const engineTimer = setInterval(poll('engine', () => fetchEngineStatus()), pollingIntervalMs)
    // The feed is history, not live state: at the 2s floor a 120-row decision payload every tick would
    // hammer the backend and re-render the list for nothing. Poll it on its own slower cadence.
    const feedTimer = isDashboardRoute
      ? setInterval(
        poll('feed', () => Promise.all([fetchOrderHistory(), fetchDecisionHistory()])),
        Math.max(pollingIntervalMs, FEED_POLL_MIN_MS)
      )
      : null

    return () => {
      clearInterval(summaryTimer)
      clearInterval(engineTimer)
      if (feedTimer) {
        clearInterval(feedTimer)
      }
      pollInFlightRef.current = {}
    }
  }, [
    activeRoute,
    authUser,
    bootstrapLoaded,
    bootstrapLoading,
    fetchDecisionHistory,
    fetchEngineStatus,
    fetchMarketCatalog,
    fetchMarketOverrides,
    fetchOrderHistory,
    fetchRatioPresets,
    fetchSummary,
    fetchStrategy,
    isDashboardRoute,
    isSettingsRoute,
    pageVisible,
    pollingIntervalMs,
  ])

  const positions = useMemo(() => {
    if (!summary?.positions) {
      return []
    }
    return [...summary.positions].sort((a, b) => (b?.valuation ?? 0) - (a?.valuation ?? 0))
  }, [summary])

  const cash = summary?.cash
  const totals = summary?.totals
  const updatedAt = summary?.queriedAt
    ? new Date(summary.queriedAt).toLocaleString('ko-KR', { hour12: false })
    : '—'

  const connectionClass = serverConnected === null ? 'checking' : serverConnected ? 'connected' : 'disconnected'
  const connectionLabel = serverConnected === null ? '확인중' : serverConnected ? '연결됨' : '끊김'
  // engineStatus stays null until a status response actually arrives. Rendering that as OFF showed a
  // live engine as stopped and offered to "start" it.
  const engineKnown = engineStatus !== null
  const engineClass = engineStatus ? 'ok' : 'error'
  const marketRowsDirty = useMemo(
    () => buildMarketOverrideSignature(marketRows) !== marketRowsBaseline,
    [marketRows, marketRowsBaseline]
  )

  useEffect(() => {
    marketRowsRef.current = marketRows
    marketRowsBaselineRef.current = marketRowsBaseline
  }, [marketRows, marketRowsBaseline])
  const marketSuggestions = useMemo(
    () => buildMarketSuggestions(newMarketInput, marketCatalog, marketRows),
    [newMarketInput, marketCatalog, marketRows]
  )
  const decisionByOrderId = useMemo(() => {
    const map = new Map()
    decisionHistory.forEach((decision) => {
      const orderId = decision?.orderId
      if (!orderId) {
        return
      }
      const existing = map.get(orderId)
      if (!existing) {
        map.set(orderId, decision)
        return
      }
      const nextAt = Date.parse(decision?.executedAt ?? '')
      const prevAt = Date.parse(existing?.executedAt ?? '')
      if (Number.isFinite(nextAt) && (!Number.isFinite(prevAt) || nextAt > prevAt)) {
        map.set(orderId, decision)
      }
    })
    return map
  }, [decisionHistory])
  const mergedOrderHistory = useMemo(
    () => appendTradeProfit(
      orderHistory.map((order) => ({
        ...order,
        decision: order?.orderId ? decisionByOrderId.get(order.orderId) : null,
      }))
    ),
    [orderHistory, decisionByOrderId]
  )
  const feedError = useMemo(() => {
    const errors = [orderHistoryError, decisionHistoryError].filter(Boolean)
    if (errors.length === 0) {
      return null
    }
    return [...new Set(errors)].join(' / ')
  }, [decisionHistoryError, orderHistoryError])
  const manualTradePosition = useMemo(
    () => positions.find((item) => item.market === manualTradeMarket) ?? null,
    [manualTradeMarket, positions]
  )
  const cashKrw = cash?.total ?? cash?.balance ?? 0

  const handleMarketReload = useCallback(() => {
    if (marketRowsDirty && !window.confirm('저장하지 않은 변경사항이 있습니다. 서버 설정으로 덮어쓸까요?')) {
      return
    }
    fetchMarketOverrides(true)
  }, [fetchMarketOverrides, marketRowsDirty])

  const handleSaveMarketOverrides = useCallback(async () => {
    setMarketConfigSaving(true)
    setMarketConfigError(null)
    setMarketConfigNotice(null)
    try {
      const data = await saveMarketOverridesRequest(marketRows)
      const nextRows = buildMarketOverrideRows(data)
      setMarketRows(nextRows)
      setMarketRowsBaseline(buildMarketOverrideSignature(nextRows))
      setMarketConfigNotice('마켓/설정이 저장되었습니다.')
      if (typeof syncUserMarkets === 'function') {
        syncUserMarkets(data?.markets)
      }
    } catch (err) {
      setMarketConfigError(err?.message ?? '마켓 설정 저장 실패')
    } finally {
      setMarketConfigSaving(false)
    }
  }, [marketRows, syncUserMarkets])

  const handleAddMarket = useCallback(() => {
    const normalized = normalizeMarket(newMarketInput)
    const canExpand = Boolean(
      normalized &&
      isValidMarketCode(normalized) &&
      !marketRows.some((row) => normalizeMarket(row?.market) === normalized)
    )
    addMarketRow(
      newMarketInput,
      marketRows,
      setNewMarketInput,
      setMarketRows,
      setMarketConfigError,
      setMarketConfigNotice,
      {
        maxOrderKrw: strategy?.maxOrderKrw,
        profile: strategy?.profile,
      }
    )
    setMarketSuggestOpen(false)
    setMarketSuggestIndex(0)
    if (canExpand) {
      setExpandedMarket(normalized)
    }
  }, [marketRows, newMarketInput, strategy])

  const handleSelectMarketSuggestion = useCallback((market) => {
    setNewMarketInput(market)
    addMarketRow(
      market,
      marketRows,
      setNewMarketInput,
      setMarketRows,
      setMarketConfigError,
      setMarketConfigNotice,
      {
        maxOrderKrw: strategy?.maxOrderKrw,
        profile: strategy?.profile,
      }
    )
    setMarketSuggestOpen(false)
    setMarketSuggestIndex(0)
    setExpandedMarket(market)
  }, [marketRows, strategy])

  useEffect(() => {
    if (!expandedMarket) {
      return
    }
    const stillExists = marketRows.some((row) => row.market === expandedMarket)
    if (!stillExists) {
      setExpandedMarket(marketRows.length > 0 ? marketRows[0].market : null)
    }
  }, [expandedMarket, marketRows])

  useEffect(() => {
    if (marketRowsDirty && marketConfigNotice) {
      setMarketConfigNotice(null)
    }
  }, [marketRowsDirty, marketConfigNotice])

  const openManualTrade = useCallback((market, side = 'SELL') => {
    const normalizedMarket = normalizeMarket(market)
    if (!normalizedMarket) {
      return
    }
    setManualTradeMarket(normalizedMarket)
    setManualTradeSide(side)
    setManualTradeType('MARKET')
    setManualTradePrice('')
    setManualTradeVolume('')
    setManualTradeFunds('')
    setManualTradeError(null)
    setManualTradeNotice(null)
    setManualTradeOpen(true)
  }, [])

  const closeManualTrade = useCallback(() => {
    if (manualTradeBusy) {
      return
    }
    setManualTradeOpen(false)
    setManualTradeError(null)
  }, [manualTradeBusy])

  const handleManualTradeSubmit = useCallback(async () => {
    const payload = buildManualOrderPayload({
      market: manualTradeMarket,
      side: manualTradeSide,
      type: manualTradeType,
      price: manualTradePrice,
      volume: manualTradeVolume,
      funds: manualTradeFunds,
    })

    setManualTradeBusy(true)
    setManualTradeError(null)
    setManualTradeNotice(null)
    try {
      const response = await fetch('/api/order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (!response.ok) {
        const errorPayload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(errorPayload, `주문 실패 ${response.status}`)
        throw new Error(message)
      }
      const data = await response.json().catch(() => null)
      setManualTradeNotice(`주문 요청 완료 (${data?.requestStatus ?? 'SUBMITTED'})`)
      setManualTradeOpen(false)
      setManualTradePrice('')
      setManualTradeVolume('')
      setManualTradeFunds('')
      fetchOrderHistory()
      fetchDecisionHistory()
      fetchSummary(true)
    } catch (err) {
      setManualTradeError(err?.message ?? '주문 실패')
    } finally {
      setManualTradeBusy(false)
    }
  }, [
    fetchDecisionHistory,
    fetchOrderHistory,
    fetchSummary,
    manualTradeFunds,
    manualTradeMarket,
    manualTradePrice,
    manualTradeSide,
    manualTradeType,
    manualTradeVolume,
  ])

  const handleEngineStart = useCallback(async () => {
    if (!window.confirm('자동매매 엔진을 시작할까요? 실제 주문이 발생할 수 있습니다.')) {
      return
    }
    setEngineBusy(true)
    setEngineError(null)
    try {
      const running = await startEngineRequest()
      setEngineStatus(running)
    } catch (err) {
      setEngineError(err?.message ?? '엔진 시작 실패')
    } finally {
      setEngineBusy(false)
    }
  }, [])

  const handleEngineStop = useCallback(async () => {
    setEngineBusy(true)
    setEngineError(null)
    try {
      const running = await stopEngineRequest()
      setEngineStatus(running)
    } catch (err) {
      setEngineError(err?.message ?? '엔진 중지 실패')
    } finally {
      setEngineBusy(false)
    }
  }, [])

  const handlePanicExit = useCallback(async () => {
    setPanicBusy(true)
    setEngineError(null)
    try {
      const result = await panicExitRequest()
      setEngineStatus(false)
      const submitted = Number(result?.liquidationsSubmitted ?? 0)
      setManualTradeNotice(
        submitted > 0
          ? `긴급 청산: ${submitted}건 시장가 매도 주문을 전송했습니다.`
          : '긴급 청산: 엔진을 중지했습니다. 청산할 포지션이 없습니다.'
      )
      await Promise.all([fetchSummary(true), fetchOrderHistory(), fetchDecisionHistory()])
    } catch (err) {
      setEngineError(err?.message ?? '긴급 청산 실패')
    } finally {
      setPanicBusy(false)
    }
  }, [fetchDecisionHistory, fetchOrderHistory, fetchSummary])

  return {
    loading,
    engineStatus,
    engineKnown,
    engineBusy,
    engineError,
    tradingMode,
    decisionFeed,
    panicBusy,
    handlePanicExit,
    strategy,
    strategyError,
    ratioError,
    presetError,
    ratioPresets,
    selectedRatioPresetByMarket,
    marketRows,
    marketConfigSaving,
    marketConfigLoading,
    marketConfigError,
    marketConfigNotice,
    newMarketInput,
    marketSuggestOpen,
    marketSuggestIndex,
    expandedMarket,
    manualTradeOpen,
    manualTradeMarket,
    manualTradeSide,
    manualTradeType,
    manualTradePrice,
    manualTradeVolume,
    manualTradeFunds,
    manualTradeBusy,
    manualTradeError,
    manualTradeNotice,
    mergedOrderHistory,
    summaryError,
    feedError,
    positions,
    cash,
    totals,
    updatedAt,
    connectionClass,
    connectionLabel,
    engineClass,
    marketRowsDirty,
    marketSuggestions,
    manualTradePosition,
    cashKrw,
    setRatioError,
    setSelectedRatioPresetByMarket,
    setMarketRows,
    setMarketConfigError,
    setMarketConfigNotice,
    setNewMarketInput,
    setMarketSuggestOpen,
    setMarketSuggestIndex,
    setExpandedMarket,
    setManualTradeSide,
    setManualTradeType,
    setManualTradePrice,
    setManualTradeVolume,
    setManualTradeFunds,
    handleSelectMarketSuggestion,
    handleAddMarket,
    handleMarketReload,
    handleSaveMarketOverrides,
    openManualTrade,
    closeManualTrade,
    handleManualTradeSubmit,
    handleEngineStart,
    handleEngineStop,
  }
}
