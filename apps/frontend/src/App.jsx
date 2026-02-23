import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'
import DashboardRoute from './routes/DashboardRoute.jsx'
import SettingsRoute from './routes/SettingsRoute.jsx'

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
const DASHBOARD_ROUTE = 'dashboard'
const SETTINGS_ROUTE = 'settings'
const SETTINGS_PATH = '/settings'
const UI_SCOPE_COMMON = 'common'
const UI_SCOPE_MOBILE = 'mobile'
const UI_SCOPE_DESKTOP = 'desktop'
const UI_DENSITY_COMFORTABLE = 'comfortable'
const UI_DENSITY_COMPACT = 'compact'
const UI_REFRESH_MIN_SEC = 2
const UI_REFRESH_MAX_SEC = 30

const resolveAppRoute = (pathname) => {
  const normalizedPath = normalizePathname(pathname)
  if (normalizedPath === SETTINGS_PATH) {
    return SETTINGS_ROUTE
  }
  return DASHBOARD_ROUTE
}

const resolveAppPath = (route) => {
  if (route === SETTINGS_ROUTE) {
    return SETTINGS_PATH
  }
  return '/'
}

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
  const [deviceKind, setDeviceKind] = useState(() => detectDeviceKind())
  const [defaultRouteApplied, setDefaultRouteApplied] = useState(false)

  const normalizedUserUiPrefs = useMemo(() => buildUiPrefsPayload(userUiPrefs), [userUiPrefs])
  const effectiveUiPrefs = useMemo(
    () => resolveEffectiveUiPrefs(normalizedUserUiPrefs, deviceKind),
    [normalizedUserUiPrefs, deviceKind]
  )
  const pollingIntervalMs = useMemo(
    () => normalizePollingIntervalMs(effectiveUiPrefs.refreshSec),
    [effectiveUiPrefs.refreshSec]
  )
  const tableDensityClass = effectiveUiPrefs.tableDensity === UI_DENSITY_COMPACT
    ? 'app--density-compact'
    : 'app--density-comfortable'

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

  const setUiPrefValue = useCallback((scope, key, value) => {
    setUserUiPrefs((prev) => updateUiPrefsSectionValue(prev, scope, key, value))
  }, [])

  const handleRefreshSecChange = useCallback((event) => {
    const nextValue = normalizeRefreshSeconds(event.target.value)
    setUiPrefValue(UI_SCOPE_COMMON, 'refreshSec', nextValue)
  }, [setUiPrefValue])

  const handleDefaultRouteChange = useCallback((scope, value) => {
    const nextValue = normalizeRouteToken(value, DASHBOARD_ROUTE)
    setUiPrefValue(scope, 'defaultRoute', nextValue)
  }, [setUiPrefValue])

  const handleTableDensityChange = useCallback((scope, value) => {
    const nextValue = normalizeTableDensity(value, UI_DENSITY_COMFORTABLE)
    setUiPrefValue(scope, 'tableDensity', nextValue)
  }, [setUiPrefValue])

  const navigateRoute = useCallback((route) => {
    const nextPath = resolveAppPath(route)
    if (window.location.pathname !== nextPath) {
      window.history.pushState({}, '', nextPath)
    }
    setActiveRoute(resolveAppRoute(nextPath))
  }, [])

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
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined
    }
    const mediaQuery = window.matchMedia('(max-width: 860px)')
    const handleMediaChange = (event) => {
      setDeviceKind(event.matches ? UI_SCOPE_MOBILE : UI_SCOPE_DESKTOP)
    }
    setDeviceKind(mediaQuery.matches ? UI_SCOPE_MOBILE : UI_SCOPE_DESKTOP)
    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', handleMediaChange)
      return () => mediaQuery.removeEventListener('change', handleMediaChange)
    }
    mediaQuery.addListener(handleMediaChange)
    return () => mediaQuery.removeListener(handleMediaChange)
  }, [])

  useEffect(() => {
    if (!authUser) {
      setUserSettings(null)
      setExchangeCredentialStatus(null)
      setDefaultRouteApplied(false)
      return
    }
    setDefaultRouteApplied(false)
    fetchMySettings()
    fetchExchangeCredentialStatus()
  }, [authUser, fetchMySettings, fetchExchangeCredentialStatus])

  useEffect(() => {
    if (!authUser || !userSettings || defaultRouteApplied) {
      return
    }
    const currentPath = normalizePathname(window.location.pathname)
    if (currentPath === '/') {
      navigateRoute(effectiveUiPrefs.defaultRoute)
    }
    setDefaultRouteApplied(true)
  }, [authUser, defaultRouteApplied, effectiveUiPrefs.defaultRoute, navigateRoute, userSettings])

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
  const commonUiPrefs = normalizedUserUiPrefs?.[UI_SCOPE_COMMON] ?? {}
  const mobileUiPrefs = normalizedUserUiPrefs?.[UI_SCOPE_MOBILE] ?? {}
  const desktopUiPrefs = normalizedUserUiPrefs?.[UI_SCOPE_DESKTOP] ?? {}
  const deviceLabel = deviceKind === UI_SCOPE_MOBILE ? '스마트폰' : 'PC'
  const effectiveRouteLabel = effectiveUiPrefs.defaultRoute === SETTINGS_ROUTE ? '매매 세팅' : '실시간 현황'
  const effectiveDensityLabel = effectiveUiPrefs.tableDensity === UI_DENSITY_COMPACT ? '컴팩트' : '컴포터블'

  const handleMarketReload = useCallback(() => {
    if (marketRowsDirty && !window.confirm('저장하지 않은 변경사항이 있습니다. 서버 설정으로 덮어쓸까요?')) {
      return
    }
    fetchMarketOverrides()
  }, [fetchMarketOverrides, marketRowsDirty])

  const handleSaveMarketOverrides = useCallback(() => {
    handleMarketOverridesSave(
      marketRows,
      setMarketConfigSaving,
      setMarketConfigError,
      setMarketConfigNotice,
      setMarketRows,
      setMarketRowsBaseline
    )
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
    <div className={`app ${tableDensityClass}`}>
      <header className="app__header">
        <div className="brand-block">
          <p className="eyebrow">BTC AUTO TRADER</p>
          <h1>Trading Control Center</h1>
          <p className="sub">실시간 상태는 메인에서 확인하고, 전략/거래 설정은 설정 페이지에서 관리합니다.</p>
          <div className="route-tabs" role="tablist" aria-label="페이지 이동">
            <button
              type="button"
              className={`route-tab ${activeRoute === DASHBOARD_ROUTE ? 'is-active' : ''}`}
              onClick={() => navigateRoute(DASHBOARD_ROUTE)}
            >
              실시간 현황
            </button>
            <button
              type="button"
              className={`route-tab ${activeRoute === SETTINGS_ROUTE ? 'is-active' : ''}`}
              onClick={() => navigateRoute(SETTINGS_ROUTE)}
            >
              매매 세팅
            </button>
          </div>
        </div>
        <div className="engine-inline-card">
          <div className="engine-inline-head">
            <strong>자동매매</strong>
            <span className={`status ${engineClass}`}>ENGINE {engineLabel}</span>
          </div>
          {engineError && <p className="status-error">{engineError}</p>}
          <div className="engine-inline-actions">
            <button
              className={`engine-action-btn engine-action-btn--start ${engineStatus ? 'is-active' : ''}`}
              onClick={() => handleEngineStart(setEngineStatus, setEngineError, setEngineBusy)}
              disabled={engineBusy || engineStatus}
            >
              <span className="engine-action-btn__icon" aria-hidden="true">▶</span>
              <span className="engine-action-btn__label">
                {engineBusy && !engineStatus ? '시작 중...' : '엔진 시작'}
              </span>
            </button>
            <button
              className={`engine-action-btn engine-action-btn--stop ${engineStatus ? '' : 'is-active'}`}
              onClick={() => handleEngineStop(setEngineStatus, setEngineError, setEngineBusy)}
              disabled={engineBusy || !engineStatus}
            >
              <span className="engine-action-btn__icon" aria-hidden="true">■</span>
              <span className="engine-action-btn__label">
                {engineBusy && engineStatus ? '중지 중...' : '엔진 중지'}
              </span>
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

      <section className="page-context">
        <div>
          <h2>{activeRoute === DASHBOARD_ROUTE ? '실시간 매매 현황' : '매매 세팅 센터'}</h2>
          <p className="sub">
            {activeRoute === DASHBOARD_ROUTE
              ? '현재 자산, 포지션, 주문 로그를 한 화면에서 빠르게 확인합니다.'
              : '사용자 설정, 거래소 키, 마켓별 전략 파라미터를 안전하게 관리합니다.'}
          </p>
        </div>
        <span className="pill">{activeRoute === DASHBOARD_ROUTE ? 'LIVE' : 'SETTINGS'}</span>
      </section>

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
          profileValues={PROFILE_VALUES}
          settingsLoading={settingsLoading}
          settingsSaving={settingsSaving}
          userSettings={userSettings}
          userSettingsError={userSettingsError}
          userSettingsNotice={userSettingsNotice}
          userRiskProfile={userRiskProfile}
          userMarketsInput={userMarketsInput}
          setUserRiskProfile={setUserRiskProfile}
          setUserMarketsInput={setUserMarketsInput}
          handleSaveMySettings={handleSaveMySettings}
          fetchMySettings={fetchMySettings}
          commonUiPrefs={commonUiPrefs}
          mobileUiPrefs={mobileUiPrefs}
          desktopUiPrefs={desktopUiPrefs}
          handleRefreshSecChange={handleRefreshSecChange}
          handleDefaultRouteChange={handleDefaultRouteChange}
          handleTableDensityChange={handleTableDensityChange}
          deviceLabel={deviceLabel}
          effectiveRouteLabel={effectiveRouteLabel}
          effectiveDensityLabel={effectiveDensityLabel}
          pollingIntervalMs={pollingIntervalMs}
          exchangeCredentialStatus={exchangeCredentialStatus}
          exchangeCredentialLoading={exchangeCredentialLoading}
          exchangeCredentialSaving={exchangeCredentialSaving}
          exchangeCredentialVerifying={exchangeCredentialVerifying}
          exchangeCredentialError={exchangeCredentialError}
          exchangeCredentialNotice={exchangeCredentialNotice}
          exchangeAccessKeyInput={exchangeAccessKeyInput}
          exchangeSecretKeyInput={exchangeSecretKeyInput}
          setExchangeAccessKeyInput={setExchangeAccessKeyInput}
          setExchangeSecretKeyInput={setExchangeSecretKeyInput}
          handleSaveExchangeCredentials={handleSaveExchangeCredentials}
          handleVerifyExchangeCredentials={handleVerifyExchangeCredentials}
          handleDeleteExchangeCredentials={handleDeleteExchangeCredentials}
          strategyError={strategyError}
          ratioError={ratioError}
          presetError={presetError}
          ratioPresets={ratioPresets}
          selectedRatioPresetByMarket={selectedRatioPresetByMarket}
          setSelectedRatioPresetByMarket={setSelectedRatioPresetByMarket}
          marketRows={marketRows}
          marketConfigSaving={marketConfigSaving}
          marketConfigLoading={marketConfigLoading}
          marketConfigError={marketConfigError}
          marketConfigNotice={marketConfigNotice}
          marketRowsDirty={marketRowsDirty}
          newMarketInput={newMarketInput}
          setNewMarketInput={setNewMarketInput}
          marketSuggestions={marketSuggestions}
          marketSuggestOpen={marketSuggestOpen}
          setMarketSuggestOpen={setMarketSuggestOpen}
          marketSuggestIndex={marketSuggestIndex}
          setMarketSuggestIndex={setMarketSuggestIndex}
          expandedMarket={expandedMarket}
          setExpandedMarket={setExpandedMarket}
          strategy={strategy}
          setRatioError={setRatioError}
          setMarketRows={setMarketRows}
          setMarketConfigError={setMarketConfigError}
          setMarketConfigNotice={setMarketConfigNotice}
          handleSelectMarketSuggestion={handleSelectMarketSuggestion}
          handleAddMarket={handleAddMarket}
          handleMarketReload={handleMarketReload}
          onSaveMarketOverrides={handleSaveMarketOverrides}
          fetchPerformance={fetchPerformance}
          performanceMode={performanceMode}
          setPerformanceMode={setPerformanceMode}
          performanceInputs={performanceInputs}
          setPerformanceInputs={setPerformanceInputs}
          performanceLoading={performanceLoading}
          performanceError={performanceError}
          performance={performance}
          performanceTotal={performanceTotal}
          helpers={{
            formatDateTime,
            formatKRW,
            formatPercent,
            toInputValue,
            pnlClass,
            normalizeRouteToken,
            normalizeTableDensity,
            normalizeProfileValue,
            resolvePresetDisplayName,
            updateMarketOverrideInput,
            removeMarketRow,
            applyRatioPresetToMarket,
            clearMarketRatioOverrides,
          }}
          constants={{
            defaultMarketMaxOrderKrw: DEFAULT_MARKET_MAX_ORDER_KRW,
            defaultMarketProfile: DEFAULT_MARKET_PROFILE,
            dashboardRoute: DASHBOARD_ROUTE,
            settingsRoute: SETTINGS_ROUTE,
            uiScopeDesktop: UI_SCOPE_DESKTOP,
            uiScopeMobile: UI_SCOPE_MOBILE,
            uiDensityComfortable: UI_DENSITY_COMFORTABLE,
            uiDensityCompact: UI_DENSITY_COMPACT,
            uiRefreshMinSec: UI_REFRESH_MIN_SEC,
            uiRefreshMaxSec: UI_REFRESH_MAX_SEC,
          }}
        />
      )}

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

const detectDeviceKind = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return UI_SCOPE_DESKTOP
  }
  return window.matchMedia('(max-width: 860px)').matches ? UI_SCOPE_MOBILE : UI_SCOPE_DESKTOP
}

const normalizePathname = (pathname) => {
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

const normalizeRouteToken = (value, fallback = DASHBOARD_ROUTE) => {
  if (value === DASHBOARD_ROUTE || value === SETTINGS_ROUTE) {
    return value
  }
  if (value === null || value === undefined) {
    return fallback
  }
  const normalized = String(value).trim().toLowerCase()
  if (normalized === SETTINGS_ROUTE) {
    return SETTINGS_ROUTE
  }
  if (normalized === DASHBOARD_ROUTE) {
    return DASHBOARD_ROUTE
  }
  return fallback
}

const normalizeTableDensity = (value, fallback = UI_DENSITY_COMFORTABLE) => {
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

const normalizeRefreshSeconds = (value, fallback = UI_REFRESH_MIN_SEC) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return fallback
  }
  const rounded = Math.round(numeric)
  return Math.min(UI_REFRESH_MAX_SEC, Math.max(UI_REFRESH_MIN_SEC, rounded))
}

const normalizePollingIntervalMs = (refreshSec) => normalizeRefreshSeconds(refreshSec, UI_REFRESH_MIN_SEC) * 1000

const pickUiPrefScope = (source, scope) => {
  if (!isPlainObject(source)) {
    return {}
  }
  const candidate = source?.[scope]
  return isPlainObject(candidate) ? candidate : {}
}

const buildUiPrefsPayload = (source) => {
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

const resolveEffectiveUiPrefs = (source, deviceKind) => {
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

const updateUiPrefsSectionValue = (source, scope, key, value) => {
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
