import { useMemo } from 'react'
import type { Recommendation } from '../lib/types'

type Params = {
  streamRecommendations: Recommendation[]
  marketFilter: string
  autoPickTopN: number
}

export default function useRecommendations({ streamRecommendations, marketFilter, autoPickTopN }: Params) {
  const normalizedFilter = useMemo(() => marketFilter.trim().toUpperCase(), [marketFilter])

  const placeholderRecommendations = useMemo(() => {
    const count = Math.max(1, autoPickTopN)
    return Array.from({ length: count }, () => ({
      market: '--',
      score: 0,
      lastPrice: 0,
      volume24h: 0,
      volatilityPct: 0,
      trendStrengthPct: 0,
    }))
  }, [autoPickTopN])

  const displayRecommendations = useMemo(
    () => (streamRecommendations.length > 0 ? streamRecommendations : placeholderRecommendations),
    [streamRecommendations, placeholderRecommendations]
  )

  const filteredRecommendations = useMemo(() => {
    const term = normalizedFilter
    if (!term) return displayRecommendations
    return displayRecommendations.filter((coin) => coin.market.toUpperCase().includes(term))
  }, [normalizedFilter, displayRecommendations])

  const maxRecommendationsToShow = Math.min(5, autoPickTopN)
  const visibleRecommendations = filteredRecommendations.slice(0, maxRecommendationsToShow)
  const hiddenRecommendations = Math.max(0, filteredRecommendations.length - visibleRecommendations.length)

  const signalTape = useMemo(() => displayRecommendations.slice(0, 4).map((coin) => ({
    market: coin.market,
    price: coin.lastPrice,
    score: coin.score,
    tag: {
      label: coin.volatilityPct >= 5 ? 'VOLATILE' : coin.trendStrengthPct >= 0.5 ? 'TREND UP' : coin.trendStrengthPct <= -0.5 ? 'TREND DOWN' : 'RANGE',
      tone: coin.volatilityPct >= 5 ? 'warn' : coin.trendStrengthPct >= 0.5 ? 'up' : coin.trendStrengthPct <= -0.5 ? 'down' : 'flat',
    },
  })), [displayRecommendations])

  return {
    placeholderRecommendations,
    displayRecommendations,
    filteredRecommendations,
    visibleRecommendations,
    hiddenRecommendations,
    maxRecommendationsToShow,
    signalTape,
    normalizedFilter,
  }
}
