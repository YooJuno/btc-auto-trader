import { memo } from 'react'
import { formatOrderStatus, isFilledOrder } from '../../utils/tradingUi.js'

const OrderHistoryRow = memo(function OrderHistoryRow({
  order,
  formatDateTime,
  formatKRW,
  pnlClass,
}) {
  const decision = order.decision
  const filled = isFilledOrder(order)
  const status = formatOrderStatus(order?.requestStatus, order?.state)
  const side = String(order.side ?? '').toUpperCase()

  return (
    <tr>
      <td className="mono small">{formatDateTime(order.requestedAt)}</td>
      <td className="mono">{order.market}</td>
      <td>
        {/* Side is a category, not a P&L value. Colouring it with the same classes the P&L column uses
            made a green cell mean "buy" in one column and "gain" in the next. */}
        <span className={`side-tag ${side === 'BUY' ? 'side-tag--buy' : 'side-tag--sell'}`}>
          {side === 'BUY' ? '매수' : '매도'}
        </span>
      </td>
      <td className="small" title={order.errorMessage ?? undefined}>
        {status}
      </td>
      <td className="num">{order.funds ? formatKRW(order.funds) : '-'}</td>
      <td className="mono small" title={decision?.reason ?? undefined}>
        {decision?.reason ?? '-'}
      </td>
      {/* A rejected or cancelled order has no realised P&L. The previous version ran every row through a
          client-side FIFO regardless of status, so failed orders produced invented profit numbers. */}
      <td className={`num ${filled ? pnlClass(order.tradeProfit) : ''}`}>
        {filled && order.tradeProfit !== null && order.tradeProfit !== undefined
          ? formatKRW(order.tradeProfit)
          : '-'}
      </td>
    </tr>
  )
})

function OrderHistoryCard({
  feedError,
  mergedOrderHistory,
  formatDateTime,
  formatKRW,
  pnlClass,
}) {
  return (
    <section className="table-card order-card">
      <div className="table-header">
        <h2>주문 내역</h2>
      </div>
      {feedError && <p className="status-error">{feedError}</p>}
      {mergedOrderHistory.length === 0 ? (
        <div className="empty-state">주문 내역이 없습니다.</div>
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>시각</th>
                <th>마켓</th>
                <th>구분</th>
                <th>상태</th>
                <th className="num">금액</th>
                <th>사유</th>
                <th className="num">실현손익</th>
              </tr>
            </thead>
            <tbody>
              {mergedOrderHistory.map((order) => (
                <OrderHistoryRow
                  key={order.id ?? `${order.orderId ?? order.market ?? '-'}-${order.requestedAt ?? ''}`}
                  order={order}
                  formatDateTime={formatDateTime}
                  formatKRW={formatKRW}
                  pnlClass={pnlClass}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

export default memo(OrderHistoryCard)
