import { useEffect, useState } from 'react'
import { toNumberOrNull } from '../lib/utils'
import { apiFetch } from '../lib/api'
import type { BotDefaults } from '../lib/types'

type UseAutomationParams = {
  token: string
  loadedDefaults?: BotDefaults | null
  resetPaper?: () => Promise<any>
  onError?: (msg: string) => void
}

export default function useAutomation({ token, loadedDefaults, resetPaper, onError }: UseAutomationParams) {
  const [configName, setConfigName] = useState('KRW Standard')
  const [market, setMarket] = useState('KRW')
  const [selectionMode, setSelectionMode] = useState('AUTO')
  const [strategyMode, setStrategyMode] = useState('AUTO')
  const [riskPreset, setRiskPreset] = useState('STANDARD')
  const [operationMode, setOperationMode] = useState('STABLE')
  const [maxPositions, setMaxPositions] = useState(3)
  const [dailyDd, setDailyDd] = useState(3)
  const [weeklyDd, setWeeklyDd] = useState(8)
  const [autoPickTopN, setAutoPickTopN] = useState(5)
  const [manualMarkets, setManualMarkets] = useState('')
  const [advancedOpen, setAdvancedOpen] = useState(false)

  const [emaFast, setEmaFast] = useState<number | ''>('')
  const [emaSlow, setEmaSlow] = useState<number | ''>('')
  const [rsiPeriod, setRsiPeriod] = useState<number | ''>('')
  const [atrPeriod, setAtrPeriod] = useState<number | ''>('')
  const [bbPeriod, setBbPeriod] = useState<number | ''>('')
  const [bbStdDev, setBbStdDev] = useState<number | ''>('')
  const [trendThreshold, setTrendThreshold] = useState<number | ''>('')
  const [volatilityHigh, setVolatilityHigh] = useState<number | ''>('')
  const [trendRsiBuyMin, setTrendRsiBuyMin] = useState<number | ''>('')
  const [trendRsiSellMax, setTrendRsiSellMax] = useState<number | ''>('')
  const [rangeRsiBuyMax, setRangeRsiBuyMax] = useState<number | ''>('')
  const [rangeRsiSellMin, setRangeRsiSellMin] = useState<number | ''>('')

  useEffect(() => {
    if (!loadedDefaults) return
    setMarket(loadedDefaults.defaultMarket)
    setSelectionMode(loadedDefaults.defaultSelectionMode)
    setStrategyMode(loadedDefaults.defaultStrategyMode)
    setRiskPreset(loadedDefaults.defaultRiskPreset)
    setOperationMode(loadedDefaults.defaultOperationMode ?? 'STABLE')
    setMaxPositions(loadedDefaults.defaultMaxPositions)
    setDailyDd(loadedDefaults.defaultDailyDrawdownPct)
    setWeeklyDd(loadedDefaults.defaultWeeklyDrawdownPct)
    setAutoPickTopN(loadedDefaults.defaultAutoPickTopN)
  }, [loadedDefaults])

  const handleApplyDefaults = () => {
    if (!loadedDefaults) return
    setEmaFast(loadedDefaults.defaultEmaFast)
    setEmaSlow(loadedDefaults.defaultEmaSlow)
    setRsiPeriod(loadedDefaults.defaultRsiPeriod)
    setAtrPeriod(loadedDefaults.defaultAtrPeriod)
    setBbPeriod(loadedDefaults.defaultBbPeriod)
    setBbStdDev(loadedDefaults.defaultBbStdDev)
    setTrendThreshold(loadedDefaults.defaultTrendThreshold)
    setVolatilityHigh(loadedDefaults.defaultVolatilityHigh)
    setTrendRsiBuyMin(loadedDefaults.defaultTrendRsiBuyMin)
    setTrendRsiSellMax(loadedDefaults.defaultTrendRsiSellMax)
    setRangeRsiBuyMax(loadedDefaults.defaultRangeRsiBuyMax)
    setRangeRsiSellMin(loadedDefaults.defaultRangeRsiSellMin)
  }

  const handleClearOverrides = () => {
    setEmaFast('')
    setEmaSlow('')
    setRsiPeriod('')
    setAtrPeriod('')
    setBbPeriod('')
    setBbStdDev('')
    setTrendThreshold('')
    setVolatilityHigh('')
    setTrendRsiBuyMin('')
    setTrendRsiSellMax('')
    setRangeRsiBuyMax('')
    setRangeRsiSellMin('')
  }

  const handleSaveConfig = async () => {
    if (!token) {
      onError?.('로그인이 필요합니다.')
      return
    }
    try {
      const payload: any = {
        name: configName,
        market,
        selectionMode,
        strategyMode,
        operationMode,
        riskPreset,
        maxPositions,
        autoPickTopN,
        dailyDd,
        weeklyDd,
        emaFast: toNumberOrNull(emaFast),
        emaSlow: toNumberOrNull(emaSlow),
        rsiPeriod: toNumberOrNull(rsiPeriod),
        atrPeriod: toNumberOrNull(atrPeriod),
        bbPeriod: toNumberOrNull(bbPeriod),
        bbStdDev: toNumberOrNull(bbStdDev),
        trendThreshold: toNumberOrNull(trendThreshold),
        volatilityHigh: toNumberOrNull(volatilityHigh),
        trendRsiBuyMin: toNumberOrNull(trendRsiBuyMin),
        trendRsiSellMax: toNumberOrNull(trendRsiSellMax),
        rangeRsiBuyMax: toNumberOrNull(rangeRsiBuyMax),
        rangeRsiSellMin: toNumberOrNull(rangeRsiSellMin),
      }

      Object.entries(payload).forEach(([key, value]) => {
        if (value === null || value === undefined) delete payload[key]
      })

      await apiFetch('/api/bot-configs', token, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    } catch (error) {
      onError?.('설정 저장 실패. API 연결을 확인해주세요.')
    }
  }

  const handleResetPaper = async () => {
    if (!token) {
      onError?.('로그인이 필요합니다.')
      return
    }
    try {
      const updated = await resetPaper?.()
      if (!updated) onError?.('리셋 실패')
    } catch (error) {
      onError?.('리셋 실패')
    }
  }

  return {
    configName,
    setConfigName,
    market,
    setMarket,
    selectionMode,
    setSelectionMode,
    strategyMode,
    setStrategyMode,
    operationOptions: loadedDefaults?.availableOperationModes ?? ['STABLE', 'ATTACK'],
    operationMode,
    setOperationMode,
    operationLabelFor: (mode: string) => (mode === 'ATTACK' ? '공격' : mode === 'STABLE' ? '안정' : mode),
    riskPreset,
    setRiskPreset,
    maxPositions,
    setMaxPositions,
    dailyDd,
    setDailyDd,
    weeklyDd,
    setWeeklyDd,
    autoPickTopN,
    setAutoPickTopN,
    manualMarkets,
    setManualMarkets,
    advancedOpen,
    setAdvancedOpen,
    emaFast,
    setEmaFast,
    emaSlow,
    setEmaSlow,
    rsiPeriod,
    setRsiPeriod,
    atrPeriod,
    setAtrPeriod,
    bbPeriod,
    setBbPeriod,
    bbStdDev,
    setBbStdDev,
    trendThreshold,
    setTrendThreshold,
    volatilityHigh,
    setVolatilityHigh,
    trendRsiBuyMin,
    setTrendRsiBuyMin,
    trendRsiSellMax,
    setTrendRsiSellMax,
    rangeRsiBuyMax,
    setRangeRsiBuyMax,
    rangeRsiSellMin,
    setRangeRsiSellMin,
    handleApplyDefaults,
    handleClearOverrides,
    handleSaveConfig,
    handleResetPaper,
  }
}
