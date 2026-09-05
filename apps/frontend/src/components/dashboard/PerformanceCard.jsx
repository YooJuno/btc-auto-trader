import { memo, useMemo } from 'react'

/*
 * Realised performance over the fetched order window.
 *
 * The console showed balances and open P&L but nothing about whether the strategy actually works —
 * no win rate, no profit factor, no realised total. Those are the numbers that answer "should this
 * engine be running at all", and every input is already present client-side.
 *
 * Scope is stated explicitly rather than implied: this covers the orders currently loaded, and the FIFO
 * starts cold, so the earliest sells in the window may have no matching buy and are excluded. It is a
 * recent-activity summary, not lifetime accounting — saying so is the difference between a useful
 * number and a misleading one.
 */
function PerformanceCard({ orders, formatKRW }) {
  const stats = useMemo(() => {
    const closed = (Array.isArray(orders) ? orders : []).filter(
      (order) =>
        String(order?.side ?? '').toUpperCase() === 'SELL' &&
        order?.tradeProfit !== null &&
        order?.tradeProfit !== undefined &&
        Number.isFinite(Number(order.tradeProfit))
    )

    if (closed.length === 0) {
      return null
    }

    let grossProfit = 0
    let grossLoss = 0
    let wins = 0

    closed.forEach((order) => {
      const profit = Number(order.tradeProfit)
      if (profit >= 0) {
        grossProfit += profit
        wins += 1
      } else {
        grossLoss += Math.abs(profit)
      }
    })

    const losses = closed.length - wins
    return {
      count: closed.length,
      net: grossProfit - grossLoss,
      winRate: (wins / closed.length) * 100,
      wins,
      losses,
      avgWin: wins > 0 ? grossProfit / wins : null,
      avgLoss: losses > 0 ? grossLoss / losses : null,
      // Undefined rather than Infinity when nothing has lost yet: a "profit factor" with no denominator
      // is not a large number, it is an unknown one.
      profitFactor: grossLoss > 0 ? grossProfit / grossLoss : null,
    }
  }, [orders])

  return (
    <section className="table-card">
      <div className="table-header">
        <h2>실현 성과</h2>
        {stats && <span className="pill">{stats.count}건 청산</span>}
      </div>

      {!stats ? (
        <div className="empty-state">청산된 거래가 아직 없습니다.</div>
      ) : (
        <>
          <div className="stat-strip">
            <div className="stat">
              <span className="stat__label">실현 손익</span>
              <span className={`stat__value ${stats.net > 0 ? 'positive' : stats.net < 0 ? 'negative' : ''}`}>
                {formatKRW(stats.net)}
              </span>
              <span className="stat__sub">최근 주문 기준</span>
            </div>
            <div className="stat">
              <span className="stat__label">승률</span>
              <span className="stat__value">{stats.winRate.toFixed(1)}%</span>
              <span className="stat__sub">
                {stats.wins}승 {stats.losses}패
              </span>
            </div>
            <div className="stat">
              <span className="stat__label">손익비</span>
              <span className="stat__value">
                {stats.profitFactor === null ? '—' : stats.profitFactor.toFixed(2)}
              </span>
              <span className="stat__sub">총이익 / 총손실</span>
            </div>
            <div className="stat">
              <span className="stat__label">평균 손익</span>
              <span className="stat__value positive">{stats.avgWin === null ? '—' : formatKRW(stats.avgWin)}</span>
              <span className="stat__sub negative">
                {stats.avgLoss === null ? '—' : `-${formatKRW(stats.avgLoss)}`}
              </span>
            </div>
          </div>
          <p className="sub compact">
            불러온 주문 범위 내 체결만 집계하며, 매수 기록이 범위 밖인 매도는 제외됩니다. 전체 기간 누적이 아닙니다.
          </p>
        </>
      )}
    </section>
  )
}

export default memo(PerformanceCard)
