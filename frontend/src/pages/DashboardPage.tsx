import React, { useMemo, useState } from 'react'
import useSparklines from '../hooks/useSparklines'
import RecommendationCard from '../components/RecommendationCard'
import CandleChart from '../components/CandleChart'
import type { Recommendation, PaperSummary, MarketCandlePoint, BotDefaults } from '../lib/types'

export type SignalItem = { market: string; price: number; score: number; tag: { label: string; tone: string } }

export type DashboardProps = {
  lastUpdated?: string
  recommendationNote?: string
  autoPickTopN: number
  streamStatus: 'idle' | 'live' | 'reconnecting' | 'error' | 'connecting'
  streamLabel: string
  filteredRecommendations: Recommendation[]
  visibleRecommendations: Recommendation[]
  hiddenRecommendations: number
  formatMoney: (n: number) => string
  summaryNote?: string
  displaySummary: PaperSummary
  chartMode: 'equity' | 'candles'
  setChartMode: (m: 'equity' | 'candles') => void
  preferredChartMarket: string
  candleChart: { candles: Array<{ x: number; highY: number; lowY: number; bodyTop: number; bodyHeight: number; up: boolean }>; min: number; max: number; bodyWidth: number } | null
  chartData: { min: number; max: number; last: { x: number; y: number; value: number }; line: string; area: string } | null
  chartCandles: MarketCandlePoint[]
  setFocusedMarket: (m: string) => void
  operationLabel: string
  selectionLabel: string
  strategyMode: string
  riskBadge: string
  maxPositions: number
  dailyDd: number
  weeklyDd: number
  loadedDefaults?: BotDefaults | null
  manualMarkets: string
  signalTape: SignalItem[]
  isAuthed: boolean
  setLoginOpen: (b: boolean) => void
}

const DashboardPage: React.FC<DashboardProps> = (props) => {
  const {
    lastUpdated,
    recommendationNote,
    autoPickTopN,
    streamStatus,
    streamLabel,
    filteredRecommendations,
    visibleRecommendations,
    hiddenRecommendations,
    formatMoney,
    summaryNote,
    displaySummary,
    chartMode,
    setChartMode,
    preferredChartMarket,
    candleChart,
    chartData,
    chartCandles,
    setFocusedMarket,
    operationLabel,
    selectionLabel,
    strategyMode,
    riskBadge,
    maxPositions,
    dailyDd,
    weeklyDd,
    loadedDefaults,
    manualMarkets,
    signalTape,
    isAuthed,
    setLoginOpen,
  } = props

  // local debug state to fetch summary on demand
  const [fetchedSummary, setFetchedSummary] = useState<any | null>(null)
  const fetchSummary = async () => {
    try {
      const tok = localStorage.getItem('token')
      const res = await fetch('/api/paper/summary', { headers: { Authorization: 'Bearer ' + tok } })
      if (!res.ok) {
        setFetchedSummary({ error: `status ${res.status}` })
        return
      }
      const body = await res.json()
      setFetchedSummary(body)
    } catch (err) {
      setFetchedSummary({ error: err?.message || String(err) })
    }
  }

  // Use fetched summary immediately when available (only when authenticated)
  const effectiveSummary: any = props.isAuthed ? (fetchedSummary ?? displaySummary) : displaySummary

  // compute sparklines for visible markets (handled in this page)
  const sparklineMarkets = useMemo(() => {
    const unique = new Set<string>()
    visibleRecommendations.forEach((coin) => {
      if (coin.market && coin.market !== '--') unique.add(coin.market)
    })
    return Array.from(unique)
  }, [visibleRecommendations])

  const sparklines = useSparklines(sparklineMarkets)

  return (
    <>
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
              {visibleRecommendations.map((coin: Recommendation, index: number) => (
                <RecommendationCard key={`${coin.market}-${index}`} coin={coin} index={index} sparklines={sparklines} formatMoney={formatMoney} />
              ))}
              {hiddenRecommendations > 0 && <p className="more-note">외 {hiddenRecommendations}개 표시</p>}
            </>
          )}
        </div>
      </section>

      <section className="panel account-panel">
        <div className="panel-header">
          <h2>Account Snapshot</h2>
          <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center' }}>
            <span className="pill">Equity \ {formatMoney(effectiveSummary.equity)}</span>
            <button className="ghost small" onClick={fetchSummary} type="button">잔고 새로고침</button>
          </div>
        </div>
        <p className="panel-subtitle">{summaryNote || `최근 업데이트 ${lastUpdated || '대기중'}`}</p>

        <div className="account-amounts">
          <div className="amount equity">Equity <strong>\ {formatMoney(effectiveSummary.equity)}</strong></div>
          <div className="amount cash">Cash <strong>\ {formatMoney(effectiveSummary.cashBalance)}</strong></div>
        </div> 

        {/* If not authenticated, show a clear CTA to log in so the user can see their balance */}
        {!props.isAuthed ? (
          <div style={{ marginTop: '0.8rem' }}>
            <p className="empty">잔고를 확인하려면 로그인 해주세요.</p>
            <div className="button-row" style={{ marginTop: '0.6rem' }}>
              <button className="primary" onClick={() => props.setLoginOpen(true)}>로그인</button>
            </div>
          </div>
        ) : (
          <>
            <div className="stats-grid">
              <div>
                <p>Cash balance</p>
                <strong>\ {formatMoney(effectiveSummary.cashBalance)}</strong>
              </div>
              <div>
                <p>Unrealized PnL</p>
                <strong className={effectiveSummary.unrealizedPnl >= 0 ? 'positive' : 'negative'}>
                  \ {formatMoney(effectiveSummary.unrealizedPnl)}
                </strong>
              </div>
              <div>
                <p>Realized PnL</p>
                <strong className={effectiveSummary.realizedPnl >= 0 ? 'positive' : 'negative'}>
                  \ {formatMoney(effectiveSummary.realizedPnl)}
                </strong>
              </div>
              <div>
                <p>Open positions</p>
                <strong>{(effectiveSummary.positions || []).length}개</strong>
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
          </>
        )}
      </section>

      <CandleChart
        chartMode={chartMode}
        setChartMode={setChartMode}
        preferredChartMarket={preferredChartMarket}
        chartData={chartData}
        candleChart={candleChart}
        chartCandles={chartCandles}
        formatMoney={formatMoney}
        setFocusedMarket={setFocusedMarket}
      />

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
            <strong>{loadedDefaults?.engineEnabled ? 'ON' : 'OFF'}</strong>
          </div>
          <div>
            <span>Rebalance</span>
            <strong>
              {loadedDefaults?.engineIntervalMs ? `${Math.round(loadedDefaults.engineIntervalMs / 1000)}s` : '-'}
            </strong>
          </div>
        </div>
        {selectionLabel === '수동' && manualMarkets && (
          <p className="hint">Manual: {manualMarkets}</p>
        )}
        <div className="signal-list compact">
          {signalTape.map((item: SignalItem, index: number) => (
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
    </>
  )
}

export default DashboardPage
