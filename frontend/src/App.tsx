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
    if (!isAuthed) {
      return
    }

    const fetchAll = () => {
      apiFetch<Recommendation[]>(`/api/market/recommendations?topN=${autoPickTopN}`, token)
        .then(setRecommendations)
        .catch(() => null)
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
  }, [isAuthed, token, autoPickTopN])

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

  const handleSaveConfig = async () => {
    if (!isAuthed) {
      setAuthError('로그인이 필요합니다.')
      return
    }
    setLoading(true)
    try {
      await apiFetch('/api/bot-configs', token, {
        method: 'POST',
        body: JSON.stringify({
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
        }),
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

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-logo">BT</div>
          <div>
            <p className="brand-title">BTC Auto Trader</p>
            <p className="brand-subtitle">KRW-first ? Strategy lab ? Paper trading</p>
          </div>
        </div>
        <div className="topbar-right">
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

      <main className="dashboard-grid">
        <section className="panel login-panel">
          <h2>Access</h2>
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
            <h2>Market Radar</h2>
            <span className="pill">Top {autoPickTopN}</span>
          </div>
          <p className="panel-subtitle">거래대금, 추세, 변동성 기반 자동 추천</p>
          <div className="recommendation-list">
            {recommendations.map((coin) => (
              <div key={coin.market} className="recommendation-card">
                <div>
                  <h3>{coin.market}</h3>
                  <span>Score {(coin.score * 100).toFixed(1)}</span>
                </div>
                <div className="recommendation-meta">
                  <span>\ {formatMoney(coin.lastPrice)}</span>
                  <span>Vol {coin.volatilityPct.toFixed(2)}%</span>
                  <span>Trend {coin.trendStrengthPct.toFixed(2)}%</span>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="panel paper-panel">
          <div className="panel-header">
            <h2>Paper Portfolio</h2>
            <span className="pill">Equity \{formatMoney(paperSummary.equity)}</span>
          </div>
          <div className="stats-grid">
            <div>
              <p>Cash</p>
              <strong>\ {formatMoney(paperSummary.cashBalance)}</strong>
            </div>
            <div>
              <p>Unrealized</p>
              <strong className={paperSummary.unrealizedPnl >= 0 ? 'positive' : 'negative'}>
                {formatPct(paperSummary.unrealizedPnl / Math.max(paperSummary.equity, 1) * 100)}
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
