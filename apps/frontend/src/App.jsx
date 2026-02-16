import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import './App.css'

const PROFILE_VALUES = ['BALANCED', 'AGGRESSIVE', 'CONSERVATIVE']
const DEFAULT_MARKET_MAX_ORDER_KRW = '30000'
const DEFAULT_MARKET_PROFILE = 'BALANCED'
const MARKET_CODE_PATTERN = /^[A-Z]{2,10}-[A-Z0-9]{2,15}$/
const RATIO_FIELDS = [
  'takeProfitPct',
  'stopLossPct',
  'trailingStopPct',
  'partialTakeProfitPct',
  'stopExitPct',
  'trendExitPct',
  'momentumExitPct',
]
const RATIO_FIELD_LABELS = {
  takeProfitPct: '익절 %',
  stopLossPct: '손절 %',
  trailingStopPct: '트레일링 %',
  partialTakeProfitPct: '부분 익절 %',
  stopExitPct: '손절/트레일링 매도 %',
  trendExitPct: '추세 이탈 매도 %',
  momentumExitPct: '모멘텀 역전 매도 %',
}
const ALERT_SEED_LIMIT = 4
const ALERT_MAX_SIZE = 12

function App() {
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
  const [feedError, setFeedError] = useState(null)
  const [_alerts, setAlerts] = useState([])
  const [performance, setPerformance] = useState(null)
  const [performanceMode, setPerformanceMode] = useState('range')
  const [performanceInputs, setPerformanceInputs] = useState(buildDefaultPerformanceInputs)
  const [performanceLoading, setPerformanceLoading] = useState(false)
  const [performanceError, setPerformanceError] = useState(null)
  const [authChecking, setAuthChecking] = useState(true)
  const [authUser, setAuthUser] = useState(null)
  const [authProviders, setAuthProviders] = useState([])
  const [authError, setAuthError] = useState(null)
  const [settingsLoading, setSettingsLoading] = useState(false)
  const [settingsSaving, setSettingsSaving] = useState(false)
  const [userSettings, setUserSettings] = useState(null)
  const [userSettingsError, setUserSettingsError] = useState(null)
  const [userSettingsNotice, setUserSettingsNotice] = useState(null)
  const [userRiskProfile, setUserRiskProfile] = useState(DEFAULT_MARKET_PROFILE)
  const [userMarketsInput, setUserMarketsInput] = useState('')
  const [userUiPrefs, setUserUiPrefs] = useState({})
  const [exchangeCredentialStatus, setExchangeCredentialStatus] = useState(null)
  const [exchangeCredentialLoading, setExchangeCredentialLoading] = useState(false)
  const [exchangeCredentialSaving, setExchangeCredentialSaving] = useState(false)
  const [exchangeCredentialVerifying, setExchangeCredentialVerifying] = useState(false)
  const [exchangeCredentialError, setExchangeCredentialError] = useState(null)
  const [exchangeCredentialNotice, setExchangeCredentialNotice] = useState(null)
  const [exchangeAccessKeyInput, setExchangeAccessKeyInput] = useState('')
  const [exchangeSecretKeyInput, setExchangeSecretKeyInput] = useState('')
  const lastOrderIdRef = useRef(null)
  const lastDecisionIdRef = useRef(null)

  const fetchAuthProviders = useCallback(async () => {
    try {
      const response = await fetch('/api/auth/providers')
      if (!response.ok) {
        throw new Error(`로그인 공급자 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setAuthProviders(Array.isArray(data) ? data : [])
    } catch {
      setAuthProviders([])
    }
  }, [])

  const checkAuthSession = useCallback(async () => {
    setAuthChecking(true)
    try {
      const response = await fetch('/api/me')
      if (response.status === 401) {
        setAuthUser(null)
        return
      }
      if (!response.ok) {
        throw new Error(`로그인 상태 확인 오류 ${response.status}`)
      }
      const data = await response.json()
      setAuthUser(data)
    } catch (err) {
      setAuthUser(null)
      setAuthError(err?.message ?? '로그인 상태 확인 실패')
    } finally {
      setAuthChecking(false)
    }
  }, [])

  const fetchMySettings = useCallback(async () => {
    if (!authUser) {
      return
    }
    setSettingsLoading(true)
    setUserSettingsError(null)
    setUserSettingsNotice(null)
    try {
      const response = await fetch('/api/me/settings')
      if (!response.ok) {
        throw new Error(`내 설정 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      const markets = Array.isArray(data?.markets) ? data.markets.map(normalizeMarket).filter(Boolean) : []

      setUserSettings(data)
      setUserRiskProfile(normalizeProfileValue(data?.riskProfile) || DEFAULT_MARKET_PROFILE)
      setUserMarketsInput(markets.join(', '))
      setUserUiPrefs(isPlainObject(data?.uiPrefs) ? data.uiPrefs : {})
    } catch (err) {
      setUserSettingsError(err?.message ?? '내 설정 조회 실패')
    } finally {
      setSettingsLoading(false)
    }
  }, [authUser])

  const fetchExchangeCredentialStatus = useCallback(async () => {
    if (!authUser) {
      return
    }
    setExchangeCredentialLoading(true)
    setExchangeCredentialError(null)
    try {
      const response = await fetch('/api/me/exchange-credentials')
      if (!response.ok) {
        throw new Error(`거래소 키 상태 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setExchangeCredentialStatus(data)
    } catch (err) {
      setExchangeCredentialStatus(null)
      setExchangeCredentialError(err?.message ?? '거래소 키 상태 조회 실패')
    } finally {
      setExchangeCredentialLoading(false)
    }
  }, [authUser])

  const handleProviderLogin = useCallback((authorizationUrl) => {
    if (!authorizationUrl) {
      return
    }
    window.location.assign(authorizationUrl)
  }, [])

  const handleLogout = useCallback(async () => {
    try {
      await fetch('/api/auth/logout', { method: 'POST' })
    } catch {
      // no-op
    }

    setAuthUser(null)
    setAuthError(null)
    setUserSettings(null)
    setUserSettingsError(null)
    setUserSettingsNotice(null)
    setUserRiskProfile(DEFAULT_MARKET_PROFILE)
    setUserMarketsInput('')
    setUserUiPrefs({})
    setExchangeCredentialStatus(null)
    setExchangeCredentialError(null)
    setExchangeCredentialNotice(null)
    setExchangeAccessKeyInput('')
    setExchangeSecretKeyInput('')
    fetchAuthProviders()
    checkAuthSession()
  }, [checkAuthSession, fetchAuthProviders])

  const handleSaveMySettings = useCallback(async () => {
    setSettingsSaving(true)
    setUserSettingsError(null)
    setUserSettingsNotice(null)
    try {
      const payload = {
        markets: parseUserMarketsInput(userMarketsInput),
        riskProfile: normalizeProfileValue(userRiskProfile) || DEFAULT_MARKET_PROFILE,
        uiPrefs: isPlainObject(userUiPrefs) ? userUiPrefs : {},
      }
      const response = await fetch('/api/me/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (!response.ok) {
        const errorPayload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(errorPayload, `내 설정 저장 오류 ${response.status}`)
        throw new Error(message)
      }
      const data = await response.json()
      const markets = Array.isArray(data?.markets) ? data.markets.map(normalizeMarket).filter(Boolean) : []
      setUserSettings(data)
      setUserRiskProfile(normalizeProfileValue(data?.riskProfile) || DEFAULT_MARKET_PROFILE)
      setUserMarketsInput(markets.join(', '))
      setUserUiPrefs(isPlainObject(data?.uiPrefs) ? data.uiPrefs : {})
      setUserSettingsNotice('내 인터페이스 설정을 저장했습니다.')
    } catch (err) {
      setUserSettingsError(err?.message ?? '내 설정 저장 실패')
    } finally {
      setSettingsSaving(false)
    }
  }, [userMarketsInput, userRiskProfile, userUiPrefs])

  const handleSaveExchangeCredentials = useCallback(async () => {
    if (!exchangeAccessKeyInput.trim() || !exchangeSecretKeyInput.trim()) {
      setExchangeCredentialError('access key와 secret key를 모두 입력해주세요.')
      return
    }

    setExchangeCredentialSaving(true)
    setExchangeCredentialError(null)
    setExchangeCredentialNotice(null)
    try {
      const response = await fetch('/api/me/exchange-credentials', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          accessKey: exchangeAccessKeyInput.trim(),
          secretKey: exchangeSecretKeyInput.trim(),
        }),
      })
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(payload, `거래소 키 저장 실패 ${response.status}`)
        throw new Error(message)
      }
      const data = await response.json()
      setExchangeCredentialStatus(data)
      setExchangeSecretKeyInput('')
      setExchangeCredentialNotice('거래소 API 키를 저장했습니다.')
    } catch (err) {
      setExchangeCredentialError(err?.message ?? '거래소 키 저장 실패')
    } finally {
      setExchangeCredentialSaving(false)
    }
  }, [exchangeAccessKeyInput, exchangeSecretKeyInput])

  const handleVerifyExchangeCredentials = useCallback(async () => {
    setExchangeCredentialVerifying(true)
    setExchangeCredentialError(null)
    setExchangeCredentialNotice(null)
    try {
      const response = await fetch('/api/me/exchange-credentials/verify', { method: 'POST' })
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(payload, `거래소 키 검증 실패 ${response.status}`)
        throw new Error(message)
      }
      const data = await response.json()
      const accountCount = Number.isFinite(data?.accountCount) ? data.accountCount : 0
      setExchangeCredentialNotice(`거래소 키 검증 성공 (${accountCount}개 계좌 조회)`)
      fetchExchangeCredentialStatus()
    } catch (err) {
      setExchangeCredentialError(err?.message ?? '거래소 키 검증 실패')
    } finally {
      setExchangeCredentialVerifying(false)
    }
  }, [fetchExchangeCredentialStatus])

  const handleDeleteExchangeCredentials = useCallback(async () => {
    if (!window.confirm('저장된 거래소 API 키를 삭제할까요?')) {
      return
    }
    setExchangeCredentialSaving(true)
    setExchangeCredentialError(null)
    setExchangeCredentialNotice(null)
    try {
      const response = await fetch('/api/me/exchange-credentials', { method: 'DELETE' })
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(payload, `거래소 키 삭제 실패 ${response.status}`)
        throw new Error(message)
      }
      setExchangeCredentialStatus(null)
      setExchangeAccessKeyInput('')
      setExchangeSecretKeyInput('')
      setExchangeCredentialNotice('저장된 거래소 API 키를 삭제했습니다.')
      fetchExchangeCredentialStatus()
    } catch (err) {
      setExchangeCredentialError(err?.message ?? '거래소 키 삭제 실패')
    } finally {
      setExchangeCredentialSaving(false)
    }
  }, [fetchExchangeCredentialStatus])

  const fetchSummary = useCallback(async (isRefresh = false) => {
    if (!isRefresh) {
      setLoading(true)
    }
    try {
      const response = await fetch('/api/portfolio/summary')
      if (!response.ok) {
        throw new Error(`서버 응답 ${response.status}`)
      }
      const data = await response.json()
      setSummary(data)
      setServerConnected(true)
    } catch {
      setServerConnected(false)
    } finally {
      if (!isRefresh) {
        setLoading(false)
      }
    }
  }, [])

  const fetchEngineStatus = useCallback(async () => {
    try {
      const response = await fetch('/api/engine/status')
      if (!response.ok) {
        throw new Error(`엔진 상태 오류 ${response.status}`)
      }
      const data = await response.json()
      setEngineStatus(Boolean(data?.running))
    } catch (err) {
      setEngineError(err?.message ?? '엔진 상태 조회 실패')
    }
  }, [])

  const fetchStrategy = useCallback(async () => {
    setStrategyError(null)
    try {
      const response = await fetch('/api/strategy')
      if (!response.ok) {
        throw new Error(`전략 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setStrategy(data)
    } catch (err) {
      setStrategyError(err?.message ?? '전략 조회 실패')
    }
  }, [])

  const fetchRatioPresets = useCallback(async () => {
    setPresetError(null)
    try {
      const response = await fetch('/api/strategy/presets')
      if (!response.ok) {
        throw new Error(`프리셋 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setRatioPresets(normalizeRatioPresets(data))
    } catch (err) {
      setRatioPresets([])
      setPresetError(err?.message ?? '프리셋 조회 실패')
    }
  }, [])

  const fetchOrderHistory = useCallback(async () => {
    try {
      const response = await fetch('/api/order/history?limit=30')
      if (!response.ok) {
        throw new Error(`주문 로그 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setOrderHistory(Array.isArray(data) ? data : [])
      appendOrderAlerts(data, lastOrderIdRef, setAlerts, { seedInitial: true })
      setFeedError(null)
    } catch (err) {
      setFeedError(err?.message ?? '주문 로그 조회 실패')
    }
  }, [])

  const fetchDecisionHistory = useCallback(async () => {
    try {
      const response = await fetch('/api/engine/decisions?limit=30&includeSkips=false')
      if (!response.ok) {
        throw new Error(`의사결정 로그 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      const allItems = Array.isArray(data) ? data : []
      const tradeOnlyItems = allItems.filter((decision) => {
        const action = String(decision?.action ?? '').toUpperCase()
        return action === 'BUY' || action === 'SELL'
      })
      setDecisionHistory(tradeOnlyItems)
      appendDecisionAlerts(tradeOnlyItems, lastDecisionIdRef, setAlerts, { seedInitial: true })
      setFeedError(null)
    } catch (err) {
      setFeedError(err?.message ?? '의사결정 로그 조회 실패')
    }
  }, [])

  const fetchMarketOverrides = useCallback(async () => {
    setMarketConfigLoading(true)
    setMarketConfigError(null)
    setMarketConfigNotice(null)
    try {
      const response = await fetch('/api/strategy/market-overrides')
      if (!response.ok) {
        throw new Error(`마켓 설정 조회 오류 ${response.status}`)
      }
      const data = await response.json()
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
    } catch (err) {
      setMarketConfigError(err?.message ?? '마켓 설정 조회 실패')
    } finally {
      setMarketConfigLoading(false)
    }
  }, [])

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

  const fetchPerformance = useCallback(async (mode = performanceMode, inputs = performanceInputs) => {
    setPerformanceLoading(true)
    setPerformanceError(null)
    try {
      const query = buildPerformanceQuery(mode, inputs)
      const response = await fetch(`/api/portfolio/performance?${query}`)
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(payload, `성과 조회 실패 ${response.status}`)
        throw new Error(message)
      }
      const data = await response.json()
      setPerformance(data)
    } catch (err) {
      setPerformanceError(err?.message ?? '성과 조회 실패')
    } finally {
      setPerformanceLoading(false)
    }
  }, [performanceInputs, performanceMode])

  useEffect(() => {
    const query = new URLSearchParams(window.location.search)
    if (query.get('loginError') === 'true') {
      setAuthError('OAuth 로그인에 실패했습니다. 다시 시도해주세요.')
    } else {
      setAuthError(null)
    }
    fetchAuthProviders()
    checkAuthSession()
  }, [checkAuthSession, fetchAuthProviders])

  useEffect(() => {
    if (!authUser) {
      setUserSettings(null)
      setExchangeCredentialStatus(null)
      return
    }
    fetchMySettings()
    fetchExchangeCredentialStatus()
  }, [authUser, fetchMySettings, fetchExchangeCredentialStatus])

  useEffect(() => {
    if (!authUser) {
      return undefined
    }
    fetchSummary(false)
    fetchEngineStatus()
    fetchStrategy()
    fetchRatioPresets()
    fetchMarketOverrides()
    fetchMarketCatalog()
    fetchOrderHistory()
    fetchDecisionHistory()
    fetchPerformance()

    const summaryTimer = setInterval(() => fetchSummary(true), 2000)
    const engineTimer = setInterval(() => fetchEngineStatus(), 2000)
    const feedTimer = setInterval(() => {
      fetchOrderHistory()
      fetchDecisionHistory()
    }, 3000)

    return () => {
      clearInterval(summaryTimer)
      clearInterval(engineTimer)
      clearInterval(feedTimer)
    }
  }, [authUser, fetchSummary, fetchEngineStatus, fetchStrategy, fetchRatioPresets, fetchMarketOverrides, fetchMarketCatalog, fetchOrderHistory, fetchDecisionHistory, fetchPerformance])

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
  const engineLabel = engineStatus ? 'ON' : 'OFF'
  const engineClass = engineStatus ? 'ok' : 'error'
  const marketRowsDirty = useMemo(
    () => buildMarketOverrideSignature(marketRows) !== marketRowsBaseline,
    [marketRows, marketRowsBaseline]
  )
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
    () =>
      orderHistory.map((order) => ({
        ...order,
        decision: order?.orderId ? decisionByOrderId.get(order.orderId) : null,
      })),
    [orderHistory, decisionByOrderId]
  )
  const performanceTotal = performance?.total
  const manualTradePosition = useMemo(
    () => positions.find((item) => item.market === manualTradeMarket) ?? null,
    [manualTradeMarket, positions]
  )
  const cashKrw = cash?.total ?? cash?.balance ?? 0

  const handleMarketReload = useCallback(() => {
    if (marketRowsDirty && !window.confirm('저장하지 않은 변경사항이 있습니다. 서버 설정으로 덮어쓸까요?')) {
      return
    }
    fetchMarketOverrides()
  }, [fetchMarketOverrides, marketRowsDirty])

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
      setMarketConfigNotice
    )
    setMarketSuggestOpen(false)
    setMarketSuggestIndex(0)
    if (canExpand) {
      setExpandedMarket(normalized)
    }
  }, [marketRows, newMarketInput])

  const handleSelectMarketSuggestion = useCallback((market) => {
    setNewMarketInput(market)
    addMarketRow(
      market,
      marketRows,
      setNewMarketInput,
      setMarketRows,
      setMarketConfigError,
      setMarketConfigNotice
    )
    setMarketSuggestOpen(false)
    setMarketSuggestIndex(0)
    setExpandedMarket(market)
  }, [marketRows])

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

  if (authChecking) {
    return (
      <div className="auth-gate">
        <div className="auth-gate__card">
          <p className="eyebrow">BTC AUTO TRADER</p>
          <h2>로그인 상태 확인 중</h2>
          <p className="sub">세션을 확인하고 있습니다.</p>
        </div>
      </div>
    )
  }

  if (!authUser) {
    return (
      <div className="auth-gate">
        <div className="auth-gate__card">
          <p className="eyebrow">BTC AUTO TRADER</p>
          <h2>로그인이 필요합니다</h2>
          <p className="sub">OAuth 로그인 후 사용자별 인터페이스 설정을 불러옵니다.</p>
          {authError && <p className="status-error">{authError}</p>}
          {authProviders.length === 0 ? (
            <p className="status-error">사용 가능한 OAuth 공급자가 없습니다. 백엔드 OAuth 설정을 확인해주세요.</p>
          ) : (
            <div className="button-row auth-provider-row">
              {authProviders.map((provider) => (
                <button
                  key={provider.id}
                  className="primary-button"
                  type="button"
                  onClick={() => handleProviderLogin(provider.authorizationUrl)}
                >
                  {provider.name} 로그인
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="app">
      <header className="app__header">
        <div className="brand-block">
          <p className="eyebrow">BTC AUTO TRADER</p>
          <h1>Sundal</h1>
          <p className="sub">Work Day & Night for your financial free life</p>
        </div>
        <div className="engine-inline-card">
          <div className="engine-inline-head">
            <strong>자동매매</strong>
            <span className={`status ${engineClass}`}>ENGINE {engineLabel}</span>
          </div>
          {engineError && <p className="status-error">{engineError}</p>}
          <div className="engine-inline-actions">
            <button
              className="primary-button"
              onClick={() => handleEngineStart(setEngineStatus, setEngineError, setEngineBusy)}
              disabled={engineBusy || engineStatus}
            >
              엔진 시작
            </button>
            <button
              className="danger-button"
              onClick={() => handleEngineStop(setEngineStatus, setEngineError, setEngineBusy)}
              disabled={engineBusy || !engineStatus}
            >
              엔진 중지
            </button>
          </div>
        </div>
        <div className="status-card">
          <div className="status-row">
            <span>업데이트</span>
            <strong className="mono">{updatedAt}</strong>
          </div>
          <div className="status-row">
            <span>사용자</span>
            <strong className="mono">
              {authUser.email || authUser.displayName || `${authUser.provider}:${authUser.providerUserId}`}
            </strong>
          </div>
          <div className="status-connection-row">
            <span>서버 연결</span>
            <span className={`connection-badge ${connectionClass}`}>{connectionLabel}</span>
          </div>
          <div className="status-actions">
            <button className="ghost-button" type="button" onClick={handleLogout}>
              로그아웃
            </button>
          </div>
        </div>
      </header>

      <section className="summary-grid">
        <div className="summary-card">
          <h3>보유 KRW</h3>
          <p className="summary-value mono">
            {formatKRW(cash?.total)} <span>KRW</span>
          </p>
          <p className="summary-sub">
            사용 가능 {formatKRW(cash?.balance)} / 예약 {formatKRW(cash?.locked)}
          </p>
        </div>
        <div className="summary-card">
          <h3>코인 평가금액</h3>
          <p className="summary-value mono">
            {formatKRW(totals?.positionValue)} <span>KRW</span>
          </p>
          <p className="summary-sub">매입 {formatKRW(totals?.positionCost)}</p>
        </div>
        <div className="summary-card">
          <h3>포지션 수익</h3>
          <p className={`summary-value mono ${pnlClass(totals?.positionPnl)}`}>
            {formatKRW(totals?.positionPnl)} <span>KRW</span>
          </p>
          <p className="summary-sub">{formatPercent(totals?.positionPnlRate)}</p>
        </div>
        <div className="summary-card">
          <h3>총 자산</h3>
          <p className="summary-value mono">
            {formatKRW(totals?.totalAsset)} <span>KRW</span>
          </p>
          <p className="summary-sub">현금 + 코인 평가 합계</p>
        </div>
      </section>

      <section className="workspace-grid">
        <div className="workspace-main">
          <section className="table-card table-card--elevated positions-card">
            <div className="table-header">
              <div>
                <h2>보유 코인</h2>
                <p className="sub">현재가 기준 평가와 수익률을 표시합니다.</p>
              </div>
            </div>
            {manualTradeNotice && <p className="status-success">{manualTradeNotice}</p>}
            {loading ? (
              <div className="empty-state">데이터를 불러오는 중입니다…</div>
            ) : positions.length === 0 ? (
              <div className="empty-state">보유 중인 코인이 없습니다.</div>
            ) : (
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>마켓</th>
                      <th>보유수량</th>
                      <th>평균 매수</th>
                      <th>현재가</th>
                      <th>평가금액</th>
                      <th>수익</th>
                      <th>수익률</th>
                      <th>매매</th>
                    </tr>
                  </thead>
                  <tbody>
                    {positions.map((position) => (
                      <tr key={position.market}>
                        <td>
                          <div className="market">
                            <span className="market__coin">{position.currency}</span>
                            <span className="market__pair">{position.market}</span>
                          </div>
                        </td>
                        <td className="mono">{formatCoin(position.quantity)}</td>
                        <td className="mono">{formatKRW(position.avgBuyPrice)}</td>
                        <td className="mono">{formatKRW(position.currentPrice)}</td>
                        <td className="mono">{formatKRW(position.valuation)}</td>
                        <td className={`mono ${pnlClass(position.pnl)}`}>{formatKRW(position.pnl)}</td>
                        <td className={`mono ${pnlClass(position.pnl)}`}>{formatPercent(position.pnlRate)}</td>
                        <td>
                          <button
                            className="table-action-button"
                            type="button"
                            onClick={() => openManualTrade(position.market, 'SELL')}
                          >
                            매매
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* <article className="table-card card--elevated feed-card">
            <div className="table-header">
              <div>
                <h2>실시간 알림</h2>
                <p className="sub">최근 체결/의사결정 이벤트</p>
              </div>
            </div>
            {feedError && <p className="status-error">{feedError}</p>}
            {alerts.length === 0 ? (
              <div className="empty-state">새 알림이 없습니다.</div>
            ) : (
              <ul className="alert-list">
                {alerts.map((alert) => (
                  <li key={alert.id} className={`alert-item ${alert.tone}`}>
                    <div>
                      <strong>{alert.message}</strong>
                      <p className="sub compact">{alert.meta}</p>
                    </div>
                    <span className="mono small">{formatTime(alert.time)}</span>
                  </li>
                ))}
              </ul>
            )}
          </article> */}

          <article className="table-card card--elevated order-card">
            <div className="table-header">
              <div>
                <h2>최근 주문 로그</h2>
                <p className="sub">주문 상태 + 매매 사유/지표 스냅샷 통합</p>
              </div>
            </div>
            {feedError && <p className="status-error">{feedError}</p>}
            {mergedOrderHistory.length === 0 ? (
              <div className="empty-state">주문 로그가 없습니다.</div>
            ) : (
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>시간</th>
                      <th>마켓</th>
                      <th>사이드</th>
                      <th>상태</th>
                      <th>주문량</th>
                      <th>주문금액</th>
                      <th>사유</th>
                      <th>RSI</th>
                      <th>MACD</th>
                      <th>MA Slope%</th>
                      <th>가격</th>
                      <th>프로필</th>
                      <th>오류</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mergedOrderHistory.map((order) => {
                      const decision = order.decision
                      return (
                        <tr key={order.id}>
                          <td className="mono small">{formatDateTime(order.requestedAt)}</td>
                          <td>{order.market}</td>
                          <td className={order.side === 'BUY' ? 'positive' : 'negative'}>{order.side}</td>
                          <td>
                            <span className="mono">{formatOrderStatus(order.requestStatus, order.state)}</span>
                          </td>
                          <td className="mono">{formatCoin(order.volume)}</td>
                          <td className="mono">{formatKRW(order.funds)}</td>
                          <td className="mono small">{decision?.reason ?? '-'}</td>
                          <td className="mono">{formatFixed(decision?.rsi, 2)}</td>
                          <td className="mono">{formatFixed(decision?.macdHistogram, 4)}</td>
                          <td className="mono">{formatFixed(decision?.maLongSlopePct, 3)}</td>
                          <td className="mono">{formatKRW(decision?.price)}</td>
                          <td>{decision?.profile ?? '-'}</td>
                          <td className="mono small">{truncateText(order.errorMessage, 36)}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </article>
        </div>

        <aside className="workspace-side">
          <article className="control-card card--elevated auth-settings-card">
            <div className="card-head">
              <div>
                <h2>내 인터페이스 설정</h2>
                <p className="sub">로그인 사용자별 기본 리스크 프로필과 관심 마켓 목록을 저장합니다.</p>
              </div>
              <span className="pill">USER</span>
            </div>
            {userSettingsError && <p className="status-error">{userSettingsError}</p>}
            {userSettingsNotice && <p className="status-success">{userSettingsNotice}</p>}
            <div className="form-grid auth-settings-grid">
              <label className="form-field">
                <span>리스크 프로필</span>
                <select
                  value={userRiskProfile}
                  onChange={(event) => setUserRiskProfile(event.target.value)}
                  disabled={settingsLoading || settingsSaving}
                >
                  {PROFILE_VALUES.map((profile) => (
                    <option key={profile} value={profile}>
                      {profile}
                    </option>
                  ))}
                </select>
              </label>
              <label className="form-field">
                <span>관심 마켓</span>
                <input
                  type="text"
                  value={userMarketsInput}
                  onChange={(event) => setUserMarketsInput(event.target.value)}
                  placeholder="예: KRW-BTC, KRW-ETH"
                  disabled={settingsLoading || settingsSaving}
                />
              </label>
            </div>
            <p className="sub compact">마켓 코드는 쉼표로 구분해 입력하세요. 형식 예: KRW-BTC</p>
            <div className="button-row">
              <button
                className="primary-button"
                type="button"
                onClick={handleSaveMySettings}
                disabled={settingsLoading || settingsSaving}
              >
                {settingsSaving ? '저장 중...' : '내 설정 저장'}
              </button>
              <button
                className="ghost-button"
                type="button"
                onClick={fetchMySettings}
                disabled={settingsLoading || settingsSaving}
              >
                {settingsLoading ? '불러오는 중...' : '다시 불러오기'}
              </button>
            </div>
            {userSettings?.updatedAt && (
              <p className="sub compact">마지막 저장 {formatDateTime(userSettings.updatedAt)}</p>
            )}
          </article>

          <article className="control-card card--elevated auth-settings-card">
            <div className="card-head">
              <div>
                <h2>거래소 API 키</h2>
                <p className="sub">사용자별 Upbit API 키를 저장/검증합니다.</p>
              </div>
              <span className="pill">
                {exchangeCredentialStatus?.configured
                  ? '등록됨'
                  : exchangeCredentialStatus?.usingDefaultCredentials
                    ? '기본키 사용'
                    : '미등록'}
              </span>
            </div>
            {exchangeCredentialError && <p className="status-error">{exchangeCredentialError}</p>}
            {exchangeCredentialNotice && <p className="status-success">{exchangeCredentialNotice}</p>}
            <div className="form-grid auth-settings-grid">
              <label className="form-field">
                <span>Access Key</span>
                <input
                  type="text"
                  value={exchangeAccessKeyInput}
                  onChange={(event) => setExchangeAccessKeyInput(event.target.value)}
                  placeholder="Upbit Access Key"
                  disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
                />
              </label>
              <label className="form-field">
                <span>Secret Key</span>
                <input
                  type="password"
                  value={exchangeSecretKeyInput}
                  onChange={(event) => setExchangeSecretKeyInput(event.target.value)}
                  placeholder="Upbit Secret Key"
                  disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
                />
              </label>
            </div>
            <div className="button-row">
              <button
                className="primary-button"
                type="button"
                onClick={handleSaveExchangeCredentials}
                disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
              >
                {exchangeCredentialSaving ? '저장 중...' : '키 저장'}
              </button>
              <button
                className="ghost-button"
                type="button"
                onClick={handleVerifyExchangeCredentials}
                disabled={exchangeCredentialLoading || exchangeCredentialSaving || exchangeCredentialVerifying}
              >
                {exchangeCredentialVerifying ? '검증 중...' : '키 검증'}
              </button>
              <button
                className="ghost-button"
                type="button"
                onClick={handleDeleteExchangeCredentials}
                disabled={exchangeCredentialSaving || exchangeCredentialVerifying || !exchangeCredentialStatus?.configured}
              >
                키 삭제
              </button>
            </div>
            {exchangeCredentialStatus?.updatedAt && (
              <p className="sub compact">마지막 저장 {formatDateTime(exchangeCredentialStatus.updatedAt)}</p>
            )}
          </article>

          <article className="control-card card--elevated market-card">
            <div className="card-head">
              <div>
                <h2>마켓별 설정</h2>
                <p className="sub">마켓별 cap/profile 저장 + 행별 토글에서 비율 override 저장을 관리합니다.</p>
              </div>
              <span className={`pill ${marketRowsDirty ? 'pill-warning' : ''}`}>
                {marketRowsDirty ? '변경 있음' : '저장됨'}
              </span>
            </div>
            {strategyError && <p className="status-error">{strategyError}</p>}
            {ratioError && <p className="status-error">{ratioError}</p>}
            {presetError && <p className="status-error">{presetError}</p>}
            {marketConfigError && <p className="status-error">{marketConfigError}</p>}
            {marketConfigNotice && <p className="status-success">{marketConfigNotice}</p>}
            <div className="market-add-row">
              <div className="market-add-input-wrap">
                <input
                  type="text"
                  value={newMarketInput}
                  placeholder="코인명/심볼/마켓코드 검색 (예: 이더리움, ETH, KRW-ETH)"
                  onFocus={() => {
                    if (marketSuggestions.length > 0) {
                      setMarketSuggestOpen(true)
                    }
                  }}
                  onBlur={() => {
                    window.setTimeout(() => setMarketSuggestOpen(false), 120)
                  }}
                  onChange={(event) => {
                    setNewMarketInput(event.target.value)
                    setMarketSuggestOpen(true)
                    setMarketSuggestIndex(0)
                  }}
                  onKeyDown={(event) => {
                    if (event.key === 'ArrowDown' && marketSuggestions.length > 0) {
                      event.preventDefault()
                      setMarketSuggestOpen(true)
                      setMarketSuggestIndex((prev) => (prev + 1) % marketSuggestions.length)
                      return
                    }
                    if (event.key === 'ArrowUp' && marketSuggestions.length > 0) {
                      event.preventDefault()
                      setMarketSuggestOpen(true)
                      setMarketSuggestIndex((prev) => (prev - 1 + marketSuggestions.length) % marketSuggestions.length)
                      return
                    }
                    if (event.key === 'Escape') {
                      setMarketSuggestOpen(false)
                      return
                    }
                    if (event.key === 'Enter') {
                      event.preventDefault()
                      if (marketSuggestOpen && marketSuggestions.length > 0) {
                        const selected = marketSuggestions[Math.max(0, Math.min(marketSuggestIndex, marketSuggestions.length - 1))]
                        if (selected?.market) {
                          handleSelectMarketSuggestion(selected.market)
                          return
                        }
                      }
                      handleAddMarket()
                    }
                  }}
                />
                {marketSuggestOpen && marketSuggestions.length > 0 && (
                  <div className="market-suggest-list">
                    {marketSuggestions.map((item, index) => (
                      <button
                        key={item.market}
                        className={`market-suggest-item ${index === marketSuggestIndex ? 'active' : ''}`}
                        type="button"
                        onMouseDown={(event) => {
                          event.preventDefault()
                          handleSelectMarketSuggestion(item.market)
                        }}
                      >
                        <strong>{item.market}</strong>
                        <span>{item.koreanName || item.englishName || item.ticker}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <button
                className="ghost-button"
                onClick={() => handleAddMarket()}
                disabled={marketConfigLoading || marketConfigSaving}
              >
                마켓 추가
              </button>
            </div>
            {marketConfigLoading ? (
              <div className="empty-state">마켓 설정을 불러오는 중입니다…</div>
            ) : marketRows.length === 0 ? (
              <div className="empty-state">설정 가능한 마켓이 없습니다.</div>
            ) : (
              <div className="market-override-list">
                <div className="market-grid-header">
                  <span>마켓</span>
                  <span>최대 매수 KRW</span>
                  <span>프로필</span>
                  <span>자동매매</span>
                  <span>관리</span>
                </div>
                {marketRows.map((row) => {
                  const expanded = expandedMarket === row.market
                  const effectiveProfileLabel = normalizeProfileValue(row.profile) || DEFAULT_MARKET_PROFILE
                  return (
                    <div className={`market-override-row ${expanded ? 'expanded' : ''}`} key={row.market}>
                      <div className="market-override-main">
                        <div className="market-symbol">
                          <button
                            className={`market-expand-button ${expanded ? 'open' : ''}`}
                            onClick={() => setExpandedMarket((prev) => (prev === row.market ? null : row.market))}
                            aria-label={expanded ? `${row.market} 비율 설정 닫기` : `${row.market} 비율 설정 열기`}
                            type="button"
                          >
                            <span>▾</span>
                          </button>
                          <strong>{row.market}</strong>
                        </div>
                        <label className="market-inline-field">
                          <input
                            type="number"
                            step="1000"
                            min="0"
                            placeholder={DEFAULT_MARKET_MAX_ORDER_KRW}
                            value={row.maxOrderKrw}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'maxOrderKrw', event.target.value)}
                          />
                        </label>
                        <label className="market-inline-field">
                          <select
                            value={normalizeProfileValue(row.profile) || DEFAULT_MARKET_PROFILE}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'profile', event.target.value)}
                          >
                            {PROFILE_VALUES.map((profile) => (
                              <option key={profile} value={profile}>
                                {profile}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label className="market-toggle-field">
                          <input
                            type="checkbox"
                            checked={Boolean(row.tradePaused)}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'tradePaused', event.target.checked)}
                          />
                          <span className={row.tradePaused ? 'is-paused' : ''}>
                            {row.tradePaused ? '일시정지' : '매매중'}
                          </span>
                        </label>
                        <button
                          className="market-remove-button"
                          onClick={() => removeMarketRow(
                            setMarketRows,
                            row.market,
                            setMarketConfigNotice,
                            setMarketConfigError,
                            setSelectedRatioPresetByMarket
                          )}
                          disabled={marketConfigSaving}
                        >
                          제거
                        </button>
                      </div>

                      <div className={`market-ratio-panel ${expanded ? 'open' : ''}`}>
                        <div className="market-ratio-panel-inner">
                          <div className="market-ratio-head">
                            <h3>{row.market} 비율 설정</h3>
                            <span className="pill">PROFILE {effectiveProfileLabel}</span>
                          </div>
                          <p className="sub compact">빈 값은 전역 전략 비율을 사용하고, 입력한 값만 이 마켓 override로 저장됩니다.</p>
                          <div className="preset-row">
                            {ratioPresets.length === 0 ? (
                              <p className="sub compact">등록된 프리셋이 없습니다.</p>
                            ) : ratioPresets.map((preset) => (
                              <button
                                key={`${row.market}-${preset.code}`}
                                className={`ghost-button ${selectedRatioPresetByMarket[row.market] === preset.code ? 'active' : ''}`}
                                onClick={() => applyRatioPresetToMarket(
                                  preset,
                                  row.market,
                                  setMarketRows,
                                  setSelectedRatioPresetByMarket,
                                  setRatioError
                                )}
                                type="button"
                              >
                                {preset.displayName} 비율 적용
                              </button>
                            ))}
                          </div>
                          {selectedRatioPresetByMarket[row.market] && (
                            <p className="sub compact">
                              {resolvePresetDisplayName(ratioPresets, selectedRatioPresetByMarket[row.market])} 프리셋이
                              입력값에 적용되었습니다. 아래 마켓 설정 저장 버튼을 눌러야 서버 반영됩니다.
                            </p>
                          )}
                          <div className="form-grid market-ratio-grid">
                            <label className="form-field">
                              <span>익절 %</span>
                              <input
                                type="number"
                                step="0.1"
                                placeholder={toInputValue(strategy?.takeProfitPct)}
                                value={row.takeProfitPct}
                                onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'takeProfitPct', event.target.value)}
                              />
                            </label>
                            <label className="form-field">
                              <span>손절 %</span>
                              <input
                                type="number"
                                step="0.1"
                                placeholder={toInputValue(strategy?.stopLossPct)}
                                value={row.stopLossPct}
                                onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'stopLossPct', event.target.value)}
                              />
                            </label>
                            <label className="form-field">
                              <span>트레일링 %</span>
                              <input
                                type="number"
                                step="0.1"
                                placeholder={toInputValue(strategy?.trailingStopPct)}
                                value={row.trailingStopPct}
                                onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'trailingStopPct', event.target.value)}
                              />
                            </label>
                            <label className="form-field">
                              <span>부분 익절 %</span>
                              <input
                                type="number"
                                step="1"
                                placeholder={toInputValue(strategy?.partialTakeProfitPct)}
                                value={row.partialTakeProfitPct}
                                onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'partialTakeProfitPct', event.target.value)}
                              />
                            </label>
                            <label className="form-field">
                              <span>손절/트레일링 매도 %</span>
                              <input
                                type="number"
                                step="1"
                                placeholder={toInputValue(strategy?.stopExitPct)}
                                value={row.stopExitPct}
                                onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'stopExitPct', event.target.value)}
                              />
                            </label>
                            <label className="form-field">
                              <span>추세 이탈 매도 %</span>
                              <input
                                type="number"
                                step="1"
                                placeholder={toInputValue(strategy?.trendExitPct)}
                                value={row.trendExitPct}
                                onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'trendExitPct', event.target.value)}
                              />
                            </label>
                            <label className="form-field">
                              <span>모멘텀 역전 매도 %</span>
                              <input
                                type="number"
                                step="1"
                                placeholder={toInputValue(strategy?.momentumExitPct)}
                                value={row.momentumExitPct}
                                onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'momentumExitPct', event.target.value)}
                              />
                            </label>
                          </div>
                          <div className="button-row">
                            <button
                              className="ghost-button"
                              onClick={() => clearMarketRatioOverrides(
                                setMarketRows,
                                row.market,
                                setSelectedRatioPresetByMarket,
                                setRatioError
                              )}
                              type="button"
                            >
                              이 마켓 비율 초기화
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
            <p className="sub compact">빈 값은 글로벌 전략 설정값을 사용합니다.</p>
            <div className="button-row">
              <button
                className="primary-button"
                onClick={() => handleMarketOverridesSave(
                  marketRows,
                  setMarketConfigSaving,
                  setMarketConfigError,
                  setMarketConfigNotice,
                  setMarketRows,
                  setMarketRowsBaseline
                )}
                disabled={marketConfigLoading || marketConfigSaving || !marketRowsDirty}
              >
                {marketConfigSaving ? '저장 중...' : marketRowsDirty ? '마켓 설정 저장' : '변경사항 없음'}
              </button>
              <button
                className="ghost-button"
                onClick={() => handleMarketReload()}
                disabled={marketConfigSaving}
              >
                다시 불러오기
              </button>
            </div>
          </article>

          <article className="control-card card--elevated performance-card">
            <div className="card-head">
              <div>
                <h2>기간 수익 분석</h2>
                <p className="sub">직접 기간/연도/월 기준 추정 실현손익을 조회합니다.</p>
              </div>
              <span className="pill">ESTIMATED</span>
            </div>

            <div className="mode-row">
              <button
                className={`ghost-button ${performanceMode === 'range' ? 'active' : ''}`}
                onClick={() => setPerformanceMode('range')}
              >
                직접 기간
              </button>
              <button
                className={`ghost-button ${performanceMode === 'year' ? 'active' : ''}`}
                onClick={() => setPerformanceMode('year')}
              >
                연도별
              </button>
              <button
                className={`ghost-button ${performanceMode === 'month' ? 'active' : ''}`}
                onClick={() => setPerformanceMode('month')}
              >
                월별
              </button>
            </div>

            {performanceMode === 'range' ? (
              <div className="filter-row">
                <label className="form-field">
                  <span>시작일</span>
                  <input
                    type="date"
                    value={performanceInputs.from}
                    onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, from: event.target.value }))}
                  />
                </label>
                <label className="form-field">
                  <span>종료일</span>
                  <input
                    type="date"
                    value={performanceInputs.to}
                    onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, to: event.target.value }))}
                  />
                </label>
              </div>
            ) : performanceMode === 'year' ? (
              <div className="filter-row filter-row--single">
                <label className="form-field">
                  <span>연도</span>
                  <input
                    type="number"
                    min="2009"
                    max="2100"
                    value={performanceInputs.year}
                    onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, year: event.target.value }))}
                  />
                </label>
              </div>
            ) : (
              <div className="filter-row">
                <label className="form-field">
                  <span>연도</span>
                  <input
                    type="number"
                    min="2009"
                    max="2100"
                    value={performanceInputs.year}
                    onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, year: event.target.value }))}
                  />
                </label>
                <label className="form-field">
                  <span>월</span>
                  <input
                    type="number"
                    min="1"
                    max="12"
                    value={performanceInputs.month}
                    onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, month: event.target.value }))}
                  />
                </label>
              </div>
            )}

            <div className="button-row">
              <button
                className="primary-button"
                onClick={() => fetchPerformance()}
                disabled={performanceLoading}
              >
                {performanceLoading ? '조회 중...' : '수익 조회'}
              </button>
            </div>

            {performanceError && <p className="status-error">{performanceError}</p>}

            {performance && (
              <>
                <p className="sub compact">
                  조회 구간 {performance.from} ~ {performance.to} ({performance.timezone})
                </p>
                <div className="performance-summary-grid">
                  <div className="performance-mini">
                    <span>실현손익</span>
                    <strong className={`mono ${pnlClass(performanceTotal?.estimatedRealizedPnlKrw)}`}>
                      {formatKRW(performanceTotal?.estimatedRealizedPnlKrw)} KRW
                    </strong>
                  </div>
                  <div className="performance-mini">
                    <span>순현금흐름</span>
                    <strong className={`mono ${pnlClass(performanceTotal?.netCashFlowKrw)}`}>
                      {formatKRW(performanceTotal?.netCashFlowKrw)} KRW
                    </strong>
                  </div>
                  <div className="performance-mini">
                    <span>매수/매도</span>
                    <strong className="mono">
                      {formatKRW(performanceTotal?.buyNotionalKrw)} / {formatKRW(performanceTotal?.sellNotionalKrw)}
                    </strong>
                  </div>
                  <div className="performance-mini">
                    <span>매도 승률</span>
                    <strong className="mono">{formatPercent(performanceTotal?.sellWinRate)}</strong>
                  </div>
                </div>

                <div className="performance-table-grid">
                  <div className="table-wrapper">
                    <table>
                      <thead>
                        <tr>
                          <th>연도</th>
                          <th>실현손익</th>
                          <th>순현금흐름</th>
                          <th>승률</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(performance.yearly ?? []).length === 0 ? (
                          <tr>
                            <td colSpan={4} className="empty-cell">연도 데이터 없음</td>
                          </tr>
                        ) : (performance.yearly ?? []).map((row) => (
                          <tr key={`year-${row.period}`}>
                            <td className="mono">{row.period}</td>
                            <td className={`mono ${pnlClass(row.estimatedRealizedPnlKrw)}`}>{formatKRW(row.estimatedRealizedPnlKrw)}</td>
                            <td className={`mono ${pnlClass(row.netCashFlowKrw)}`}>{formatKRW(row.netCashFlowKrw)}</td>
                            <td className="mono">{formatPercent(row.sellWinRate)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <div className="table-wrapper">
                    <table>
                      <thead>
                        <tr>
                          <th>월</th>
                          <th>실현손익</th>
                          <th>순현금흐름</th>
                          <th>승률</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(performance.monthly ?? []).length === 0 ? (
                          <tr>
                            <td colSpan={4} className="empty-cell">월 데이터 없음</td>
                          </tr>
                        ) : (performance.monthly ?? []).map((row) => (
                          <tr key={`month-${row.period}`}>
                            <td className="mono">{row.period}</td>
                            <td className={`mono ${pnlClass(row.estimatedRealizedPnlKrw)}`}>{formatKRW(row.estimatedRealizedPnlKrw)}</td>
                            <td className={`mono ${pnlClass(row.netCashFlowKrw)}`}>{formatKRW(row.netCashFlowKrw)}</td>
                            <td className="mono">{formatPercent(row.sellWinRate)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </>
            )}
          </article>

        </aside>
      </section>

      {manualTradeOpen && (
        <div className="modal-backdrop" onClick={closeManualTrade}>
          <div className="trade-modal" onClick={(event) => event.stopPropagation()}>
            <div className="card-head">
              <div>
                <h2>수동 매매</h2>
                <p className="sub">시장가/지정가 주문을 직접 넣습니다.</p>
              </div>
              <button className="ghost-button" type="button" onClick={closeManualTrade} disabled={manualTradeBusy}>
                닫기
              </button>
            </div>

            <div className="trade-meta-row">
              <span>마켓 {manualTradeMarket}</span>
              <span>보유 {formatCoin(manualTradePosition?.quantity)}</span>
              <span>현금 {formatKRW(cashKrw)} KRW</span>
            </div>

            <div className="form-grid trade-form-grid">
              <label className="form-field">
                <span>구분</span>
                <select value={manualTradeSide} onChange={(event) => setManualTradeSide(event.target.value)}>
                  <option value="BUY">매수</option>
                  <option value="SELL">매도</option>
                </select>
              </label>
              <label className="form-field">
                <span>주문방식</span>
                <select value={manualTradeType} onChange={(event) => setManualTradeType(event.target.value)}>
                  <option value="MARKET">시장가</option>
                  <option value="LIMIT">지정가</option>
                </select>
              </label>
              {manualTradeType === 'LIMIT' && (
                <>
                  <label className="form-field">
                    <span>지정가 (KRW)</span>
                    <input
                      type="number"
                      min="0"
                      step="0.1"
                      value={manualTradePrice}
                      onChange={(event) => setManualTradePrice(event.target.value)}
                      placeholder="예: 101500000"
                    />
                  </label>
                  <label className="form-field">
                    <span>수량</span>
                    <input
                      type="number"
                      min="0"
                      step="0.00000001"
                      value={manualTradeVolume}
                      onChange={(event) => setManualTradeVolume(event.target.value)}
                      placeholder="예: 0.001"
                    />
                  </label>
                </>
              )}
              {manualTradeType === 'MARKET' && manualTradeSide === 'BUY' && (
                <label className="form-field">
                  <span>매수 금액 (KRW)</span>
                  <input
                    type="number"
                    min="0"
                    step="1000"
                    value={manualTradeFunds}
                    onChange={(event) => setManualTradeFunds(event.target.value)}
                    placeholder="예: 30000"
                  />
                </label>
              )}
              {manualTradeType === 'MARKET' && manualTradeSide === 'SELL' && (
                <label className="form-field">
                  <span>매도 수량</span>
                  <div className="trade-volume-row">
                    <input
                      type="number"
                      min="0"
                      step="0.00000001"
                      value={manualTradeVolume}
                      onChange={(event) => setManualTradeVolume(event.target.value)}
                      placeholder="예: 0.001"
                    />
                    <button
                      className="ghost-button"
                      type="button"
                      onClick={() => setManualTradeVolume(toInputValue(manualTradePosition?.quantity))}
                    >
                      전량
                    </button>
                  </div>
                </label>
              )}
            </div>

            {manualTradeError && <p className="status-error">{manualTradeError}</p>}

            <div className="button-row">
              <button className="primary-button" type="button" onClick={handleManualTradeSubmit} disabled={manualTradeBusy}>
                {manualTradeBusy ? '주문 중...' : '주문 실행'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const appendOrderAlerts = (orders, lastOrderIdRef, setAlerts, options = {}) => {
  const seedInitial = Boolean(options?.seedInitial)
  if (!Array.isArray(orders) || orders.length === 0) {
    return
  }

  const newestId = toNumber(orders[0]?.id)
  if (newestId === null) {
    return
  }

  if (lastOrderIdRef.current === null) {
    if (seedInitial) {
      const seedOrders = orders
        .filter((order) => order?.side === 'BUY' || order?.side === 'SELL')
        .slice(0, ALERT_SEED_LIMIT)
      seedOrders.forEach((order) => {
        const tone = order.side === 'BUY' ? 'positive' : 'negative'
        const message = `주문 ${order.side} ${order.market}`
        const meta = `${order.requestStatus ?? '-'} / ${order.state ?? '-'} / ${formatDateTime(order.requestedAt)}`
        pushAlert(setAlerts, message, meta, tone, order.requestedAt)
      })
    }
    lastOrderIdRef.current = newestId
    return
  }

  const baseline = lastOrderIdRef.current
  const newOrders = orders
    .filter((order) => {
      const id = toNumber(order?.id)
      return id !== null && id > baseline && (order?.side === 'BUY' || order?.side === 'SELL')
    })
    .sort((a, b) => a.id - b.id)

  if (newOrders.length === 0) {
    if (newestId > baseline) {
      lastOrderIdRef.current = newestId
    }
    return
  }

  newOrders.forEach((order) => {
    const tone = order.side === 'BUY' ? 'positive' : 'negative'
    const message = `주문 ${order.side} ${order.market}`
    const meta = `${order.requestStatus ?? '-'} / ${order.state ?? '-'} / ${formatDateTime(order.requestedAt)}`
    pushAlert(setAlerts, message, meta, tone, order.requestedAt)
  })
  lastOrderIdRef.current = newestId
}

const appendDecisionAlerts = (decisions, lastDecisionIdRef, setAlerts, options = {}) => {
  const seedInitial = Boolean(options?.seedInitial)
  if (!Array.isArray(decisions) || decisions.length === 0) {
    return
  }

  const newestId = toNumber(decisions[0]?.id)
  if (newestId === null) {
    return
  }

  if (lastDecisionIdRef.current === null) {
    if (seedInitial) {
      const seedDecisions = decisions
        .filter((decision) => decision?.action === 'BUY' || decision?.action === 'SELL')
        .slice(0, ALERT_SEED_LIMIT)
      seedDecisions.forEach((decision) => {
        const tone = decision.action === 'BUY' ? 'positive' : 'negative'
        const message = `신호 ${decision.action} ${decision.market}`
        const meta = `${decision.reason ?? '-'} / ${formatDateTime(decision.executedAt)}`
        pushAlert(setAlerts, message, meta, tone, decision.executedAt)
      })
    }
    lastDecisionIdRef.current = newestId
    return
  }

  const baseline = lastDecisionIdRef.current
  const newDecisions = decisions
    .filter((decision) => {
      const id = toNumber(decision?.id)
      return id !== null && id > baseline && (decision?.action === 'BUY' || decision?.action === 'SELL')
    })
    .sort((a, b) => a.id - b.id)

  if (newDecisions.length === 0) {
    if (newestId > baseline) {
      lastDecisionIdRef.current = newestId
    }
    return
  }

  newDecisions.forEach((decision) => {
    const tone = decision.action === 'BUY' ? 'positive' : 'negative'
    const message = `신호 ${decision.action} ${decision.market}`
    const meta = `${decision.reason ?? '-'} / ${formatDateTime(decision.executedAt)}`
    pushAlert(setAlerts, message, meta, tone, decision.executedAt)
  })
  lastDecisionIdRef.current = newestId
}

const pushAlert = (setAlerts, message, meta, tone, occurredAt = null) => {
  setAlerts((prev) => {
    if (prev.some((item) => item.message === message && item.meta === meta)) {
      return prev
    }
    const next = [{
      id: `${Date.now()}-${Math.random()}`,
      message,
      meta,
      tone,
      time: occurredAt ?? new Date().toISOString(),
    }, ...prev]
    return next.slice(0, ALERT_MAX_SIZE)
  })
}

const handleEngineStart = async (setEngineStatus, setEngineError, setEngineBusy) => {
  if (!window.confirm('자동매매 엔진을 시작할까요? 실제 주문이 발생할 수 있습니다.')) {
    return
  }
  setEngineBusy(true)
  setEngineError(null)
  try {
    const response = await fetch('/api/engine/start', { method: 'POST' })
    if (!response.ok) {
      const payload = await response.json().catch(() => null)
      const message = buildApiErrorMessage(payload, `엔진 시작 실패 ${response.status}`)
      throw new Error(message)
    }
    const data = await response.json()
    setEngineStatus(Boolean(data?.running))
  } catch (err) {
    setEngineError(err?.message ?? '엔진 시작 실패')
  } finally {
    setEngineBusy(false)
  }
}

const handleEngineStop = async (setEngineStatus, setEngineError, setEngineBusy) => {
  setEngineBusy(true)
  setEngineError(null)
  try {
    const response = await fetch('/api/engine/stop', { method: 'POST' })
    if (!response.ok) {
      const payload = await response.json().catch(() => null)
      const message = buildApiErrorMessage(payload, `엔진 중지 실패 ${response.status}`)
      throw new Error(message)
    }
    const data = await response.json()
    setEngineStatus(Boolean(data?.running))
  } catch (err) {
    setEngineError(err?.message ?? '엔진 중지 실패')
  } finally {
    setEngineBusy(false)
  }
}

const handleMarketOverridesSave = async (
  rows,
  setMarketConfigSaving,
  setMarketConfigError,
  setMarketConfigNotice,
  setMarketRows,
  setMarketRowsBaseline
) => {
  setMarketConfigSaving(true)
  setMarketConfigError(null)
  setMarketConfigNotice(null)
  try {
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
    const nextRows = buildMarketOverrideRows(data)
    setMarketRows(nextRows)
    setMarketRowsBaseline(buildMarketOverrideSignature(nextRows))
    setMarketConfigNotice('마켓/설정이 저장되었습니다.')
  } catch (err) {
    setMarketConfigError(err?.message ?? '마켓 설정 저장 실패')
  } finally {
    setMarketConfigSaving(false)
  }
}

const applyRatioPresetToMarket = (
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

const clearMarketRatioOverrides = (
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

const resolvePresetDisplayName = (presets, code) => {
  if (!code) {
    return ''
  }
  const found = Array.isArray(presets) ? presets.find((preset) => preset.code === code) : null
  return found?.displayName ?? code
}

const updateMarketOverrideInput = (setMarketRows, market, field, value) => {
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

const addMarketRow = (
  input,
  rows,
  setNewMarketInput,
  setMarketRows,
  setMarketConfigError,
  setMarketConfigNotice
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

  setMarketRows((prev) => [...prev, {
    market,
    maxOrderKrw: DEFAULT_MARKET_MAX_ORDER_KRW,
    profile: DEFAULT_MARKET_PROFILE,
    tradePaused: false,
    ...createEmptyRatioFields(),
  }])
  setNewMarketInput('')
  setMarketConfigError(null)
  setMarketConfigNotice(null)
}

const removeMarketRow = (
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

const buildManualOrderPayload = ({ market, side, type, price, volume, funds }) => {
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

const normalizeRatioPresets = (payload) => {
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

const normalizeMarketCatalog = (payload) => {
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

const buildMarketSuggestions = (input, catalog, rows, limit = 8) => {
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

const buildMarketOverrideRows = (payload) => {
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

const buildMarketOverridePayload = (rows) => {
  const payload = {
    maxOrderKrwByMarket: {},
    profileByMarket: {},
    tradePausedByMarket: {},
    ratiosByMarket: {},
  }
  if (!Array.isArray(rows)) {
    return payload
  }

  rows.forEach((row) => {
    const market = normalizeMarket(row?.market)
    if (!market) {
      return
    }
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

const buildMarketListPayload = (rows) => {
  if (!Array.isArray(rows)) {
    throw new Error('마켓 목록이 비어 있습니다.')
  }

  const markets = []
  const seen = new Set()
  rows.forEach((row) => {
    const market = normalizeMarket(row?.market)
    if (!market) {
      return
    }
    if (!isValidMarketCode(market)) {
      throw new Error(`${market} 마켓 코드 형식이 올바르지 않습니다. 예: KRW-BTC`)
    }
    if (seen.has(market)) {
      return
    }
    seen.add(market)
    markets.push(market)
  })

  if (markets.length === 0) {
    throw new Error('최소 1개 이상의 마켓이 필요합니다.')
  }

  return { markets }
}

const parseUserMarketsInput = (value) => {
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

const buildMarketOverrideSignature = (rows) => {
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
    .sort((a, b) => a.market.localeCompare(b.market))
  return JSON.stringify(normalized)
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

const buildApiErrorMessage = (payload, fallback) => {
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

const formatOrderStatus = (requestStatus, state) => {
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

const buildDefaultPerformanceInputs = () => {
  const now = new Date()
  const to = formatDateInput(now)
  const from = formatDateInput(addDays(now, -29))
  return {
    from,
    to,
    year: String(now.getFullYear()),
    month: String(now.getMonth() + 1),
  }
}

const buildPerformanceQuery = (mode, inputs) => {
  const params = new URLSearchParams()
  if (mode === 'year') {
    const year = normalizeIntegerInput(inputs?.year)
    if (!year) {
      throw new Error('연도 값을 입력해주세요.')
    }
    params.set('year', String(year))
    return params.toString()
  }
  if (mode === 'month') {
    const year = normalizeIntegerInput(inputs?.year)
    const month = normalizeIntegerInput(inputs?.month)
    if (!year || !month) {
      throw new Error('연도/월 값을 입력해주세요.')
    }
    params.set('year', String(year))
    params.set('month', String(month))
    return params.toString()
  }

  const from = normalizeDateInput(inputs?.from)
  const to = normalizeDateInput(inputs?.to)
  if (!from || !to) {
    throw new Error('시작일/종료일을 입력해주세요.')
  }
  if (from > to) {
    throw new Error('시작일은 종료일보다 늦을 수 없습니다.')
  }
  params.set('from', from)
  params.set('to', to)
  return params.toString()
}

const formatDateInput = (date) => {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
    return ''
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const addDays = (date, days) => {
  const next = new Date(date.getTime())
  next.setDate(next.getDate() + days)
  return next
}

const normalizeIntegerInput = (value) => {
  const num = Number(value)
  if (!Number.isInteger(num)) {
    return null
  }
  return num
}

const normalizeDateInput = (value) => {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  return /^\d{4}-\d{2}-\d{2}$/.test(trimmed) ? trimmed : null
}

const isPlainObject = (value) => {
  if (value === null || typeof value !== 'object') {
    return false
  }
  return !Array.isArray(value)
}

const normalizeProfileValue = (value) => {
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

const normalizeMarket = (value) => {
  if (value === null || value === undefined) {
    return null
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === '') {
    return null
  }
  return normalized
}

const isValidMarketCode = (value) => MARKET_CODE_PATTERN.test(value)

const toInputValue = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
}

const toNumber = (value) => {
  const numeric = Number(value)
  return Number.isNaN(numeric) ? null : numeric
}

const formatKRW = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 0 })
}

const formatCoin = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toLocaleString('en-US', { maximumFractionDigits: 8 })
}

const formatPercent = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return `${(Number(value) * 100).toFixed(2)}%`
}

const formatFixed = (value, digits) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toFixed(digits)
}

const formatDateTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleString('ko-KR', { hour12: false })
}

const _formatTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleTimeString('ko-KR', { hour12: false })
}

const truncateText = (value, max) => {
  if (!value) {
    return '-'
  }
  if (value.length <= max) {
    return value
  }
  return `${value.slice(0, max)}...`
}

const pnlClass = (value) => {
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

export default App
