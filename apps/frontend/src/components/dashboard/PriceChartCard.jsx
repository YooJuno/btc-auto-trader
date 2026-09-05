import { memo, useEffect, useMemo, useRef, useState } from 'react'
import {
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  LineStyle,
  createChart,
  createSeriesMarkers,
} from 'lightweight-charts'
import { requestJson } from '../../utils/apiClient.js'

/*
 * Price chart with the engine's own fills marked on it.
 *
 * The console previously had no visualisation of any kind — package.json carried no charting
 * dependency and no <svg>/<canvas> existed anywhere — so an operator could not see price action, where
 * their average buy sat, or where the bot actually entered and exited. Showing fills against candles is
 * the fastest way to judge whether the strategy is behaving.
 *
 * Colours follow the Upbit/Korean convention: red = up, blue = down.
 */

// Upbit minute-candle units. 60 matches the engine's signal timeframe; 240 matches its HTF confirm.
const TIMEFRAMES = [
  { unit: 15, label: '15m' },
  { unit: 60, label: '1H' },
  { unit: 240, label: '4H' },
]

// Stable identity so the derived `candles` reference does not change on every render.
const EMPTY_CANDLES = Object.freeze([])

const UP = '#f0616d'
const DOWN = '#4d90f0'

const toEpochSeconds = (isoUtc) => {
  if (!isoUtc) return null
  // Upbit returns "2026-09-05T09:00:00" with no zone marker; it is UTC on this field.
  const normalized = /[zZ]|[+-]\d{2}:\d{2}$/.test(isoUtc) ? isoUtc : `${isoUtc}Z`
  const ms = Date.parse(normalized)
  return Number.isFinite(ms) ? Math.floor(ms / 1000) : null
}

const seoulTime = (epochSeconds, withDate) => {
  const date = new Date(epochSeconds * 1000)
  return date.toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour12: false,
    ...(withDate ? { month: '2-digit', day: '2-digit' } : {}),
    hour: '2-digit',
    minute: '2-digit',
  })
}

function PriceChartCard({ market, unitDefault = 60, orders, avgBuyPrice }) {
  const hostRef = useRef(null)
  const chartRef = useRef(null)
  const seriesRef = useRef(null)
  const markersRef = useRef(null)
  const priceLineRef = useRef(null)

  const [unit, setUnit] = useState(unitDefault)
  // One piece of state tagged with the request it answers. Deriving `loading` from it means the effect
  // never sets state synchronously, and candles for one market can never be shown under another.
  const [chartData, setChartData] = useState({ key: null, candles: [], error: null })

  const dataKey = market ? `${market}|${unit}` : null
  const settled = chartData.key === dataKey
  const candles = settled ? chartData.candles : EMPTY_CANDLES
  const error = settled ? chartData.error : null
  const loading = Boolean(market) && !settled

  // Create the chart once; data and markers are applied separately so a refresh never rebuilds it.
  useEffect(() => {
    const host = hostRef.current
    if (!host) return undefined

    const chart = createChart(host, {
      layout: {
        background: { type: ColorType.Solid, color: '#111317' },
        textColor: '#8b94a3',
        fontSize: 11,
        fontFamily: "'IBM Plex Mono', ui-monospace, monospace",
      },
      grid: {
        vertLines: { color: '#1d2129' },
        horzLines: { color: '#1d2129' },
      },
      rightPriceScale: { borderColor: '#262b35' },
      timeScale: {
        borderColor: '#262b35',
        timeVisible: true,
        secondsVisible: false,
        tickMarkFormatter: (time) => seoulTime(time, false),
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: '#5b6474', width: 1, style: LineStyle.Dotted, labelBackgroundColor: '#262b35' },
        horzLine: { color: '#5b6474', width: 1, style: LineStyle.Dotted, labelBackgroundColor: '#262b35' },
      },
      localization: {
        locale: 'ko-KR',
        timeFormatter: (time) => seoulTime(time, true),
      },
      autoSize: true,
    })

    const series = chart.addSeries(CandlestickSeries, {
      upColor: UP,
      downColor: DOWN,
      borderUpColor: UP,
      borderDownColor: DOWN,
      wickUpColor: UP,
      wickDownColor: DOWN,
    })

    chartRef.current = chart
    seriesRef.current = series
    markersRef.current = createSeriesMarkers(series, [])

    return () => {
      chart.remove()
      chartRef.current = null
      seriesRef.current = null
      markersRef.current = null
      priceLineRef.current = null
    }
  }, [])

  // Fetch candles whenever the market or timeframe changes.
  useEffect(() => {
    if (!market) {
      return undefined
    }
    let cancelled = false
    const key = dataKey

    requestJson(
      `/api/market/candles?market=${encodeURIComponent(market)}&unit=${unit}&count=200`,
      {},
      '캔들 조회 실패'
    )
      .then((data) => {
        if (cancelled) return
        const rows = Array.isArray(data?.candles) ? data.candles : []
        const mapped = rows
          .map((row) => {
            const time = toEpochSeconds(row.time)
            if (time === null) return null
            return {
              time,
              open: Number(row.open),
              high: Number(row.high),
              low: Number(row.low),
              close: Number(row.close),
            }
          })
          .filter((row) => row && Number.isFinite(row.close))
        setChartData({ key, candles: mapped, error: null })
      })
      .catch((err) => {
        if (!cancelled) {
          setChartData({ key, candles: [], error: err?.message ?? '캔들 조회 실패' })
        }
      })

    return () => {
      cancelled = true
    }
  }, [market, unit, dataKey])

  useEffect(() => {
    const series = seriesRef.current
    if (!series || candles.length === 0) return
    series.setData(candles)
    chartRef.current?.timeScale().fitContent()
  }, [candles])

  // Fills for this market, snapped onto the candle grid so a marker always lands on a bar.
  const markers = useMemo(() => {
    if (!Array.isArray(orders) || candles.length === 0) return []
    const bucket = unit * 60
    const first = candles[0].time
    const last = candles[candles.length - 1].time

    return orders
      .filter((order) => order?.market === market && order?.requestedAt)
      .map((order) => {
        const ms = Date.parse(order.requestedAt)
        if (!Number.isFinite(ms)) return null
        const time = Math.floor(Math.floor(ms / 1000) / bucket) * bucket
        if (time < first || time > last) return null
        const isBuy = String(order.side).toUpperCase() === 'BUY'
        return {
          time,
          position: isBuy ? 'belowBar' : 'aboveBar',
          color: isBuy ? UP : DOWN,
          shape: isBuy ? 'arrowUp' : 'arrowDown',
          text: isBuy ? '매수' : '매도',
        }
      })
      .filter(Boolean)
      .sort((a, b) => a.time - b.time)
  }, [orders, market, candles, unit])

  useEffect(() => {
    markersRef.current?.setMarkers(markers)
  }, [markers])

  // Average buy price line — "am I above or below my cost" is the question a position screen must answer.
  useEffect(() => {
    const series = seriesRef.current
    if (!series) return
    if (priceLineRef.current) {
      series.removePriceLine(priceLineRef.current)
      priceLineRef.current = null
    }
    const price = Number(avgBuyPrice)
    if (Number.isFinite(price) && price > 0) {
      priceLineRef.current = series.createPriceLine({
        price,
        color: '#d99a3e',
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: true,
        title: '평균매수',
      })
    }
  }, [avgBuyPrice, candles])

  return (
    <section className="table-card chart-panel">
      <div className="table-header">
        <h2>{market || '마켓 미선택'}</h2>
        <div className="chart-toolbar">
          {TIMEFRAMES.map((tf) => (
            <button
              key={tf.unit}
              type="button"
              className={`chart-tf ${unit === tf.unit ? 'is-active' : ''}`}
              onClick={() => setUnit(tf.unit)}
            >
              {tf.label}
            </button>
          ))}
        </div>
      </div>

      {error && <p className="status-error">{error}</p>}
      {!market && <div className="empty-state">자동매매 마켓을 설정하면 차트가 표시됩니다.</div>}
      {market && !error && candles.length === 0 && loading && (
        <div className="empty-state">캔들을 불러오는 중…</div>
      )}

      <div className="chart-host" ref={hostRef} style={{ display: market && !error ? 'block' : 'none' }} />
    </section>
  )
}

export default memo(PriceChartCard)
