import { useMemo, useState } from 'react'
import './App.css'

type PresetKey = 'Conservative' | 'Standard' | 'Aggressive'

const presetProfiles: Record<PresetKey, { riskPerTrade: string; dailyDd: string; weeklyDd: string; maxPositions: number }> = {
  Conservative: { riskPerTrade: '0.3%', dailyDd: '2%', weeklyDd: '5%', maxPositions: 2 },
  Standard: { riskPerTrade: '0.7%', dailyDd: '3%', weeklyDd: '8%', maxPositions: 3 },
  Aggressive: { riskPerTrade: '1.2%', dailyDd: '5%', weeklyDd: '12%', maxPositions: 5 },
}

const strategyModes = ['Auto', 'Scalp', 'Day', 'Swing'] as const
const markets = ['KRW', 'BTC', 'USDT'] as const
const selectionModes = ['Auto pick', 'Manual pick'] as const

const recommendedCoins = [
  { symbol: 'BTC', score: 92, liquidity: 'High', volatility: 'Medium' },
  { symbol: 'ETH', score: 88, liquidity: 'High', volatility: 'Medium' },
  { symbol: 'SOL', score: 84, liquidity: 'High', volatility: 'High' },
  { symbol: 'XRP', score: 81, liquidity: 'Medium', volatility: 'Medium' },
  { symbol: 'ADA', score: 79, liquidity: 'Medium', volatility: 'Medium' },
]

function App() {
  const [loggedIn, setLoggedIn] = useState(false)
  const [preset, setPreset] = useState<PresetKey>('Standard')
  const [strategyMode, setStrategyMode] = useState<(typeof strategyModes)[number]>('Auto')
  const [market, setMarket] = useState<(typeof markets)[number]>('KRW')
  const [selectionMode, setSelectionMode] = useState<(typeof selectionModes)[number]>('Auto pick')
  const [dailyDd, setDailyDd] = useState(3)
  const [weeklyDd, setWeeklyDd] = useState(8)
  const [maxPositions, setMaxPositions] = useState(3)
  const [autoPickTopN, setAutoPickTopN] = useState(5)

  const activePreset = presetProfiles[preset]

  const riskSummary = useMemo(() => {
    return {
      riskPerTrade: activePreset.riskPerTrade,
      dailyDd: `${dailyDd}%`,
      weeklyDd: `${weeklyDd}%`,
      maxPositions: maxPositions,
      autoPickTopN: autoPickTopN,
    }
  }, [activePreset, dailyDd, weeklyDd, maxPositions, autoPickTopN])

  return (
    <div className="app">
      <header className="app-header">
        <div className="brand">
          <span className="brand-mark">BTC</span>
          <div>
            <h1>Auto Trader</h1>
            <p>Upbit focused multi-coin console</p>
          </div>
        </div>
        <div className="status">
          <span className={`status-dot ${loggedIn ? 'online' : 'offline'}`} />
          <span>{loggedIn ? 'Engine ready' : 'Connect to start'}</span>
        </div>
      </header>

      <main className="app-grid">
        <section className="panel login-panel">
          <h2>Access</h2>
          <p className="panel-subtitle">Single server today. Multi-tenant ready for subscription later.</p>
          <div className="form-grid">
            <label>
              Email
              <input placeholder="you@example.com" />
            </label>
            <label>
              Password
              <input placeholder="8+ characters" type="password" />
            </label>
            <label>
              Tenant name
              <input placeholder="Your brand" />
            </label>
          </div>
          <div className="button-row">
            <button className="primary" onClick={() => setLoggedIn(true)}>
              Create account
            </button>
            <button className="ghost" onClick={() => setLoggedIn(true)}>
              Login
            </button>
          </div>
          <div className="note">
            Store API keys in the backend only. Keys are encrypted with APP_ENC_KEY.
          </div>
        </section>

        <section className="panel overview-panel">
          <div className="panel-header">
            <h2>Today runbook</h2>
            <span className="tag">Standard preset</span>
          </div>
          <div className="overview-grid">
            <div>
              <h3>Market</h3>
              <p>{market} base (BTC/USDT optional)</p>
            </div>
            <div>
              <h3>Mode</h3>
              <p>{strategyMode} strategy</p>
            </div>
            <div>
              <h3>Selection</h3>
              <p>{selectionMode} ? Top {autoPickTopN}</p>
            </div>
            <div>
              <h3>Risk</h3>
              <p>{riskSummary.riskPerTrade} per trade</p>
            </div>
          </div>
          <div className="divider" />
          <div className="disclaimer">
            Auto trader is not guaranteed profit. Use only risk capital.
          </div>
        </section>

        <section className="panel control-panel">
          <h2>Strategy controls</h2>
          <div className="control-grid">
            <label>
              Preset
              <select value={preset} onChange={(event) => setPreset(event.target.value as PresetKey)}>
                {Object.keys(presetProfiles).map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Strategy mode
              <select value={strategyMode} onChange={(event) => setStrategyMode(event.target.value as (typeof strategyModes)[number])}>
                {strategyModes.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Base market
              <select value={market} onChange={(event) => setMarket(event.target.value as (typeof markets)[number])}>
                {markets.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Coin selection
              <select
                value={selectionMode}
                onChange={(event) => setSelectionMode(event.target.value as (typeof selectionModes)[number])}
              >
                {selectionModes.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Daily drawdown limit (%)
              <input
                type="number"
                value={dailyDd}
                min={1}
                max={10}
                onChange={(event) => setDailyDd(Number(event.target.value))}
              />
            </label>
            <label>
              Weekly drawdown limit (%)
              <input
                type="number"
                value={weeklyDd}
                min={3}
                max={30}
                onChange={(event) => setWeeklyDd(Number(event.target.value))}
              />
            </label>
            <label>
              Max positions
              <input
                type="number"
                value={maxPositions}
                min={1}
                max={10}
                onChange={(event) => setMaxPositions(Number(event.target.value))}
              />
            </label>
            <label>
              Auto pick top N
              <input
                type="number"
                value={autoPickTopN}
                min={1}
                max={20}
                onChange={(event) => setAutoPickTopN(Number(event.target.value))}
              />
            </label>
          </div>
          <div className="button-row">
            <button className="primary">Save config</button>
            <button className="ghost">Run simulation</button>
          </div>
        </section>

        <section className="panel signal-panel">
          <h2>Auto pick recommendations</h2>
          <p className="panel-subtitle">Top 5 based on liquidity, spread, and trend strength.</p>
          <div className="coin-grid">
            {recommendedCoins.map((coin) => (
              <div key={coin.symbol} className="coin-card">
                <div>
                  <h3>{coin.symbol}</h3>
                  <p>Score {coin.score}</p>
                </div>
                <div className="coin-tags">
                  <span>{coin.liquidity}</span>
                  <span>{coin.volatility}</span>
                </div>
              </div>
            ))}
          </div>
          <div className="divider" />
          <div className="signal-footer">
            <button className="ghost">Review signals</button>
            <button className="primary">Start auto trade</button>
          </div>
        </section>
      </main>
    </div>
  )
}

export default App
