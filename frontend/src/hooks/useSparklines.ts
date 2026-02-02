import { useEffect, useState } from 'react'
import { apiFetch } from '../lib/api'
import type { MarketCandlePoint } from '../lib/types'

export default function useSparklines(markets: string[]) {
  const [sparklines, setSparklines] = useState<Record<string, MarketCandlePoint[]>>({})

  useEffect(() => {
    if (markets.length === 0) return
    let cancelled = false
    markets.forEach((market) => {
      apiFetch<MarketCandlePoint[]>(`/api/market/candles?market=${encodeURIComponent(market)}&limit=30`)
        .then((data) => {
          if (cancelled) return
          setSparklines((prev) => ({ ...prev, [market]: data }))
        })
        .catch(() => null)
    })
    return () => {
      cancelled = true
    }
  }, [markets.join('|')])

  return sparklines
}
