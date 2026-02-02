export type BotDefaults = {
  defaultMarket: string
  defaultSelectionMode: string
  defaultStrategyMode: string
  defaultRiskPreset: string
  defaultOperationMode: string
  defaultMaxPositions: number
  defaultDailyDrawdownPct: number
  defaultWeeklyDrawdownPct: number
  defaultAutoPickTopN: number
  defaultEmaFast: number
  defaultEmaSlow: number
  defaultRsiPeriod: number
  defaultAtrPeriod: number
  defaultBbPeriod: number
  defaultBbStdDev: number
  defaultTrendThreshold: number
  defaultVolatilityHigh: number
  defaultTrendRsiBuyMin: number
  defaultTrendRsiSellMax: number
  defaultRangeRsiBuyMax: number
  defaultRangeRsiSellMin: number
  engineEnabled: boolean
  engineIntervalMs: number
  availableMarkets: string[]
  availableSelectionModes: string[]
  availableStrategyModes: string[]
  availableRiskPresets: string[]
  availableOperationModes: string[]
}

export type Recommendation = {
  market: string
  score: number
  lastPrice: number
  volume24h: number
  volatilityPct: number
  trendStrengthPct: number
}

export type PaperPosition = {
  market: string
  quantity: number
  entryPrice: number
  lastPrice: number
  unrealizedPnl: number
  unrealizedPnlPct: number
}

export type PaperSummary = {
  cashBalance: number
  equity: number
  realizedPnl: number
  unrealizedPnl: number
  positions: PaperPosition[]
}

export type PerformancePoint = {
  label: string
  equity: number
  returnPct: number
}

export type PaperPerformance = {
  totalReturnPct: number
  maxDrawdownPct: number
  daily: PerformancePoint[]
  weekly: PerformancePoint[]
}

export type MarketStreamEvent = {
  timestamp: string
  recommendations: Recommendation[]
}

export type MarketCandlePoint = {
  timestamp: string
  open: number
  high: number
  low: number
  close: number
}

export type SignalTag = {
  label: string
  tone: 'up' | 'down' | 'flat' | 'warn'
}
