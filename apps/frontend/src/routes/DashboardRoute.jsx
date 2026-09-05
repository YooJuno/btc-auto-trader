import { lazy, memo, Suspense } from 'react'
import DashboardSummaryCards from '../components/dashboard/DashboardSummaryCards.jsx'
import DecisionFeedCard from '../components/dashboard/DecisionFeedCard.jsx'
import OrderHistoryCard from '../components/dashboard/OrderHistoryCard.jsx'
import PositionsCard from '../components/dashboard/PositionsCard.jsx'

// lightweight-charts is ~177kB. The positions and decision feed are the load-bearing views, so let them
// paint first rather than blocking the whole dashboard on the charting library.
const PriceChartCard = lazy(() => import('../components/dashboard/PriceChartCard.jsx'))

/*
 * Two columns, not one. Every route used to apply workspace-grid--settings, which forces a single 1fr
 * column and display:none's .workspace-main — so the two-column grid was dead CSS and a 1760px canvas
 * showed one table per row.
 *
 * Left: what the engine is doing (positions, fills). Right: why it is doing it (decision feed).
 */
function DashboardRoute({
  authRequired,
  cash,
  totals,
  loading,
  positions,
  summaryError,
  manualTradeNotice,
  mergedOrderHistory,
  feedError,
  decisionFeed,
  chartMarket,
  chartAvgBuyPrice,
  onOpenManualTrade,
  formatters,
}) {
  const {
    formatKRW,
    formatCoin,
    formatPercent,
    formatDateTime,
    formatTime,
    pnlClass,
  } = formatters

  return (
    <>
      <DashboardSummaryCards
        cash={cash}
        totals={totals}
        summaryError={summaryError}
        formatKRW={formatKRW}
        formatPercent={formatPercent}
        pnlClass={pnlClass}
      />

      <section className="workspace-grid">
        <div className="workspace-main">
          <Suspense
            fallback={
              <section className="table-card chart-panel">
                <div className="empty-state">차트를 불러오는 중…</div>
              </section>
            }
          >
            <PriceChartCard
              market={chartMarket}
              orders={mergedOrderHistory}
              avgBuyPrice={chartAvgBuyPrice}
            />
          </Suspense>

          <PositionsCard
            authRequired={authRequired}
            loading={loading}
            positions={positions}
            summaryError={summaryError}
            manualTradeNotice={manualTradeNotice}
            onOpenManualTrade={onOpenManualTrade}
            formatKRW={formatKRW}
            formatCoin={formatCoin}
            formatPercent={formatPercent}
            pnlClass={pnlClass}
          />

          <OrderHistoryCard
            feedError={feedError}
            mergedOrderHistory={mergedOrderHistory}
            formatDateTime={formatDateTime}
            formatKRW={formatKRW}
            pnlClass={pnlClass}
          />
        </div>

        <div className="workspace-side">
          <DecisionFeedCard
            decisions={decisionFeed}
            decisionError={null}
            formatTime={formatTime}
          />
        </div>
      </section>
    </>
  )
}

export default memo(DashboardRoute)
