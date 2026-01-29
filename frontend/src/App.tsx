import { useEffect, useMemo, useState } from 'react'
import './App.css'

type BotDefaults = {
  defaultMarket: string
  defaultSelectionMode: string
  defaultStrategyMode: string
  defaultRiskPreset: string
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
  availableMarkets: string[]
  availableSelectionModes: string[]
  availableStrategyModes: string[]
  availableRiskPresets: string[]
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

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

const demoRecommendations: Recommendation[] = [
  { market: 'KRW-BTC', score: 0.92, lastPrice: 88500000, volume24h: 42000000000, volatilityPct: 2.1, trendStrengthPct: 0.6 },
  { market: 'KRW-ETH', score: 0.87, lastPrice: 3720000, volume24h: 18000000000, volatilityPct: 2.4, trendStrengthPct: 0.4 },
  { market: 'KRW-SOL', score: 0.83, lastPrice: 155000, volume24h: 9200000000, volatilityPct: 3.2, trendStrengthPct: 0.9 },
  { market: 'KRW-XRP', score: 0.8, lastPrice: 960, volume24h: 7500000000, volatilityPct: 1.8, trendStrengthPct: -0.3 },
  { market: 'KRW-ADA', score: 0.77, lastPrice: 820, volume24h: 4300000000, volatilityPct: 2.0, trendStrengthPct: 0.2 },
]

const demoSummary: PaperSummary = {
  cashBalance: 620000,
  equity: 1015000,
  realizedPnl: 22000,
  unrealizedPnl: -7000,
  positions: [
    { market: 'KRW-BTC', quantity: 0.0042, entryPrice: 87200000, lastPrice: 88500000, unrealizedPnl: 5460, unrealizedPnlPct: 1.49 },
    { market: 'KRW-SOL', quantity: 1.9, entryPrice: 162000, lastPrice: 155000, unrealizedPnl: -13300, unrealizedPnlPct: -4.32 },
  ],
}

const demoPerformance: PaperPerformance = {
  totalReturnPct: 3.2,
  maxDrawdownPct: 2.8,
  daily: [
    { label: '01-23', equity: 980000, returnPct: -0.4 },
    { label: '01-24', equity: 988000, returnPct: 0.82 },
    { label: '01-25', equity: 1004000, returnPct: 1.62 },
    { label: '01-26', equity: 1022000, returnPct: 1.79 },
    { label: '01-27', equity: 1015000, returnPct: -0.69 },
    { label: '01-28', equity: 1030000, returnPct: 1.48 },
    { label: '01-29', equity: 1015000, returnPct: -1.46 },
  ],
  weekly: [
    { label: '01-06', equity: 965000, returnPct: 0 },
    { label: '01-13', equity: 995000, returnPct: 3.11 },
    { label: '01-20', equity: 1015000, returnPct: 2.01 },
  ],
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
  const [tenantName, setTenantName] = useState('')
  const [authError, setAuthError] = useState('')
  const [defaults, setDefaults] = useState<BotDefaults | null>(null)
  const [recommendations, setRecommendations] = useState<Recommendation[]>(demoRecommendations)
  const [paperSummary, setPaperSummary] = useState<PaperSummary>(demoSummary)
  const [performance, setPerformance] = useState<PaperPerformance>(demoPerformance)
  const [loading, setLoading] = useState(false)
  const [streamStatus, setStreamStatus] = useState<'idle' | 'live' | 'error'>('idle')
  const [lastUpdated, setLastUpdated] = useState('')

  const [configName, setConfigName] = useState('KRW Standard')
  const [market, setMarket] = useState('KRW')
  const [selectionMode, setSelectionMode] = useState('AUTO')
  const [strategyMode, setStrategyMode] = useState('AUTO')
  const [riskPreset, setRiskPreset] = useState('STANDARD')
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
    const source = new EventSource(streamUrl)
    setStreamStatus('idle')

    const handleStream = (event: MessageEvent) => {
      try {
        const payload = JSON.parse(event.data) as MarketStreamEvent
        if (!payload || !payload.recommendations) {
          return
        }
        setRecommendations(payload.recommendations)
        const timestamp = payload.timestamp ? new Date(payload.timestamp) : new Date()
        if (!Number.isNaN(timestamp.valueOf())) {
          setLastUpdated(timestamp.toLocaleTimeString('ko-KR'))
        }
      } catch (error) {
        // ignore parse errors
      }
    }

    source.addEventListener('recommendations', handleStream as EventListener)
    source.onmessage = handleStream
    source.onopen = () => {
      if (!closed) {
        setStreamStatus('live')
      }
    }
    source.onerror = () => {
      if (closed) {
        return
      }
      setStreamStatus('error')
      source.close()
      if (isAuthed) {
        apiFetch<Recommendation[]>(`/api/market/recommendations?topN=${autoPickTopN}`, token)
          .then(setRecommendations)
          .catch(() => null)
      }
    }

    return () => {
      closed = true
      source.close()
    }
  }, [autoPickTopN, isAuthed, token])

  useEffect(() => {
    if (!isAuthed) {
      return
    }

    const fetchAll = () => {
      apiFetch<PaperSummary>('/api/paper/summary', token)
        .then(setPaperSummary)
        .catch(() => null)
      apiFetch<PaperPerformance>('/api/paper/performance?days=7&weeks=4', token)
        .then(setPerformance)
        .catch(() => null)
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
    } catch (error) {
      setAuthError('로그인 실패. 데모 데이터로 표시합니다.')
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
        body: JSON.stringify({ tenantName, email, password }),
      })
      localStorage.setItem('token', response.token)
      setToken(response.token)
    } catch (error) {
      setAuthError('회원가입 실패. 입력값을 확인하세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleLogout = () => {
    localStorage.removeItem('token')
    setToken('')
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
      setAuthError('설정 저장 실패. API를 확인하세요.')
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
    if (streamStatus === 'error') return 'Offline'
    return 'Connecting'
  }, [streamStatus])

  const selectionLabel = useMemo(() => (selectionMode === 'MANUAL' ? 'Manual' : 'Auto'), [selectionMode])
  const positionCount = paperSummary.positions.length

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
        <div className="topbar-right">
          <span className={`stream-chip ${streamStatus}`}>{streamLabel}</span>
          <span className={`status-chip ${isAuthed ? 'online' : 'offline'}`}>
            {isAuthed ? 'API Connected' : 'Demo Mode'}
          </span>
          {isAuthed && (
            <button className="ghost" onClick={handleLogout}>
              로그아웃
            </button>
          )}
        </div>
      </header>

      <section className="hero">
        <div className="hero-copy">
          <p className="hero-kicker">Auto 전략 & 시그널 인덱스</p>
          <h1>실시간 시세 기반 자동 추천을 한 화면에서</h1>
          <p className="hero-desc">
            스트림 추천, 리스크 관리, 모의매매 성과를 한 곳에서 관리하세요. 모드별 기본 전략을 유지하면서
            필요한 곳만 미세 조정할 수 있습니다.
          </p>
          <div className="hero-meta">
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
          </div>
        </div>
        <div className="hero-cards">
          <div className="hero-card">
            <p>Live stream</p>
            <strong>{streamLabel}</strong>
            <span>{lastUpdated ? `최근 업데이트 ${lastUpdated}` : '업데이트 대기중'}</span>
          </div>
          <div className="hero-card">
            <p>Paper equity</p>
            <strong>\ {formatMoney(paperSummary.equity)}</strong>
            <span>보유 포지션 {positionCount}개</span>
          </div>
          <div className="hero-card">
            <p>Auto picks</p>
            <strong>Top {autoPickTopN}</strong>
            <span>시장 기준 {market}</span>
          </div>
        </div>
      </section>

      <main className="dashboard-grid">
        <section className="panel login-panel">
          <div className="panel-header">
            <h2>Access</h2>
            <span className="pill">Account</span>
          </div>
          <p className="panel-subtitle">로그인 후 실시간 추천과 모의매매 성과가 연결됩니다.</p>
          <div className="form-grid">
            <label>
              Email
              <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
            </label>
            <label>
              Password
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="8+ characters" />
            </label>
            <label>
              Tenant
              <input value={tenantName} onChange={(e) => setTenantName(e.target.value)} placeholder="brand" />
            </label>
          </div>
          {authError && <div className="alert">{authError}</div>}
          <div className="button-row">
            <button className="primary" onClick={handleLogin} disabled={loading}>
              로그인
            </button>
            <button className="ghost" onClick={handleRegister} disabled={loading}>
              회원가입
            </button>
          </div>
        </section>

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
              </p>
            </div>
            <div className="chip-row">
              <span className="pill">Top {autoPickTopN}</span>
              <span className={`pill stream ${streamStatus}`}>{streamLabel}</span>
            </div>
          </div>
          <div className="recommendation-list">
            {recommendations.map((coin, index) => (
              <div key={coin.market} className="recommendation-card">
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
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="panel paper-panel">
          <div className="panel-header">
            <h2>Paper Portfolio</h2>
            <span className="pill">Equity \ {formatMoney(paperSummary.equity)}</span>
          </div>
          <div className="stats-grid">
            <div>
              <p>Cash</p>
              <strong>\ {formatMoney(paperSummary.cashBalance)}</strong>
            </div>
            <div>
              <p>Unrealized</p>
              <strong className={paperSummary.unrealizedPnl >= 0 ? 'positive' : 'negative'}>
                {formatPct((paperSummary.unrealizedPnl / Math.max(paperSummary.equity, 1)) * 100)}
              </strong>
            </div>
            <div>
              <p>Realized</p>
              <strong className={paperSummary.realizedPnl >= 0 ? 'positive' : 'negative'}>
                \ {formatMoney(paperSummary.realizedPnl)}
              </strong>
            </div>
          </div>
          <div className="positions-table">
            <div className="positions-row header">
              <span>Market</span>
              <span>Entry</span>
              <span>Last</span>
              <span>PnL</span>
            </div>
            {paperSummary.positions.map((pos) => (
              <div key={pos.market} className="positions-row">
                <span>{pos.market}</span>
                <span>\ {formatMoney(pos.entryPrice)}</span>
                <span>\ {formatMoney(pos.lastPrice)}</span>
                <span className={pos.unrealizedPnl >= 0 ? 'positive' : 'negative'}>{formatPct(pos.unrealizedPnlPct)}</span>
              </div>
            ))}
            {paperSummary.positions.length === 0 && <p className="empty">No open positions</p>}
          </div>
        </section>

        <section className="panel performance-panel">
          <div className="panel-header">
            <h2>Performance</h2>
            <span className="pill">Return {formatPct(performance.totalReturnPct)}</span>
          </div>
          <p className="panel-subtitle">Max DD {formatPct(performance.maxDrawdownPct)} · 최근 7일</p>
          <div className="performance-chart">
            {performance.daily.map((point) => (
              <div key={point.label} className="performance-row">
                <span>{point.label}</span>
                <div className="bar-track">
                  <div
                    className={`bar ${point.returnPct >= 0 ? 'positive' : 'negative'}`}
                    style={{ width: `${Math.min(Math.abs(point.returnPct) * 6, 100)}%` }}
                  />
                </div>
                <span>{formatPct(point.returnPct)}</span>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  )
}

export default App
