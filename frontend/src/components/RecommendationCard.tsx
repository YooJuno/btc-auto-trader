import React from 'react'
import { buildSparkline } from '../lib/utils'
import type { Recommendation, MarketCandlePoint } from '../lib/types'

type Props = {
  coin: Recommendation
  index: number
  sparklines: Record<string, MarketCandlePoint[]>
  formatMoney: (n: number) => string
}

const RecommendationCard: React.FC<Props> = ({ coin, index, sparklines, formatMoney }) => {
  const sparkPoints = sparklines[coin.market] ?? []
  const sparkPath = sparkPoints.length > 1 ? buildSparkline(sparkPoints) : ''
  const sparkTone = sparkPoints.length > 1 && sparkPoints[sparkPoints.length - 1].close >= sparkPoints[0].close ? 'up' : 'down'

  return (
    <div key={`${coin.market}-${index}`} className="recommendation-card">
      <div className="recommendation-main">
        <div className="rank">#{index + 1}</div>
        <div>
          <h3>{coin.market}</h3>
          <span>Score {(coin.score * 100).toFixed(1)}</span>
        </div>
      </div>
      <div className="recommendation-meta">
        <span>\ {formatMoney(coin.lastPrice)}</span>
        <span>Vol {coin.volatilityPct.toFixed(2)}%</span>
        <span>Trend {coin.trendStrengthPct.toFixed(2)}%</span>
        <span>24h {formatMoney(coin.volume24h)}</span>
        <div className={`sparkline ${sparkPath ? sparkTone : 'idle'}`}>
          {sparkPath ? (
            <svg viewBox="0 0 100 32" preserveAspectRatio="xMidYMid meet">
              <path d={sparkPath} fill="none" strokeWidth="2" />
            </svg>
          ) : (
            <span>차트 대기</span>
          )}
        </div>
      </div>
    </div>
  )
}

export default RecommendationCard
