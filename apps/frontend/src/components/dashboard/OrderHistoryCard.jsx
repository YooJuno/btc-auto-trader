import { memo } from 'react'

const OrderHistoryRow = memo(function OrderHistoryRow({
  order,
  formatDateTime,
  formatKRW,
  pnlClass,
}) {
  const decision = order.decision
  const profitClass = pnlClass(order.tradeProfit)

  return (
    <tr>
      <td className="mono small">{formatDateTime(order.requestedAt)}</td>
      <td>{order.market}</td>
      <td className={order.side === 'BUY' ? 'positive' : 'negative'}>{order.side}</td>
      <td className="mono small">{decision?.reason ?? '-'}</td>
      <td className={`mono ${profitClass}`}>{formatKRW(order.tradeProfit)}</td>
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
    <article className="table-card card--elevated order-card">
      <div className="table-header">
        <div>
          <h2>최근 주문 로그</h2>
          <p className="sub">매매 사유와 거래별 수익 추정치를 간단히 표시합니다.</p>
        </div>
      </div>
      {feedError && <p className="status-error">{feedError}</p>}
      {mergedOrderHistory.length === 0 ? (
        <div className="empty-state">주문 로그가 없습니다.</div>
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>시간</th>
                <th>마켓</th>
                <th>사이드</th>
                <th>사유</th>
                <th>수익</th>
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
    </article>
  )
}

export default memo(OrderHistoryCard)
