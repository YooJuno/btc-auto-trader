import { lazy, memo, Suspense } from 'react'
import DashboardSummaryCards from '../components/dashboard/DashboardSummaryCards.jsx'
import OrderHistoryCard from '../components/dashboard/OrderHistoryCard.jsx'
import PositionsCard from '../components/dashboard/PositionsCard.jsx'

// lightweight-charts is ~177kB. Positions and fills are the load-bearing views, so let them paint
// first rather than blocking the whole dashboard on the charting library.
const PriceChartCard = lazy(() => import('../components/dashboard/PriceChartCard.jsx'))

/*
 * The main column only.
 *
 * The performance summary and decision feed moved to the shell's right rail: they are context about the
 * engine rather than part of this page, and keeping them here meant every route that was not the
 * dashboard had to fake a second column or hide one.
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

      <Suspense
        fallback={
          <section className="panel chart-panel">
            <div className="empty-state">차트를 불러오는 중…</div>
          </section>
        }
      >
        <PriceChartCard
          market={chartMarket}
          orders={mergedOrderHistory}
          avgBuyPrice={chartAvgBuyPrice}
          onOpenManualTrade={onOpenManualTrade}
          tradeDisabled={authRequired}
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
    </>
  )
}

export default memo(DashboardRoute)
