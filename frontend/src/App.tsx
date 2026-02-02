import { useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import AppRoutes from './AppRoutes'
// pages are lazy loaded in AppRoutes
import Header from './components/Header'
import AuthModals from './components/AuthModals'
import useDefaults from './hooks/useDefaults'
import useMarketStream from './hooks/useMarketStream'
import usePaperData from './hooks/usePaperData'
import useRecommendations from './hooks/useRecommendations'
import useAuth from './hooks/useAuth'
import useAutomation from './hooks/useAutomation'
import useChartData from './hooks/useChartData'
import './App.css'

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




import { formatMoney, formatPct } from './lib/utils'
import useAutomationProps from './hooks/useAutomationProps'
import type { PaperSummary, PaperPerformance } from './lib/types'

function App() {
  // Auth & UI state delegated to hooks
  const auth = useAuth()
  const { token, email, setEmail, password, setPassword, authError, setAuthError, loading, loginOpen, setLoginOpen, registerOpen, setRegisterOpen, isAuthed, handleLogin, handleRegister, handleLogout } = auth

  const [marketFilter, setMarketFilter] = useState('')
  const [focusedMarket, setFocusedMarket] = useState('')
  const [chartMode, setChartMode] = useState<'equity' | 'candles'>('equity')

  // Hooks for data
  const { defaults: loadedDefaults } = useDefaults()
  const { paperSummary: ps, performance: perf, summaryStatus: psStatus, resetPaper } = usePaperData(token)
  const automation = useAutomation({ token, loadedDefaults, resetPaper, onError: setAuthError })
  const { recommendations: streamRecommendations, recoStatus: streamRecoStatus, streamStatus: streamState, lastUpdated: streamLastUpdated } = useMarketStream(automation.autoPickTopN, token)

  const location = useLocation()
  const activeViewFromPath = (() => {
    const p = location.pathname || '/'
    if (p === '/' || p === '') return 'dashboard'
    if (p.startsWith('/holdings')) return 'holdings'
    if (p.startsWith('/automation')) return 'automation'
    return 'dashboard'
  })()


  const riskBadge = useMemo(() => {
    if (automation.riskPreset === 'CONSERVATIVE') return 'Low'
    if (automation.riskPreset === 'AGGRESSIVE') return 'High'
    return 'Standard'
  }, [automation.riskPreset])

  const streamLabel = useMemo(() => {
    if (streamState === 'live') return 'Live'
    if (streamState === 'reconnecting') return 'Reconnecting'
    if (streamState === 'error') return 'Offline'
    return 'Connecting'
  }, [streamState])

  const recommendationNote = useMemo(() => {
    if (streamRecoStatus === 'error') return '서버 연결 끊김'
    if (streamRecoStatus === 'empty') return '서버 연결 대기'
    return ''
  }, [streamRecoStatus])



  const summaryNote = useMemo(() => {
    if (psStatus === 'error') return '서버 연결 끊김'
    if (psStatus === 'empty') return '서버 연결 대기'
    return ''
  }, [psStatus])



  const { displayRecommendations, filteredRecommendations, visibleRecommendations, hiddenRecommendations, signalTape, normalizedFilter } = useRecommendations({
    streamRecommendations,
    marketFilter,
    autoPickTopN: automation.autoPickTopN,
  })

  const displaySummary = ps ?? emptySummary
  const displayPerformance = perf ?? emptyPerformance

  const maxPositionsToShow = 6
  const filteredPositions = useMemo(() => {
    if (!normalizedFilter) {
      return displaySummary.positions
    }
    return displaySummary.positions.filter((pos) => pos.market.toUpperCase().includes(normalizedFilter))
  }, [displaySummary.positions, normalizedFilter])
  const visiblePositions = filteredPositions.slice(0, maxPositionsToShow)
  const hiddenPositions = Math.max(0, filteredPositions.length - visiblePositions.length)



  const searchOptions = useMemo(() => {
    const set = new Set<string>()
    displayRecommendations.forEach((item) => {
      if (item.market && item.market !== '--') {
        set.add(item.market)
      }
    })
    displaySummary.positions.forEach((pos) => set.add(pos.market))
    return Array.from(set).sort()
  }, [displayRecommendations, displaySummary.positions])

  const { chartData, preferredChartMarket, chartCandles, candleChart } = useChartData({
    chartMode,
    focusedMarket,
    displaySummary,
    displayPerformance,
    displayRecommendations,
  })
  const selectionLabel = useMemo(() => (automation.selectionMode === 'MANUAL' ? '수동' : '자동'), [automation.selectionMode])
  const operationLabel = useMemo(() => (automation.operationMode === 'ATTACK' ? '공격' : '안정'), [automation.operationMode])
  const positionCount = displaySummary.positions.length

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


  const dashboardProps = {
    lastUpdated: streamLastUpdated,
    recommendationNote,
    autoPickTopN: automation.autoPickTopN,
    streamStatus: streamState,
    streamLabel,
    filteredRecommendations,
    visibleRecommendations,
    hiddenRecommendations,
    formatMoney,
    formatPct,
    summaryNote,
    displaySummary,
    chartMode,
    setChartMode,
    preferredChartMarket,
    candleChart,
    chartData,
    chartCandles,
    focusedMarket,
    setFocusedMarket,
    operationLabel,
    selectionLabel,
    strategyMode: automation.strategyMode,
    riskBadge,
    maxPositions: automation.maxPositions,
    dailyDd: automation.dailyDd,
    weeklyDd: automation.weeklyDd,
    loadedDefaults,
    manualMarkets: automation.manualMarkets,
    signalTape,
    isAuthed, // pass authentication state so dashboard can show CTA
    setLoginOpen, // allow dashboard to open login modal
  }

  const automationProps = useAutomationProps({ automation, loading, riskBadge, operationLabelFor, loadedDefaults })

  return (
    <div className="app-shell">
      <Header
        marketFilter={marketFilter}
        setMarketFilter={setMarketFilter}
        searchOptions={searchOptions}
        setFocusedMarket={setFocusedMarket}
        setChartMode={setChartMode}
        streamStatus={streamState}
        streamLabel={streamLabel}
        isAuthed={isAuthed}
        setAuthError={setAuthError}
        setRegisterOpen={setRegisterOpen}
        setLoginOpen={setLoginOpen}
        handleLogout={handleLogout}
      />

        <main className={`terminal-grid view-${activeViewFromPath}`}>
          <AppRoutes
            dashboardProps={dashboardProps}
            holdingsProps={{
              summaryNote,
              visiblePositions,
              filteredPositions,
              hiddenPositions,
              positionCount,
              formatMoney,
              formatPct,
            }}
            automationProps={automationProps}
          />
        </main>

      <AuthModals
        loginOpen={loginOpen}
        setLoginOpen={setLoginOpen}
        registerOpen={registerOpen}
        setRegisterOpen={setRegisterOpen}
        email={email}
        setEmail={setEmail}
        password={password}
        setPassword={setPassword}
        authError={authError}
        loading={loading}
        handleLogin={handleLogin}
        handleRegister={() => handleRegister(derivedTenantName)}
      />
    </div>
  )
}

export default App
