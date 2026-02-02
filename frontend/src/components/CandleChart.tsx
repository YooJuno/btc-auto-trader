import React from 'react'

const CandleChart: React.FC<any> = ({ chartMode, setChartMode, preferredChartMarket, chartData, candleChart, chartCandles, formatMoney, setFocusedMarket }) => {
  return (
    <section className="panel chart-panel">
      <div className="panel-header">
        <div>
          <h2>Chart</h2>
          <p className="panel-subtitle">{chartMode === 'candles' ? `Market ${preferredChartMarket || '대기중'}` : 'Equity trend'}</p>
        </div>
        <div className="chip-row">
          {setFocusedMarket && (
            <div>
              {/* placeholder for focus controls if needed */}
            </div>
          )}
          <button className={`pill button-pill ${chartMode === 'equity' ? 'active' : ''}`} type="button" onClick={() => setChartMode('equity')}>
            Line
          </button>
          <button className={`pill button-pill ${chartMode === 'candles' ? 'active' : ''}`} type="button" onClick={() => setChartMode('candles')}>
            Candles
          </button>
        </div>
      </div>
      <div className="performance-chart">
        {chartMode === 'candles' ? (
          candleChart ? (
            <div className="performance-visual large">
              <div className="chart-wrapper tall">
                <svg className="chart-svg" viewBox="0 0 100 60" preserveAspectRatio="xMidYMid meet">
                  <defs>
                    <pattern id="candleGrid" width="10" height="10" patternUnits="userSpaceOnUse">
                      <path d="M 10 0 L 0 0 0 10" fill="none" stroke="rgba(255, 255, 255, 0.08)" strokeWidth="0.4" />
                    </pattern>
                  </defs>
                  <rect width="100" height="60" fill="url(#candleGrid)" />
                  {candleChart.candles.map((candle: any, index: number) => (
                    <g key={index}>
                      <line x1={candle.x} y1={candle.highY} x2={candle.x} y2={candle.lowY} stroke={candle.up ? 'rgba(96, 230, 134, 0.9)' : 'rgba(255, 111, 111, 0.9)'} strokeWidth="1" />
                      <rect x={candle.x - candleChart.bodyWidth / 2} y={candle.bodyTop} width={candleChart.bodyWidth} height={candle.bodyHeight} fill={candle.up ? 'rgba(96, 230, 134, 0.5)' : 'rgba(255, 111, 111, 0.5)'} stroke={candle.up ? 'rgba(96, 230, 134, 0.9)' : 'rgba(255, 111, 111, 0.9)'} strokeWidth="0.6" />
                    </g>
                  ))}
                </svg>
              </div>
              <div className="chart-legend">
                <div>
                  <span>High</span>
                  <strong>\ {formatMoney(candleChart.max)}</strong>
                </div>
                <div>
                  <span>Low</span>
                  <strong>\ {formatMoney(candleChart.min)}</strong>
                </div>
                <div>
                  <span>Last</span>
                  <strong>\ {formatMoney(chartCandles[chartCandles.length - 1].close)}</strong>
                </div>
              </div>
            </div>
          ) : (
            <p className="empty">캔들 데이터 수신 대기중</p>
          )
        ) : chartData ? (
          <>
            <div className="performance-visual large">
              <div className="chart-wrapper tall">
                <svg className="chart-svg" viewBox="0 0 100 60" preserveAspectRatio="xMidYMid meet">
                  <defs>
                    <pattern id="chartGrid" width="10" height="10" patternUnits="userSpaceOnUse">
                      <path d="M 10 0 L 0 0 0 10" fill="none" stroke="rgba(255, 255, 255, 0.08)" strokeWidth="0.4" />
                    </pattern>
                    <linearGradient id="chartLine" x1="0" y1="0" x2="1" y2="0">
                      <stop offset="0%" stopColor="#ffbf6b" stopOpacity="0.9" />
                      <stop offset="100%" stopColor="#57d9c6" stopOpacity="0.9" />
                    </linearGradient>
                    <linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="rgba(255, 191, 107, 0.35)" />
                      <stop offset="100%" stopColor="rgba(10, 15, 20, 0.05)" />
                    </linearGradient>
                  </defs>
                  <rect width="100" height="60" fill="url(#chartGrid)" />
                  <path d={chartData.area} fill="url(#chartFill)" />
                  <path d={chartData.line} fill="none" stroke="url(#chartLine)" strokeWidth="1.8" />
                  <circle cx={chartData.last.x} cy={chartData.last.y} r="3" fill="rgba(255, 191, 107, 0.25)" />
                  <circle cx={chartData.last.x} cy={chartData.last.y} r="1.4" fill="#ffbf6b" />
                </svg>
              </div>
              <div className="chart-legend">
                <div>
                  <span>최근</span>
                  <strong>\ {formatMoney(chartData.last.value)}</strong>
                </div>
                <div>
                  <span>최저</span>
                  <strong>\ {formatMoney(chartData.min)}</strong>
                </div>
                <div>
                  <span>최고</span>
                  <strong>\ {formatMoney(chartData.max)}</strong>
                </div>
              </div>
            </div>
          </>
        ) : (
          <p className="empty">성과 데이터 수신 대기중</p>
        )}
      </div>
    </section>
  )
}

export default CandleChart
