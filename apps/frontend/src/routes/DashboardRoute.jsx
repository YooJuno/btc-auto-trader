import { memo } from 'react'
import DashboardSummaryCards from '../components/dashboard/DashboardSummaryCards.jsx'
import OrderHistoryCard from '../components/dashboard/OrderHistoryCard.jsx'
import PositionsCard from '../components/dashboard/PositionsCard.jsx'

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

      <section className="workspace-grid workspace-grid--settings">
        <div className="workspace-side">
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
      </section>
    </>
  )
}

export default memo(DashboardRoute)
