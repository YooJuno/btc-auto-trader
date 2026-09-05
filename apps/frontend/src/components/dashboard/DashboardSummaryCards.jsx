import { memo } from 'react'

/*
 * A one-line stat strip, not four 108px cards holding one number each. Account equity is glanceable
 * reference data, so it gets a dense header band the way Upbit Pro / Binance present it — the reclaimed
 * vertical space goes to the tables and the chart.
 */
function DashboardSummaryCards({ cash, totals, summaryError, formatKRW, formatPercent, pnlClass }) {
  return (
    <>
      {summaryError && <p className="status-error">{summaryError}</p>}
      <section className="stat-strip" aria-label="계좌 요약">
        <div className="stat">
          <span className="stat__label">총 자산</span>
          <span className="stat__value">
            {formatKRW(totals?.totalAsset)}
            <span className="unit">KRW</span>
          </span>
          <span className="stat__sub">현금 + 코인 평가</span>
        </div>
        <div className="stat">
          <span className="stat__label">보유 KRW</span>
          <span className="stat__value">{formatKRW(cash?.total)}</span>
          <span className="stat__sub">
            가용 {formatKRW(cash?.balance)} · 예약 {formatKRW(cash?.locked)}
          </span>
        </div>
        <div className="stat">
          <span className="stat__label">코인 평가</span>
          <span className="stat__value">{formatKRW(totals?.positionValue)}</span>
          <span className="stat__sub">매입 {formatKRW(totals?.positionCost)}</span>
        </div>
        <div className="stat">
          <span className="stat__label">평가 손익</span>
          <span className={`stat__value ${pnlClass(totals?.positionPnl)}`}>
            {formatKRW(totals?.positionPnl)}
          </span>
          <span className={`stat__sub ${pnlClass(totals?.positionPnl)}`}>
            {formatPercent(totals?.positionPnlRate)}
          </span>
        </div>
      </section>
    </>
  )
}

export default memo(DashboardSummaryCards)
