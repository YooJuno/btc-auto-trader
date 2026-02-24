import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'
import DashboardRoute from './routes/DashboardRoute.jsx'
import SettingsRoute from './routes/SettingsRoute.jsx'
import AuthGate from './components/auth/AuthGate.jsx'
import AppHeader from './components/layout/AppHeader.jsx'
import PageContextBanner from './components/layout/PageContextBanner.jsx'
import ManualTradeModal from './components/trade/ManualTradeModal.jsx'
import { useDeviceUiPreferences } from './hooks/useDeviceUiPreferences.js'
import {
  DASHBOARD_ROUTE,
  DEFAULT_MARKET_PROFILE,
} from './constants/tradingUi.js'
import {
  addMarketRow,
  buildApiErrorMessage,
  buildDefaultPerformanceInputs,
  buildManualOrderPayload,
  buildMarketOverrideRows,
  buildMarketOverrideSignature,
  buildMarketSuggestions,
  buildPerformanceQuery,
  buildUiPrefsPayload,
  formatCoin,
  formatDateTime,
  formatFixed,
  formatKRW,
  formatOrderStatus,
  formatPercent,
  isValidMarketCode,
  normalizeMarket,
  normalizeProfileValue,
  parseUserMarketsInput,
  pnlClass,
  resolveAppPath,
  resolveAppRoute,
  toInputValue,
  truncateText,
  normalizeRatioPresets,
  normalizeMarketCatalog,
} from './utils/tradingUi.js'
import {
  saveMarketOverridesRequest,
  startEngineRequest,
  stopEngineRequest,
} from './utils/tradingActions.js'

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
  const [activeRoute, setActiveRoute] = useState(() => resolveAppRoute(window.location.pathname))

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
      setUserUiPrefs(buildUiPrefsPayload(data?.uiPrefs))
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
        uiPrefs: buildUiPrefsPayload(userUiPrefs),
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
      setUserUiPrefs(buildUiPrefsPayload(data?.uiPrefs))
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

  const navigateRoute = useCallback((route) => {
    const nextPath = resolveAppPath(route)
    if (window.location.pathname !== nextPath) {
      window.history.pushState({}, '', nextPath)
    }
    setActiveRoute(resolveAppRoute(nextPath))
  }, [])

  const {
    commonUiPrefs,
    mobileUiPrefs,
    desktopUiPrefs,
    deviceLabel,
    effectiveRouteLabel,
    effectiveDensityLabel,
    pollingIntervalMs,
    tableDensityClass,
    handleRefreshSecChange,
    handleDefaultRouteChange,
    handleTableDensityChange,
  } = useDeviceUiPreferences({
    userUiPrefs,
    setUserUiPrefs,
    authUser,
    userSettings,
    navigateRoute,
  })

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
    const handlePopstate = () => {
      setActiveRoute(resolveAppRoute(window.location.pathname))
    }

    window.addEventListener('popstate', handlePopstate)
    return () => {
      window.removeEventListener('popstate', handlePopstate)
    }
  }, [])

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

    const summaryTimer = setInterval(() => fetchSummary(true), pollingIntervalMs)
    const engineTimer = setInterval(() => fetchEngineStatus(), pollingIntervalMs)
    const feedTimer = setInterval(() => {
      fetchOrderHistory()
      fetchDecisionHistory()
    }, pollingIntervalMs)

    return () => {
      clearInterval(summaryTimer)
      clearInterval(engineTimer)
      clearInterval(feedTimer)
    }
  }, [authUser, fetchSummary, fetchEngineStatus, fetchStrategy, fetchRatioPresets, fetchMarketOverrides, fetchMarketCatalog, fetchOrderHistory, fetchDecisionHistory, fetchPerformance, pollingIntervalMs])

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

  const handleSaveMarketOverrides = useCallback(async () => {
    setMarketConfigSaving(true)
    setMarketConfigError(null)
    setMarketConfigNotice(null)
    try {
      const nextRows = await saveMarketOverridesRequest(marketRows)
      setMarketRows(nextRows)
      setMarketRowsBaseline(buildMarketOverrideSignature(nextRows))
      setMarketConfigNotice('마켓/설정이 저장되었습니다.')
    } catch (err) {
      setMarketConfigError(err?.message ?? '마켓 설정 저장 실패')
    } finally {
      setMarketConfigSaving(false)
    }
  }, [marketRows])

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

  const userPreferencesProps = {
    settingsLoading,
    settingsSaving,
    userSettings,
    userSettingsError,
    userSettingsNotice,
    userRiskProfile,
    userMarketsInput,
    setUserRiskProfile,
    setUserMarketsInput,
    handleSaveMySettings,
    fetchMySettings,
    commonUiPrefs,
    mobileUiPrefs,
    desktopUiPrefs,
    handleRefreshSecChange,
    handleDefaultRouteChange,
    handleTableDensityChange,
    deviceLabel,
    effectiveRouteLabel,
    effectiveDensityLabel,
    pollingIntervalMs,
  }

  const exchangeCredentialsProps = {
    exchangeCredentialStatus,
    exchangeCredentialLoading,
    exchangeCredentialSaving,
    exchangeCredentialVerifying,
    exchangeCredentialError,
    exchangeCredentialNotice,
    exchangeAccessKeyInput,
    exchangeSecretKeyInput,
    setExchangeAccessKeyInput,
    setExchangeSecretKeyInput,
    handleSaveExchangeCredentials,
    handleVerifyExchangeCredentials,
    handleDeleteExchangeCredentials,
  }

  const marketOverridesProps = {
    strategyError,
    ratioError,
    presetError,
    ratioPresets,
    selectedRatioPresetByMarket,
    setSelectedRatioPresetByMarket,
    marketRows,
    marketConfigSaving,
    marketConfigLoading,
    marketConfigError,
    marketConfigNotice,
    marketRowsDirty,
    newMarketInput,
    setNewMarketInput,
    marketSuggestions,
    marketSuggestOpen,
    setMarketSuggestOpen,
    marketSuggestIndex,
    setMarketSuggestIndex,
    expandedMarket,
    setExpandedMarket,
    strategy,
    setRatioError,
    setMarketRows,
    setMarketConfigError,
    setMarketConfigNotice,
    handleSelectMarketSuggestion,
    handleAddMarket,
    handleMarketReload,
    onSaveMarketOverrides: handleSaveMarketOverrides,
  }

  const performanceProps = {
    fetchPerformance,
    performanceMode,
    setPerformanceMode,
    performanceInputs,
    setPerformanceInputs,
    performanceLoading,
    performanceError,
    performance,
    performanceTotal,
  }

  if (authChecking) {
    return <AuthGate checking authError={null} authProviders={[]} onProviderLogin={handleProviderLogin} />
  }

  if (!authUser) {
    return (
      <AuthGate
        checking={false}
        authError={authError}
        authProviders={authProviders}
        onProviderLogin={handleProviderLogin}
      />
    )
  }

  return (
    <div className={`app ${tableDensityClass}`}>
      <AppHeader
        activeRoute={activeRoute}
        onNavigateRoute={navigateRoute}
        engineClass={engineClass}
        engineLabel={engineLabel}
        engineError={engineError}
        engineBusy={engineBusy}
        engineStatus={engineStatus}
        onEngineStart={handleEngineStart}
        onEngineStop={handleEngineStop}
        updatedAt={updatedAt}
        authUser={authUser}
        connectionClass={connectionClass}
        connectionLabel={connectionLabel}
        onLogout={handleLogout}
      />

      <PageContextBanner activeRoute={activeRoute} />

      {activeRoute === DASHBOARD_ROUTE ? (
        <DashboardRoute
          cash={cash}
          totals={totals}
          loading={loading}
          positions={positions}
          manualTradeNotice={manualTradeNotice}
          mergedOrderHistory={mergedOrderHistory}
          feedError={feedError}
          onOpenManualTrade={openManualTrade}
          formatters={{
            formatKRW,
            formatCoin,
            formatPercent,
            formatDateTime,
            formatOrderStatus,
            formatFixed,
            truncateText,
            pnlClass,
          }}
        />
      ) : (
        <SettingsRoute
          userPreferences={userPreferencesProps}
          exchangeCredentials={exchangeCredentialsProps}
          marketOverrides={marketOverridesProps}
          performance={performanceProps}
        />
      )}

      <ManualTradeModal
        open={manualTradeOpen}
        busy={manualTradeBusy}
        market={manualTradeMarket}
        side={manualTradeSide}
        type={manualTradeType}
        price={manualTradePrice}
        volume={manualTradeVolume}
        funds={manualTradeFunds}
        position={manualTradePosition}
        cashKrw={cashKrw}
        error={manualTradeError}
        onClose={closeManualTrade}
        onSubmit={handleManualTradeSubmit}
        setSide={setManualTradeSide}
        setType={setManualTradeType}
        setPrice={setManualTradePrice}
        setVolume={setManualTradeVolume}
        setFunds={setManualTradeFunds}
        formatters={{ formatCoin, formatKRW, toInputValue }}
      />
    </div>
  )
}

export default App
