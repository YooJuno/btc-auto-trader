import { memo } from 'react'

const OrderHistoryRow = memo(function OrderHistoryRow({
  order,
  formatDateTime,
  formatOrderStatus,
  formatCoin,
  formatKRW,
  formatFixed,
  truncateText,
}) {
  const decision = order.decision
  return (
    <tr>
      <td className="mono small">{formatDateTime(order.requestedAt)}</td>
      <td>{order.market}</td>
      <td className={order.side === 'BUY' ? 'positive' : 'negative'}>{order.side}</td>
      <td>
        <span className="mono">{formatOrderStatus(order.requestStatus, order.state)}</span>
      </td>
      <td className="mono">{formatCoin(order.volume)}</td>
      <td className="mono">{formatKRW(order.funds)}</td>
      <td className="mono small">{decision?.reason ?? '-'}</td>
      <td className="mono">{formatFixed(decision?.rsi, 2)}</td>
      <td className="mono">{formatFixed(decision?.macdHistogram, 4)}</td>
      <td className="mono">{formatFixed(decision?.maLongSlopePct, 3)}</td>
      <td className="mono">{formatKRW(decision?.price)}</td>
      <td>{decision?.profile ?? '-'}</td>
      <td className="mono small">{truncateText(order.errorMessage, 36)}</td>
    </tr>
  )
})

function OrderHistoryCard({
  feedError,
  mergedOrderHistory,
  formatDateTime,
  formatOrderStatus,
  formatCoin,
  formatKRW,
  formatFixed,
  truncateText,
}) {
  return (
    <article className="table-card card--elevated order-card">
      <div className="table-header">
        <div>
          <h2>최근 주문 로그</h2>
          <p className="sub">주문 상태 + 매매 사유/지표 스냅샷 통합</p>
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
                <th>상태</th>
                <th>주문량</th>
                <th>주문금액</th>
                <th>사유</th>
                <th>RSI</th>
                <th>MACD</th>
                <th>MA Slope%</th>
                <th>가격</th>
                <th>프로필</th>
                <th>오류</th>
              </tr>
            </thead>
            <tbody>
              {mergedOrderHistory.map((order) => (
                <OrderHistoryRow
                  key={order.id ?? `${order.orderId ?? order.market ?? '-'}-${order.requestedAt ?? ''}`}
                  order={order}
                  formatDateTime={formatDateTime}
                  formatOrderStatus={formatOrderStatus}
                  formatCoin={formatCoin}
                  formatKRW={formatKRW}
                  formatFixed={formatFixed}
                  truncateText={truncateText}
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
