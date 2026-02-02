import { useEffect, useMemo, useState } from 'react'
import { apiFetch } from '../lib/api'
import type { MarketCandlePoint, PaperPerformance, Recommendation, PaperSummary } from '../lib/types'

type Params = {
  chartMode: 'equity' | 'candles'
  focusedMarket: string
  displaySummary: PaperSummary
  displayPerformance: PaperPerformance
  displayRecommendations: Recommendation[]
}

export default function useChartData({ chartMode, focusedMarket, displaySummary, displayPerformance, displayRecommendations }: Params) {
  const [chartCandles, setChartCandles] = useState<MarketCandlePoint[]>([])

  const chartSeries = useMemo(() => {
    if (displayPerformance.daily.length === 0) return []
    return displayPerformance.daily.slice(-7)
  }, [displayPerformance.daily])

  const chartData = useMemo(() => {
    if (chartSeries.length === 0) return null
    const values = chartSeries.map((point) => point.equity)
    const min = Math.min(...values)
    const max = Math.max(...values)
    const range = max - min || 1
    const points = chartSeries.map((point, index) => {
      const x = chartSeries.length === 1 ? 0 : (index / (chartSeries.length - 1)) * 100
      const y = 60 - ((point.equity - min) / range) * 60
      return { x, y, value: point.equity }
    })
    const line = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`).join(' ')
    const area = `${line} L ${points[points.length - 1].x.toFixed(2)} 60 L 0 60 Z`
    return { min, max, last: points[points.length - 1], line, area }
  }, [chartSeries])

  const preferredChartMarket = useMemo(() => {
    if (focusedMarket) return focusedMarket
    if (displaySummary.positions.length > 0) return displaySummary.positions[0].market
    const reco = displayRecommendations.find((item) => item.market && item.market !== '--')
    return reco?.market ?? ''
  }, [focusedMarket, displaySummary.positions, displayRecommendations])

  useEffect(() => {
    if (chartMode !== 'candles') return
    if (!preferredChartMarket) {
      setChartCandles([])
      return
    }
    let cancelled = false
    apiFetch<MarketCandlePoint[]>(`/api/market/candles?market=${encodeURIComponent(preferredChartMarket)}&limit=40`)
      .then((data) => {
        if (!cancelled) setChartCandles(data)
      })
      .catch(() => {
        if (!cancelled) setChartCandles([])
      })
    return () => {
      cancelled = true
    }
  }, [chartMode, preferredChartMarket])

  const candleChart = useMemo(() => {
    if (chartMode !== 'candles' || chartCandles.length === 0) return null
    const highs = chartCandles.map((candle) => candle.high)
    const lows = chartCandles.map((candle) => candle.low)
    const min = Math.min(...lows)
    const max = Math.max(...highs)
    const range = max - min || 1
    const count = chartCandles.length
    const step = count <= 1 ? 100 : 100 / count
    const bodyWidth = Math.max(2, step * 0.6)
    const candles = chartCandles.map((candle, index) => {
      const x = step * index + step / 2
      const highY = 60 - ((candle.high - min) / range) * 60
      const lowY = 60 - ((candle.low - min) / range) * 60
      const openY = 60 - ((candle.open - min) / range) * 60
      const closeY = 60 - ((candle.close - min) / range) * 60
      return {
        x,
        highY,
        lowY,
        openY,
        closeY,
        bodyTop: Math.min(openY, closeY),
        bodyHeight: Math.max(2, Math.abs(openY - closeY)),
        up: candle.close >= candle.open,
      }
    })
    return { candles, min, max, bodyWidth }
  }, [chartMode, chartCandles])

  return { chartSeries, chartData, preferredChartMarket, chartCandles, candleChart }
}
