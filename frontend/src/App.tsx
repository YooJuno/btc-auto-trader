import { useEffect, useMemo, useState } from 'react'
import './App.css'

type BotDefaults = {
  defaultMarket: string
  defaultSelectionMode: string
  defaultStrategyMode: string
  defaultRiskPreset: string
  defaultOperationMode: string
  defaultMaxPositions: number
  defaultDailyDrawdownPct: number
  defaultWeeklyDrawdownPct: number
  defaultAutoPickTopN: number
  defaultEmaFast: number
  defaultEmaSlow: number
  defaultRsiPeriod: number
  defaultAtrPeriod: number
  defaultBbPeriod: number
  defaultBbStdDev: number
  defaultTrendThreshold: number
  defaultVolatilityHigh: number
  defaultTrendRsiBuyMin: number
  defaultTrendRsiSellMax: number
  defaultRangeRsiBuyMax: number
  defaultRangeRsiSellMin: number
  engineEnabled: boolean
  engineIntervalMs: number
  availableMarkets: string[]
  availableSelectionModes: string[]
  availableStrategyModes: string[]
  availableRiskPresets: string[]
  availableOperationModes: string[]
}

type Recommendation = {
  market: string
  score: number
  lastPrice: number
  volume24h: number
  volatilityPct: number
  trendStrengthPct: number
}

type PaperPosition = {
  market: string
  quantity: number
  entryPrice: number
  lastPrice: number
  unrealizedPnl: number
  unrealizedPnlPct: number
}

type PaperSummary = {
  cashBalance: number
  equity: number
  realizedPnl: number
  unrealizedPnl: number
  positions: PaperPosition[]
}

type PerformancePoint = {
  label: string
  equity: number
  returnPct: number
}

type PaperPerformance = {
  totalReturnPct: number
  maxDrawdownPct: number
  daily: PerformancePoint[]
  weekly: PerformancePoint[]
}

type MarketStreamEvent = {
  timestamp: string
  recommendations: Recommendation[]
}

type MarketCandlePoint = {
  timestamp: string
  open: number
  high: number
  low: number
  close: number
}

type SignalTag = {
  label: string
  tone: 'up' | 'down' | 'flat' | 'warn'
}

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

const emptySummary: PaperSummary = {
  cashBalance: 0,
  equity: 0,
  realizedPnl: 0,
  unrealizedPnl: 0,
  positions: [],
}

const emptyPerformance: PaperPerformance = {
  totalReturnPct: 0,
  maxDrawdownPct: 0,
  daily: [],
  weekly: [],
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('ko-KR').format(Math.round(value))
}

function formatPct(value: number) {
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
}

function parseNumberInput(value: string): number | '' {
  if (value.trim() === '') {
    return ''
  }
  const parsed = Number(value)
  return Number.isNaN(parsed) ? '' : parsed
}

function toNumberOrNull(value: number | ''): number | null {
  if (value === '' || Number.isNaN(value)) {
    return null
  }
  return value
}

function buildSparkline(points: MarketCandlePoint[]): string {
  if (points.length < 2) {
    return ''
  }
  const values = points.map((point) => point.close)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * 100
      const y = 32 - ((value - min) / range) * 32
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
    })
    .join(' ')
}

function signalTag(coin: Recommendation): SignalTag {
  if (coin.volatilityPct >= 5) {
    return { label: 'VOLATILE', tone: 'warn' }
  }
  if (coin.trendStrengthPct >= 0.5) {
    return { label: 'TREND UP', tone: 'up' }
  }
  if (coin.trendStrengthPct <= -0.5) {
    return { label: 'TREND DOWN', tone: 'down' }
  }
  return { label: 'RANGE', tone: 'flat' }
}

async function apiFetch<T>(path: string, token?: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  })
  if (!response.ok) {
    throw new Error(await response.text())
  }
  return response.json() as Promise<T>
}

function App() {
  const [token, setToken] = useState<string>(() => localStorage.getItem('token') ?? '')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [authError, setAuthError] = useState('')
  const [defaults, setDefaults] = useState<BotDefaults | null>(null)
  const [recommendations, setRecommendations] = useState<Recommendation[]>([])
  const [paperSummary, setPaperSummary] = useState<PaperSummary>(emptySummary)
  const [performance, setPerformance] = useState<PaperPerformance>(emptyPerformance)
  const [recoStatus, setRecoStatus] = useState<'empty' | 'live' | 'error'>('empty')
  const [summaryStatus, setSummaryStatus] = useState<'empty' | 'live' | 'error'>('empty')
  const [performanceStatus, setPerformanceStatus] = useState<'empty' | 'live' | 'error'>('empty')
  const [loading, setLoading] = useState(false)
  const [streamStatus, setStreamStatus] = useState<'idle' | 'live' | 'reconnecting' | 'error'>('idle')
  const [lastUpdated, setLastUpdated] = useState('')
  const [marketFilter, setMarketFilter] = useState('')
  const [activeView, setActiveView] = useState<'overview' | 'portfolio' | 'config'>('overview')
  const [loginOpen, setLoginOpen] = useState(false)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [sparklines, setSparklines] = useState<Record<string, MarketCandlePoint[]>>({})
  const [chartMode, setChartMode] = useState<'equity' | 'candles'>('equity')
  const [chartCandles, setChartCandles] = useState<MarketCandlePoint[]>([])

  const [configName, setConfigName] = useState('KRW Standard')
  const [market, setMarket] = useState('KRW')
  const [selectionMode, setSelectionMode] = useState('AUTO')
  const [strategyMode, setStrategyMode] = useState('AUTO')
  const [riskPreset, setRiskPreset] = useState('STANDARD')
  const [operationMode, setOperationMode] = useState('STABLE')
  const [maxPositions, setMaxPositions] = useState(3)
  const [dailyDd, setDailyDd] = useState(3)
  const [weeklyDd, setWeeklyDd] = useState(8)
  const [autoPickTopN, setAutoPickTopN] = useState(5)
  const [manualMarkets, setManualMarkets] = useState('')
  const [advancedOpen, setAdvancedOpen] = useState(false)

  const [emaFast, setEmaFast] = useState<number | ''>('')
  const [emaSlow, setEmaSlow] = useState<number | ''>('')
  const [rsiPeriod, setRsiPeriod] = useState<number | ''>('')
  const [atrPeriod, setAtrPeriod] = useState<number | ''>('')
  const [bbPeriod, setBbPeriod] = useState<number | ''>('')
  const [bbStdDev, setBbStdDev] = useState<number | ''>('')
  const [trendThreshold, setTrendThreshold] = useState<number | ''>('')
  const [volatilityHigh, setVolatilityHigh] = useState<number | ''>('')
  const [trendRsiBuyMin, setTrendRsiBuyMin] = useState<number | ''>('')
  const [trendRsiSellMax, setTrendRsiSellMax] = useState<number | ''>('')
  const [rangeRsiBuyMax, setRangeRsiBuyMax] = useState<number | ''>('')
  const [rangeRsiSellMin, setRangeRsiSellMin] = useState<number | ''>('')

  const isAuthed = Boolean(token)

  useEffect(() => {
    apiFetch<BotDefaults>('/api/bot-configs/defaults')
      .then((data) => {
        setDefaults(data)
        setMarket(data.defaultMarket)
        setSelectionMode(data.defaultSelectionMode)
        setStrategyMode(data.defaultStrategyMode)
        setRiskPreset(data.defaultRiskPreset)
        setOperationMode(data.defaultOperationMode ?? 'STABLE')
        setMaxPositions(data.defaultMaxPositions)
        setDailyDd(data.defaultDailyDrawdownPct)
        setWeeklyDd(data.defaultWeeklyDrawdownPct)
        setAutoPickTopN(data.defaultAutoPickTopN)
      })
      .catch(() => null)
  }, [])

  useEffect(() => {
    const streamUrl = `${API_BASE}/api/market/stream?topN=${autoPickTopN}`
    let closed = false
    let source: EventSource | null = null
    let retryTimer: number | undefined
    let retryCount = 0

    const handleStream = (event: MessageEvent) => {
      try {
        const payload = JSON.parse(event.data) as MarketStreamEvent
        if (!payload || !payload.recommendations) {
          return
        }
        setRecommendations(payload.recommendations)
        setRecoStatus('live')
        const timestamp = payload.timestamp ? new Date(payload.timestamp) : new Date()
        if (!Number.isNaN(timestamp.valueOf())) {
          setLastUpdated(timestamp.toLocaleTimeString('ko-KR'))
        }
      } catch (error) {
        // ignore parse errors
      }
    }

    const connect = () => {
      if (closed) {
        return
      }
      setStreamStatus('idle')
      source = new EventSource(streamUrl)
      source.addEventListener('recommendations', handleStream as EventListener)
      source.onmessage = handleStream
      source.onopen = () => {
        if (!closed) {
          retryCount = 0
          setStreamStatus('live')
        }
      }
      source.onerror = () => {
        if (closed) {
          return
        }
        setStreamStatus('reconnecting')
        setRecoStatus((prev) => (prev === 'live' ? 'error' : 'empty'))
        source?.close()
        source = null
        if (isAuthed) {
          apiFetch<Recommendation[]>(`/api/market/recommendations?topN=${autoPickTopN}`, token)
            .then((data) => {
              setRecommendations(data)
              setRecoStatus('live')
            })
            .catch(() => null)
        }
        const delay = Math.min(30000, 1000 * Math.pow(2, retryCount))
        retryCount = Math.min(retryCount + 1, 5)
        retryTimer = window.setTimeout(connect, delay)
      }
    }

    connect()

    return () => {
      closed = true
      if (retryTimer) {
        window.clearTimeout(retryTimer)
      }
      source?.close()
    }
  }, [autoPickTopN, isAuthed, token])

  useEffect(() => {
    if (!isAuthed) {
      return
    }

    const fetchAll = () => {
      apiFetch<PaperSummary>('/api/paper/summary', token)
        .then((data) => {
          setPaperSummary(data)
          setSummaryStatus('live')
        })
        .catch(() => {
          setSummaryStatus((prev) => (prev === 'live' ? 'error' : 'empty'))
        })
      apiFetch<PaperPerformance>('/api/paper/performance?days=7&weeks=4', token)
        .then((data) => {
          setPerformance(data)
          setPerformanceStatus('live')
        })
        .catch(() => {
          setPerformanceStatus((prev) => (prev === 'live' ? 'error' : 'empty'))
        })
    }

    fetchAll()
    const interval = setInterval(fetchAll, 30000)
    return () => clearInterval(interval)
  }, [isAuthed, token])

  const handleLogin = async () => {
    setAuthError('')
    setLoading(true)
    try {
      const response = await apiFetch<{ token: string }>('/api/auth/login', undefined, {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      localStorage.setItem('token', response.token)
      setToken(response.token)
      setLoginOpen(false)
      setRegisterOpen(false)
    } catch (error) {
      setAuthError('로그인 실패. 이메일과 비밀번호를 확인해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = async () => {
    setAuthError('')
    setLoading(true)
    try {
      const response = await apiFetch<{ token: string }>('/api/auth/register', undefined, {
        method: 'POST',
        body: JSON.stringify({ tenantName: derivedTenantName, email, password }),
      })
      localStorage.setItem('token', response.token)
      setToken(response.token)
      setLoginOpen(false)
      setRegisterOpen(false)
    } catch (error) {
      setAuthError('회원가입 실패. 입력값을 확인해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleLogout = () => {
    localStorage.removeItem('token')
    setToken('')
    setRecommendations([])
    setPaperSummary(emptySummary)
    setPerformance(emptyPerformance)
    setRecoStatus('empty')
    setSummaryStatus('empty')
    setPerformanceStatus('empty')
    setLastUpdated('')
    setLoginOpen(false)
    setRegisterOpen(false)
  }

  const handleApplyDefaults = () => {
    if (!defaults) {
      return
    }
    setEmaFast(defaults.defaultEmaFast)
    setEmaSlow(defaults.defaultEmaSlow)
    setRsiPeriod(defaults.defaultRsiPeriod)
    setAtrPeriod(defaults.defaultAtrPeriod)
    setBbPeriod(defaults.defaultBbPeriod)
    setBbStdDev(defaults.defaultBbStdDev)
    setTrendThreshold(defaults.defaultTrendThreshold)
    setVolatilityHigh(defaults.defaultVolatilityHigh)
    setTrendRsiBuyMin(defaults.defaultTrendRsiBuyMin)
    setTrendRsiSellMax(defaults.defaultTrendRsiSellMax)
    setRangeRsiBuyMax(defaults.defaultRangeRsiBuyMax)
    setRangeRsiSellMin(defaults.defaultRangeRsiSellMin)
  }

  const handleClearOverrides = () => {
    setEmaFast('')
    setEmaSlow('')
    setRsiPeriod('')
    setAtrPeriod('')
    setBbPeriod('')
    setBbStdDev('')
    setTrendThreshold('')
    setVolatilityHigh('')
    setTrendRsiBuyMin('')
    setTrendRsiSellMax('')
    setRangeRsiBuyMax('')
    setRangeRsiSellMin('')
  }

  const handleSaveConfig = async () => {
    if (!isAuthed) {
      setAuthError('로그인이 필요합니다.')
      return
    }
    setLoading(true)
    try {
      const payload: Record<string, unknown> = {
        name: configName,
        baseMarket: market,
        selectionMode,
        strategyMode,
        riskPreset,
        operationMode,
        maxPositions,
        maxDailyDrawdownPct: dailyDd,
        maxWeeklyDrawdownPct: weeklyDd,
        autoPickTopN,
        manualMarkets,
      }

      const overrides = {
        emaFast: toNumberOrNull(emaFast),
        emaSlow: toNumberOrNull(emaSlow),
        rsiPeriod: toNumberOrNull(rsiPeriod),
        atrPeriod: toNumberOrNull(atrPeriod),
        bbPeriod: toNumberOrNull(bbPeriod),
        bbStdDev: toNumberOrNull(bbStdDev),
        trendThreshold: toNumberOrNull(trendThreshold),
        volatilityHigh: toNumberOrNull(volatilityHigh),
        trendRsiBuyMin: toNumberOrNull(trendRsiBuyMin),
        trendRsiSellMax: toNumberOrNull(trendRsiSellMax),
        rangeRsiBuyMax: toNumberOrNull(rangeRsiBuyMax),
        rangeRsiSellMin: toNumberOrNull(rangeRsiSellMin),
      }

      Object.entries(overrides).forEach(([key, value]) => {
        if (value !== null) {
          payload[key] = value
        }
      })

      await apiFetch('/api/bot-configs', token, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    } catch (error) {
      setAuthError('설정 저장 실패. API 연결을 확인해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleResetPaper = async () => {
    if (!isAuthed) {
      setAuthError('로그인이 필요합니다.')
      return
    }
    setLoading(true)
    try {
      const updated = await apiFetch<PaperSummary>('/api/paper/reset', token, {
        method: 'POST',
        body: JSON.stringify({ initialCash: 1000000 }),
      })
      setPaperSummary(updated)
      setSummaryStatus('live')
    } catch (error) {
      setAuthError('리셋 실패')
    } finally {
      setLoading(false)
    }
  }

  const riskBadge = useMemo(() => {
    if (riskPreset === 'CONSERVATIVE') return 'Low'
    if (riskPreset === 'AGGRESSIVE') return 'High'
    return 'Standard'
  }, [riskPreset])

  const streamLabel = useMemo(() => {
    if (streamStatus === 'live') return 'Live'
    if (streamStatus === 'reconnecting') return 'Reconnecting'
    if (streamStatus === 'error') return 'Offline'
    return 'Connecting'
  }, [streamStatus])

  const recommendationNote = useMemo(() => {
    if (recoStatus === 'error') return '서버 연결 끊김'
    if (recoStatus === 'empty') return '서버 연결 대기'
    return ''
  }, [recoStatus])

  const summaryNote = useMemo(() => {
    if (summaryStatus === 'error') return '서버 연결 끊김'
    if (summaryStatus === 'empty') return '서버 연결 대기'
    return ''
  }, [summaryStatus])

  const performanceNote = useMemo(() => {
    if (performanceStatus === 'error') return '서버 연결 끊김'
    if (performanceStatus === 'empty') return '서버 연결 대기'
    return ''
  }, [performanceStatus])

  const placeholderRecommendations = useMemo(() => {
    const count = Math.max(1, autoPickTopN)
    return Array.from({ length: count }, () => ({
      market: '--',
      score: 0,
      lastPrice: 0,
      volume24h: 0,
      volatilityPct: 0,
      trendStrengthPct: 0,
    }))
  }, [autoPickTopN])

  const displayRecommendations = recommendations.length > 0 ? recommendations : placeholderRecommendations
  const displaySummary = paperSummary ?? emptySummary
  const displayPerformance = performance ?? emptyPerformance

  const filteredRecommendations = useMemo(() => {
    const term = marketFilter.trim().toUpperCase()
    if (!term) {
      return displayRecommendations
    }
    return displayRecommendations.filter((coin) => coin.market.toUpperCase().includes(term))
  }, [marketFilter, displayRecommendations])

  const maxRecommendationsToShow = Math.min(5, autoPickTopN)
  const visibleRecommendations = filteredRecommendations.slice(0, maxRecommendationsToShow)
  const hiddenRecommendations = Math.max(0, filteredRecommendations.length - visibleRecommendations.length)

  const maxPositionsToShow = 6
  const visiblePositions = displaySummary.positions.slice(0, maxPositionsToShow)
  const hiddenPositions = Math.max(0, displaySummary.positions.length - visiblePositions.length)

  const signalTape = useMemo(() => {
    return displayRecommendations.slice(0, 4).map((coin) => ({
      market: coin.market,
      price: coin.lastPrice,
      score: coin.score,
      tag: signalTag(coin),
    }))
  }, [displayRecommendations])

  const chartSeries = useMemo(() => {
    if (displayPerformance.daily.length === 0) {
      return []
    }
    return displayPerformance.daily.slice(-7)
  }, [displayPerformance.daily])

  const chartData = useMemo(() => {
    if (chartSeries.length === 0) {
      return null
    }
    const values = chartSeries.map((point) => point.equity)
    const min = Math.min(...values)
    const max = Math.max(...values)
    const range = max - min || 1
    const points = chartSeries.map((point, index) => {
      const x = chartSeries.length === 1 ? 0 : (index / (chartSeries.length - 1)) * 100
      const y = 60 - ((point.equity - min) / range) * 60
      return { x, y, value: point.equity }
    })
    const line = points
      .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`)
      .join(' ')
    const area = `${line} L ${points[points.length - 1].x.toFixed(2)} 60 L 0 60 Z`
    return {
      min,
      max,
      last: points[points.length - 1],
      line,
      area,
    }
  }, [chartSeries])

  const preferredChartMarket = useMemo(() => {
    if (displaySummary.positions.length > 0) {
      return displaySummary.positions[0].market
    }
    const reco = displayRecommendations.find((item) => item.market && item.market !== '--')
    return reco?.market ?? ''
  }, [displaySummary.positions, displayRecommendations])

  useEffect(() => {
    if (chartMode !== 'candles') {
      return
    }
    if (!preferredChartMarket) {
      setChartCandles([])
      return
    }
    let cancelled = false
    apiFetch<MarketCandlePoint[]>(
      `/api/market/candles?market=${encodeURIComponent(preferredChartMarket)}&limit=40`
    )
      .then((data) => {
        if (!cancelled) {
          setChartCandles(data)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setChartCandles([])
        }
      })
    return () => {
      cancelled = true
    }
  }, [chartMode, preferredChartMarket])

  const candleChart = useMemo(() => {
    if (chartMode !== 'candles' || chartCandles.length === 0) {
      return null
    }
    const highs = chartCandles.map((candle) => candle.high)
    const lows = chartCandles.map((candle) => candle.low)
    const min = Math.min(...lows)
    const max = Math.max(...highs)
    const range = max - min || 1
    const count = chartCandles.length
    const step = count <= 1 ? 100 : 100 / count
    const bodyWidth = Math.max(2, step * 0.6)
    const candles = chartCandles.map((candle, index) => {
      const x = step * index + step / 2
      const highY = 60 - ((candle.high - min) / range) * 60
      const lowY = 60 - ((candle.low - min) / range) * 60
      const openY = 60 - ((candle.open - min) / range) * 60
      const closeY = 60 - ((candle.close - min) / range) * 60
      return {
        x,
        highY,
        lowY,
        openY,
        closeY,
        bodyTop: Math.min(openY, closeY),
        bodyHeight: Math.max(2, Math.abs(openY - closeY)),
        up: candle.close >= candle.open,
      }
    })
    return { candles, min, max, bodyWidth }
  }, [chartMode, chartCandles])
  const selectionLabel = useMemo(() => (selectionMode === 'MANUAL' ? '수동' : '자동'), [selectionMode])
  const operationLabel = useMemo(() => (operationMode === 'ATTACK' ? '공격' : '안정'), [operationMode])
  const positionCount = displaySummary.positions.length
  const operationOptions = defaults?.availableOperationModes?.length
    ? defaults.availableOperationModes
    : ['STABLE', 'ATTACK']
  const derivedTenantName = useMemo(() => {
    const base = email.split('@')[0]?.trim() || 'default'
    const sanitized = base.replace(/[^a-zA-Z0-9-_]/g, '')
    return sanitized || 'default'
  }, [email])

  const operationLabelFor = (mode: string) => {
    if (mode === 'ATTACK') return '공격'
    if (mode === 'STABLE') return '안정'
    return mode
  }

  const sparklineMarkets = useMemo(() => {
    const unique = new Set<string>()
    visibleRecommendations.forEach((coin) => {
      if (coin.market && coin.market !== '--') {
        unique.add(coin.market)
      }
    })
    return Array.from(unique)
  }, [visibleRecommendations])

  const sparklineKey = useMemo(() => sparklineMarkets.join('|'), [sparklineMarkets])

  useEffect(() => {
    if (sparklineMarkets.length === 0) {
      return
    }
    let cancelled = false
    sparklineMarkets.forEach((market) => {
      apiFetch<MarketCandlePoint[]>(`/api/market/candles?market=${encodeURIComponent(market)}&limit=30`)
        .then((data) => {
          if (cancelled) return
          setSparklines((prev) => ({ ...prev, [market]: data }))
        })
        .catch(() => null)
    })
    return () => {
      cancelled = true
    }
  }, [sparklineKey, sparklineMarkets])

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-logo">BT</div>
          <div>
            <p className="brand-title">BTC Auto Trader</p>
            <p className="brand-subtitle">KRW-first / Strategy lab / Paper trading</p>
          </div>
        </div>
        <nav className="topnav">
          <button
            className={`nav-pill ${activeView === 'overview' ? 'active' : ''}`}
            type="button"
            onClick={() => setActiveView('overview')}
          >
            Overview
          </button>
          <button
            className={`nav-pill ${activeView === 'portfolio' ? 'active' : ''}`}
            type="button"
            onClick={() => setActiveView('portfolio')}
          >
            Portfolio
          </button>
          <button
            className={`nav-pill ${activeView === 'config' ? 'active' : ''}`}
            type="button"
            onClick={() => setActiveView('config')}
          >
            Config
          </button>
        </nav>
        <div className="top-actions">
          <label className="search-box">
            <span>Search</span>
            <input
              value={marketFilter}
              onChange={(event) => setMarketFilter(event.target.value)}
              placeholder="KRW-BTC"
            />
          </label>
          <span className={`stream-chip ${streamStatus}`}>{streamLabel}</span>
          <span
            className={`status-chip ${
              streamStatus === 'live' ? 'online' : streamStatus === 'reconnecting' ? 'reconnecting' : 'offline'
            }`}
          >
            {streamStatus === 'live' ? 'Upbit Online' : streamStatus === 'reconnecting' ? 'Upbit Reconnecting' : 'Upbit Offline'}
          </span>
          {!isAuthed && (
            <button
              className="primary small login-button"
              onClick={() => {
                setAuthError('')
                setRegisterOpen(false)
                setLoginOpen(true)
              }}
            >
              로그인
            </button>
          )}
          {!isAuthed && (
            <button
              className="ghost small login-button"
              onClick={() => {
                setAuthError('')
                setLoginOpen(false)
                setRegisterOpen(true)
              }}
            >
              회원가입
            </button>
          )}
          {isAuthed && (
            <button className="ghost" onClick={handleLogout}>
              로그아웃
            </button>
          )}
        </div>
      </header>

      <section className="command-strip">
        <div className="strip-card">
          <span>Stream</span>
          <strong>{streamLabel}</strong>
          <p>{lastUpdated ? `최근 업데이트 ${lastUpdated}` : '업데이트 대기중'}</p>
        </div>
        <div className="strip-card">
          <span>Paper equity</span>
          <strong>\ {formatMoney(displaySummary.equity)}</strong>
          <p>{summaryNote || `보유 포지션 ${positionCount}개`}</p>
        </div>
        <div className="strip-card">
          <span>Risk guard</span>
          <strong>{operationLabel}</strong>
          <p>{riskBadge} · Daily {dailyDd}% · Weekly {weeklyDd}%</p>
        </div>
        <div className="strip-card">
          <span>Auto picks</span>
          <strong>Top {autoPickTopN}</strong>
          <p>{selectionLabel} · {market}</p>
        </div>
      </section>

      <main className={`terminal-grid view-${activeView}`}>
        <section className="panel strategy-panel">
          <div className="panel-header">
            <h2>Strategy Builder</h2>
            <span className="pill">{riskBadge} risk</span>
          </div>
          <div className="control-grid">
            <label>
              Config name
              <input value={configName} onChange={(e) => setConfigName(e.target.value)} />
            </label>
            <label>
              Base market
              <select value={market} onChange={(e) => setMarket(e.target.value)}>
                {defaults?.availableMarkets?.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                )) ?? <option value="KRW">KRW</option>}
              </select>
            </label>
            <label>
              Selection mode
              <select value={selectionMode} onChange={(e) => setSelectionMode(e.target.value)}>
                {defaults?.availableSelectionModes?.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                )) ?? (
                  <>
                    <option value="AUTO">AUTO</option>
                    <option value="MANUAL">MANUAL</option>
                  </>
                )}
              </select>
            </label>
            <label>
              Strategy mode
              <select value={strategyMode} onChange={(e) => setStrategyMode(e.target.value)}>
                {defaults?.availableStrategyModes?.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                )) ?? (
                  <>
                    <option value="AUTO">AUTO</option>
                    <option value="SCALP">SCALP</option>
                    <option value="DAY">DAY</option>
                    <option value="SWING">SWING</option>
                  </>
                )}
              </select>
            </label>
            <div className="mode-toggle">
              <span>Operation mode</span>
              <div className="mode-buttons">
                {operationOptions.map((mode) => (
                  <button
                    key={mode}
                    type="button"
                    className={`mode-pill ${operationMode === mode ? 'active' : ''}`}
                    onClick={() => setOperationMode(mode)}
                  >
                    {operationLabelFor(mode)}
                  </button>
                ))}
              </div>
            </div>
            <label>
              Risk preset
              <select value={riskPreset} onChange={(e) => setRiskPreset(e.target.value)}>
                {defaults?.availableRiskPresets?.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                )) ?? (
                  <>
                    <option value="CONSERVATIVE">CONSERVATIVE</option>
                    <option value="STANDARD">STANDARD</option>
                    <option value="AGGRESSIVE">AGGRESSIVE</option>
                  </>
                )}
              </select>
            </label>
            <label>
              Max positions
              <input type="number" value={maxPositions} min={1} max={10} onChange={(e) => setMaxPositions(Number(e.target.value))} />
            </label>
            <label>
              Daily drawdown (%)
              <input type="number" value={dailyDd} min={1} max={10} onChange={(e) => setDailyDd(Number(e.target.value))} />
            </label>
            <label>
              Weekly drawdown (%)
              <input type="number" value={weeklyDd} min={3} max={30} onChange={(e) => setWeeklyDd(Number(e.target.value))} />
            </label>
            <label>
              Auto pick top N
              <input type="number" value={autoPickTopN} min={1} max={20} onChange={(e) => setAutoPickTopN(Number(e.target.value))} />
            </label>
          </div>
          {selectionMode === 'MANUAL' && (
            <label className="full-width">
              Manual markets (KRW-BTC,KRW-ETH or BTC,ETH)
              <input value={manualMarkets} onChange={(e) => setManualMarkets(e.target.value)} />
            </label>
          )}

          <div className="advanced-section">
            <div className="advanced-header">
              <div>
                <h3>Advanced tuning</h3>
                <p className="hint">비워두면 기본 전략값을 사용합니다.</p>
              </div>
              <button className="ghost small" onClick={() => setAdvancedOpen((prev) => !prev)}>
                {advancedOpen ? '숨기기' : '열기'}
              </button>
            </div>
            {advancedOpen && (
              <>
                <div className="advanced-grid">
                  <label>
                    EMA fast
                    <input
                      type="number"
                      value={emaFast}
                      placeholder={`${defaults?.defaultEmaFast ?? 12}`}
                      onChange={(e) => setEmaFast(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    EMA slow
                    <input
                      type="number"
                      value={emaSlow}
                      placeholder={`${defaults?.defaultEmaSlow ?? 26}`}
                      onChange={(e) => setEmaSlow(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    RSI period
                    <input
                      type="number"
                      value={rsiPeriod}
                      placeholder={`${defaults?.defaultRsiPeriod ?? 14}`}
                      onChange={(e) => setRsiPeriod(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    ATR period
                    <input
                      type="number"
                      value={atrPeriod}
                      placeholder={`${defaults?.defaultAtrPeriod ?? 14}`}
                      onChange={(e) => setAtrPeriod(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    BB period
                    <input
                      type="number"
                      value={bbPeriod}
                      placeholder={`${defaults?.defaultBbPeriod ?? 20}`}
                      onChange={(e) => setBbPeriod(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    BB std dev
                    <input
                      type="number"
                      step="0.1"
                      value={bbStdDev}
                      placeholder={`${defaults?.defaultBbStdDev ?? 2.0}`}
                      onChange={(e) => setBbStdDev(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    Trend threshold (0.005 = 0.5%)
                    <input
                      type="number"
                      step="0.001"
                      value={trendThreshold}
                      placeholder={`${defaults?.defaultTrendThreshold ?? 0.005}`}
                      onChange={(e) => setTrendThreshold(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    Volatility high (0.06 = 6%)
                    <input
                      type="number"
                      step="0.01"
                      value={volatilityHigh}
                      placeholder={`${defaults?.defaultVolatilityHigh ?? 0.06}`}
                      onChange={(e) => setVolatilityHigh(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    Trend RSI buy min
                    <input
                      type="number"
                      value={trendRsiBuyMin}
                      placeholder={`${defaults?.defaultTrendRsiBuyMin ?? 52}`}
                      onChange={(e) => setTrendRsiBuyMin(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    Trend RSI sell max
                    <input
                      type="number"
                      value={trendRsiSellMax}
                      placeholder={`${defaults?.defaultTrendRsiSellMax ?? 48}`}
                      onChange={(e) => setTrendRsiSellMax(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    Range RSI buy max
                    <input
                      type="number"
                      value={rangeRsiBuyMax}
                      placeholder={`${defaults?.defaultRangeRsiBuyMax ?? 35}`}
                      onChange={(e) => setRangeRsiBuyMax(parseNumberInput(e.target.value))}
                    />
                  </label>
                  <label>
                    Range RSI sell min
                    <input
                      type="number"
                      value={rangeRsiSellMin}
                      placeholder={`${defaults?.defaultRangeRsiSellMin ?? 65}`}
                      onChange={(e) => setRangeRsiSellMin(parseNumberInput(e.target.value))}
                    />
                  </label>
                </div>
                <div className="advanced-actions">
                  <button className="ghost small" onClick={handleApplyDefaults} type="button">
                    기본값 채우기
                  </button>
                  <button className="ghost small" onClick={handleClearOverrides} type="button">
                    비우기
                  </button>
                </div>
              </>
            )}
          </div>
          <div className="button-row">
            <button className="primary" onClick={handleSaveConfig} disabled={loading}>
              설정 저장
            </button>
            <button className="ghost" onClick={handleResetPaper} disabled={loading}>
              모의계좌 리셋
            </button>
          </div>
        </section>

        <section className="panel recommendation-panel">
          <div className="panel-header">
            <div>
              <h2>Market Radar</h2>
              <p className="panel-subtitle">
                거래대금, 추세, 변동성 기반 자동 추천 {lastUpdated ? `/ 최근 업데이트 ${lastUpdated}` : ''}
                {recommendationNote ? ` / ${recommendationNote}` : ''}
              </p>
            </div>
            <div className="chip-row">
              <span className="pill">Top {autoPickTopN}</span>
              <span className={`pill stream ${streamStatus}`}>{streamLabel}</span>
            </div>
          </div>
          <div className="recommendation-list">
            {filteredRecommendations.length === 0 ? (
              <p className="empty">검색 결과가 없습니다.</p>
            ) : (
              <>
                {visibleRecommendations.map((coin, index) => {
                  const sparkPoints = sparklines[coin.market] ?? []
                  const sparkPath = sparkPoints.length > 1 ? buildSparkline(sparkPoints) : ''
                  const sparkTone =
                    sparkPoints.length > 1 && sparkPoints[sparkPoints.length - 1].close >= sparkPoints[0].close ? 'up' : 'down'
                  return (
                    <div key={`${coin.market}-${index}`} className="recommendation-card">
                      <div className="recommendation-main">
                        <div className="rank">#{index + 1}</div>
                        <div>
                          <h3>{coin.market}</h3>
                          <span>Score {(coin.score * 100).toFixed(1)}</span>
                        </div>
                      </div>
                      <div className="recommendation-meta">
                        <span>\ {formatMoney(coin.lastPrice)}</span>
                        <span>Vol {coin.volatilityPct.toFixed(2)}%</span>
                        <span>Trend {coin.trendStrengthPct.toFixed(2)}%</span>
                        <span>24h {formatMoney(coin.volume24h)}</span>
                        <div className={`sparkline ${sparkPath ? sparkTone : 'idle'}`}>
                          {sparkPath ? (
                            <svg viewBox="0 0 100 32" preserveAspectRatio="none">
                              <path d={sparkPath} fill="none" strokeWidth="2" />
                            </svg>
                          ) : (
                            <span>차트 대기</span>
                          )}
                        </div>
                      </div>
                    </div>
                  )
                })}
                {hiddenRecommendations > 0 && <p className="more-note">외 {hiddenRecommendations}개 표시</p>}
              </>
            )}
          </div>
        </section>

        <section className="panel account-panel">
          <div className="panel-header">
            <h2>Account Snapshot</h2>
            <span className="pill">Equity \ {formatMoney(displaySummary.equity)}</span>
          </div>
          <p className="panel-subtitle">
            {summaryNote || `최근 업데이트 ${lastUpdated || '대기중'}`}
          </p>
          <div className="stats-grid">
            <div>
              <p>Cash balance</p>
              <strong>\ {formatMoney(displaySummary.cashBalance)}</strong>
            </div>
            <div>
              <p>Unrealized PnL</p>
              <strong className={displaySummary.unrealizedPnl >= 0 ? 'positive' : 'negative'}>
                \ {formatMoney(displaySummary.unrealizedPnl)}
              </strong>
            </div>
            <div>
              <p>Realized PnL</p>
              <strong className={displaySummary.realizedPnl >= 0 ? 'positive' : 'negative'}>
                \ {formatMoney(displaySummary.realizedPnl)}
              </strong>
            </div>
            <div>
              <p>Open positions</p>
              <strong>{positionCount}개</strong>
            </div>
          </div>
          <div className="account-footer">
            <div>
              <span className="label">Stream</span>
              <strong>{streamLabel}</strong>
            </div>
            <div>
              <span className="label">Selection</span>
              <strong>{selectionLabel}</strong>
            </div>
            <div>
              <span className="label">Strategy</span>
              <strong>{strategyMode}</strong>
            </div>
          </div>
        </section>

        <section className="panel holdings-panel">
          <div className="panel-header">
            <h2>Holdings</h2>
            <span className="pill">Positions {positionCount}</span>
          </div>
          {summaryNote && <p className="panel-subtitle">{summaryNote}</p>}
          <div className="positions-table">
            <div className="positions-row header">
              <span>Market</span>
              <span>Qty</span>
              <span>Entry</span>
              <span>Buy</span>
              <span>Last</span>
              <span>PnL</span>
              <span>PnL ₩</span>
            </div>
            {visiblePositions.map((pos) => (
              <div key={pos.market} className="positions-row">
                <span>{pos.market}</span>
                <span>{pos.quantity.toFixed(4)}</span>
                <span>\ {formatMoney(pos.entryPrice)}</span>
                <span>\ {formatMoney(pos.entryPrice * pos.quantity)}</span>
                <span>\ {formatMoney(pos.lastPrice)}</span>
                <span className={pos.unrealizedPnl >= 0 ? 'positive' : 'negative'}>
                  {formatPct(pos.unrealizedPnlPct)}
                </span>
                <span className={pos.unrealizedPnl >= 0 ? 'positive' : 'negative'}>
                  \ {formatMoney(pos.unrealizedPnl)}
                </span>
              </div>
            ))}
            {displaySummary.positions.length === 0 && <p className="empty">보유 포지션 없음</p>}
            {hiddenPositions > 0 && <p className="more-note">외 {hiddenPositions}개 보유중</p>}
          </div>
        </section>

        <section className="panel chart-panel">
          <div className="panel-header">
            <div>
              <h2>Chart</h2>
              <p className="panel-subtitle">
                {chartMode === 'candles'
                  ? `Market ${preferredChartMarket || '대기중'}`
                  : 'Equity trend'}
              </p>
            </div>
            <div className="chip-row">
              <button
                className={`pill button-pill ${chartMode === 'equity' ? 'active' : ''}`}
                type="button"
                onClick={() => setChartMode('equity')}
              >
                Line
              </button>
              <button
                className={`pill button-pill ${chartMode === 'candles' ? 'active' : ''}`}
                type="button"
                onClick={() => setChartMode('candles')}
              >
                Candles
              </button>
            </div>
          </div>
          <div className="performance-chart">
            {chartMode === 'candles' ? (
              candleChart ? (
                <div className="performance-visual large">
                  <div className="chart-wrapper tall">
                    <svg className="chart-svg" viewBox="0 0 100 60" preserveAspectRatio="none">
                      <defs>
                        <pattern id="candleGrid" width="10" height="10" patternUnits="userSpaceOnUse">
                          <path d="M 10 0 L 0 0 0 10" fill="none" stroke="rgba(255, 255, 255, 0.08)" strokeWidth="0.4" />
                        </pattern>
                      </defs>
                      <rect width="100" height="60" fill="url(#candleGrid)" />
                      {candleChart.candles.map((candle, index) => (
                        <g key={index}>
                          <line
                            x1={candle.x}
                            y1={candle.highY}
                            x2={candle.x}
                            y2={candle.lowY}
                            stroke={candle.up ? 'rgba(96, 230, 134, 0.9)' : 'rgba(255, 111, 111, 0.9)'}
                            strokeWidth="1"
                          />
                          <rect
                            x={candle.x - candleChart.bodyWidth / 2}
                            y={candle.bodyTop}
                            width={candleChart.bodyWidth}
                            height={candle.bodyHeight}
                            fill={candle.up ? 'rgba(96, 230, 134, 0.5)' : 'rgba(255, 111, 111, 0.5)'}
                            stroke={candle.up ? 'rgba(96, 230, 134, 0.9)' : 'rgba(255, 111, 111, 0.9)'}
                            strokeWidth="0.6"
                          />
                        </g>
                      ))}
                    </svg>
                  </div>
                  <div className="chart-legend">
                    <div>
                      <span>High</span>
                      <strong>\ {formatMoney(candleChart.max)}</strong>
                    </div>
                    <div>
                      <span>Low</span>
                      <strong>\ {formatMoney(candleChart.min)}</strong>
                    </div>
                    <div>
                      <span>Last</span>
                      <strong>\ {formatMoney(chartCandles[chartCandles.length - 1].close)}</strong>
                    </div>
                  </div>
                </div>
              ) : (
                <p className="empty">캔들 데이터 수신 대기중</p>
              )
            ) : chartData ? (
              <>
                <div className="performance-visual large">
                  <div className="chart-wrapper tall">
                    <svg className="chart-svg" viewBox="0 0 100 60" preserveAspectRatio="none">
                      <defs>
                        <pattern id="chartGrid" width="10" height="10" patternUnits="userSpaceOnUse">
                          <path d="M 10 0 L 0 0 0 10" fill="none" stroke="rgba(255, 255, 255, 0.08)" strokeWidth="0.4" />
                        </pattern>
                        <linearGradient id="chartLine" x1="0" y1="0" x2="1" y2="0">
                          <stop offset="0%" stopColor="#ffbf6b" stopOpacity="0.9" />
                          <stop offset="100%" stopColor="#57d9c6" stopOpacity="0.9" />
                        </linearGradient>
                        <linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="rgba(255, 191, 107, 0.35)" />
                          <stop offset="100%" stopColor="rgba(10, 15, 20, 0.05)" />
                        </linearGradient>
                      </defs>
                      <rect width="100" height="60" fill="url(#chartGrid)" />
                      <path d={chartData.area} fill="url(#chartFill)" />
                      <path d={chartData.line} fill="none" stroke="url(#chartLine)" strokeWidth="1.8" />
                      <circle cx={chartData.last.x} cy={chartData.last.y} r="3" fill="rgba(255, 191, 107, 0.25)" />
                      <circle cx={chartData.last.x} cy={chartData.last.y} r="1.4" fill="#ffbf6b" />
                    </svg>
                  </div>
                  <div className="chart-legend">
                    <div>
                      <span>최근</span>
                      <strong>\ {formatMoney(chartData.last.value)}</strong>
                    </div>
                    <div>
                      <span>최저</span>
                      <strong>\ {formatMoney(chartData.min)}</strong>
                    </div>
                    <div>
                      <span>최고</span>
                      <strong>\ {formatMoney(chartData.max)}</strong>
                    </div>
                  </div>
                </div>
              </>
            ) : (
              <p className="empty">성과 데이터 수신 대기중</p>
            )}
          </div>
        </section>

        <section className="panel auto-panel">
          <div className="panel-header">
            <h2>Auto Trading</h2>
            <span className="pill">{operationLabel}</span>
          </div>
          <p className="panel-subtitle">자동 매매 설정과 추천 시그널을 요약합니다.</p>
          <div className="auto-grid">
            <div>
              <span>Selection</span>
              <strong>{selectionLabel}</strong>
            </div>
            <div>
              <span>Strategy</span>
              <strong>{strategyMode}</strong>
            </div>
            <div>
              <span>Risk</span>
              <strong>{riskBadge}</strong>
            </div>
            <div>
              <span>Top N</span>
              <strong>{autoPickTopN}</strong>
            </div>
            <div>
              <span>Max positions</span>
              <strong>{maxPositions}</strong>
            </div>
            <div>
              <span>DD limits</span>
              <strong>{dailyDd}% / {weeklyDd}%</strong>
            </div>
            <div>
              <span>Engine</span>
              <strong>{defaults?.engineEnabled ? 'ON' : 'OFF'}</strong>
            </div>
            <div>
              <span>Rebalance</span>
              <strong>
                {defaults?.engineIntervalMs ? `${Math.round(defaults.engineIntervalMs / 1000)}s` : '-'}
              </strong>
            </div>
          </div>
          {selectionMode === 'MANUAL' && manualMarkets && (
            <p className="hint">Manual: {manualMarkets}</p>
          )}
          <div className="signal-list compact">
            {signalTape.map((item, index) => (
              <div key={`${item.market}-${index}`} className="signal-item">
                <div>
                  <p>{item.market}</p>
                  <strong>\ {formatMoney(item.price)}</strong>
                </div>
                <div className="signal-right">
                  <span className={`signal-badge ${item.tag.tone}`}>{item.tag.label}</span>
                  <span className="signal-score">{(item.score * 100).toFixed(0)}</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      </main>

      {loginOpen && (
        <div className="modal-backdrop" onClick={() => setLoginOpen(false)}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>로그인</h3>
                <p>실시간 추천과 모의매매 성과를 연결합니다.</p>
              </div>
              <button className="ghost small" onClick={() => setLoginOpen(false)}>
                닫기
              </button>
            </div>
            <div className="form-grid">
              <label>
                Email
                <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
              </label>
              <label>
                Password
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="8+ characters" />
              </label>
            </div>
            {authError && <div className="alert">{authError}</div>}
            <div className="button-row">
              <button className="primary" onClick={handleLogin} disabled={loading}>
                로그인
              </button>
              <button
                className="ghost"
                onClick={() => {
                  setAuthError('')
                  setLoginOpen(false)
                  setRegisterOpen(true)
                }}
                disabled={loading}
              >
                회원가입으로
              </button>
            </div>
          </div>
        </div>
      )}
      {registerOpen && (
        <div className="modal-backdrop" onClick={() => setRegisterOpen(false)}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>회원가입</h3>
                <p>이메일로 자동 테넌트를 생성합니다. (예: {derivedTenantName})</p>
              </div>
              <button className="ghost small" onClick={() => setRegisterOpen(false)}>
                닫기
              </button>
            </div>
            <div className="form-grid">
              <label>
                Email
                <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
              </label>
              <label>
                Password
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="8+ characters" />
              </label>
            </div>
            {authError && <div className="alert">{authError}</div>}
            <div className="button-row">
              <button className="primary" onClick={handleRegister} disabled={loading}>
                회원가입
              </button>
              <button
                className="ghost"
                onClick={() => {
                  setAuthError('')
                  setRegisterOpen(false)
                  setLoginOpen(true)
                }}
                disabled={loading}
              >
                로그인으로
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default App
