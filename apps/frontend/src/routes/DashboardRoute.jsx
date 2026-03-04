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
    formatOrderStatus,
    formatFixed,
    truncateText,
    pnlClass,
  } = formatters

  return (
    <>
      <DashboardSummaryCards
        cash={cash}
        totals={totals}
        formatKRW={formatKRW}
        formatPercent={formatPercent}
        pnlClass={pnlClass}
      />

      <section className="workspace-grid workspace-grid--status">
        <div className="workspace-main">
          <PositionsCard
            authRequired={authRequired}
            loading={loading}
            positions={positions}
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
            formatOrderStatus={formatOrderStatus}
            formatCoin={formatCoin}
            formatKRW={formatKRW}
            formatFixed={formatFixed}
            truncateText={truncateText}
          />
        </div>
      </section>
    </>
  )
}

export default memo(DashboardRoute)
