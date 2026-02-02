import React from 'react'
import type { PaperPosition } from '../lib/types'

export type HoldingsProps = {
  summaryNote?: string
  visiblePositions: PaperPosition[]
  filteredPositions: PaperPosition[]
  hiddenPositions: number
  positionCount: number
  formatMoney: (n: number) => string
  formatPct: (n: number) => string
}

const HoldingsPage: React.FC<HoldingsProps> = (props) => {
  const { summaryNote, visiblePositions, filteredPositions, hiddenPositions, positionCount, formatMoney, formatPct } = props
  return (
    <section className="panel holdings-panel">
      <div className="panel-header">
        <h2>Holdings</h2>
        <span className="pill">Positions {positionCount}</span>
      </div>
      {summaryNote && <p className="panel-subtitle">{summaryNote}</p>}
      <div className="positions-table">
        <div className="positions-row header">
          <span>Market</span>
          <span>Qty</span>
          <span>Entry</span>
          <span>Buy</span>
          <span>Last</span>
          <span>PnL</span>
          <span>PnL ₩</span>
        </div>
        {visiblePositions.map((pos: PaperPosition) => (
          <div key={pos.market} className="positions-row">
            <span>{pos.market}</span>
            <span>{pos.quantity.toFixed(4)}</span>
            <span>\ {formatMoney(pos.entryPrice)}</span>
            <span>\ {formatMoney(pos.entryPrice * pos.quantity)}</span>
            <span>\ {formatMoney(pos.lastPrice)}</span>
            <span className={pos.unrealizedPnl >= 0 ? 'positive' : 'negative'}>{formatPct(pos.unrealizedPnlPct)}</span>
            <span className={pos.unrealizedPnl >= 0 ? 'positive' : 'negative'}>\ {formatMoney(pos.unrealizedPnl)}</span>
          </div>
        ))}
        {filteredPositions.length === 0 && <p className="empty">보유 포지션 없음</p>}
        {hiddenPositions > 0 && <p className="more-note">외 {hiddenPositions}개 보유중</p>}
      </div>
    </section>
  )
}

export default HoldingsPage
