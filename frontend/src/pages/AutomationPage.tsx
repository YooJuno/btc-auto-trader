import React from 'react'
import type { BotDefaults } from '../lib/types'

export type AutomationProps = {
  riskBadge: string
  configName: string
  setConfigName: (v: string) => void
  market: string
  setMarket: (v: string) => void
  loadedDefaults?: BotDefaults | null
  selectionMode: string
  setSelectionMode: (v: string) => void
  strategyMode: string
  setStrategyMode: (v: string) => void
  operationOptions: string[]
  operationMode: string
  setOperationMode: (v: string) => void
  operationLabelFor: (mode: string) => string
  riskPreset: string
  setRiskPreset: (v: string) => void
  maxPositions: number
  setMaxPositions: (v: number) => void
  dailyDd: number
  setDailyDd: (v: number) => void
  weeklyDd: number
  setWeeklyDd: (v: number) => void
  autoPickTopN: number
  setAutoPickTopN: (v: number) => void
  manualMarkets: string
  setManualMarkets: (v: string) => void
  advancedOpen: boolean
  setAdvancedOpen: React.Dispatch<React.SetStateAction<boolean>>
  emaFast: number | ''
  setEmaFast: (v: number | '') => void
  emaSlow: number | ''
  setEmaSlow: (v: number | '') => void
  rsiPeriod: number | ''
  setRsiPeriod: (v: number | '') => void
  atrPeriod: number | ''
  setAtrPeriod: (v: number | '') => void
  bbPeriod: number | ''
  setBbPeriod: (v: number | '') => void
  bbStdDev: number | ''
  setBbStdDev: (v: number | '') => void
  trendThreshold: number | ''
  setTrendThreshold: (v: number | '') => void
  volatilityHigh: number | ''
  setVolatilityHigh: (v: number | '') => void
  trendRsiBuyMin: number | ''
  setTrendRsiBuyMin: (v: number | '') => void
  trendRsiSellMax: number | ''
  setTrendRsiSellMax: (v: number | '') => void
  rangeRsiBuyMax: number | ''
  setRangeRsiBuyMax: (v: number | '') => void
  rangeRsiSellMin: number | ''
  setRangeRsiSellMin: (v: number | '') => void
  handleApplyDefaults: () => void
  handleClearOverrides: () => void
  handleSaveConfig: () => Promise<void>
  handleResetPaper: () => Promise<void>
  loading?: boolean
}

const AutomationPage: React.FC<AutomationProps> = (props) => {
  const {
    riskBadge,
    configName,
    setConfigName,
    market,
    setMarket,
    loadedDefaults,
    selectionMode,
    setSelectionMode,
    strategyMode,
    setStrategyMode,
    operationOptions,
    operationMode,
    setOperationMode,
    operationLabelFor,
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
    loading,
  } = props

  return (
    <section className="panel strategy-panel">
      <div className="panel-header">
        <h2>Strategy Builder</h2>
        <span className="pill">{riskBadge} risk</span>
      </div>
      <div className="control-grid">
        <label>
          Config name
          <input value={configName} onChange={(e) => setConfigName(e.target.value)} />
        </label>
        <label>
          Base market
          <select value={market} onChange={(e) => setMarket(e.target.value)}>
            {loadedDefaults?.availableMarkets?.map((option: string) => (
              <option key={option} value={option}>
                {option}
              </option>
            )) ?? <option value="KRW">KRW</option>}
          </select>
        </label>
        <label>
          Selection mode
          <select value={selectionMode} onChange={(e) => setSelectionMode(e.target.value)}>
            {loadedDefaults?.availableSelectionModes?.map((option: string) => (
              <option key={option} value={option}>
                {option}
              </option>
            )) ?? (
              <>
                <option value="AUTO">AUTO</option>
                <option value="MANUAL">MANUAL</option>
              </>
            )}
          </select>
        </label>
        <label>
          Strategy mode
          <select value={strategyMode} onChange={(e) => setStrategyMode(e.target.value)}>
            {loadedDefaults?.availableStrategyModes?.map((option: string) => (
              <option key={option} value={option}>
                {option}
              </option>
            )) ?? (
              <>
                <option value="AUTO">AUTO</option>
                <option value="SCALP">SCALP</option>
                <option value="DAY">DAY</option>
                <option value="SWING">SWING</option>
              </>
            )}
          </select>
        </label>
        <div className="mode-toggle">
          <span>Operation mode</span>
          <div className="mode-buttons">
            {operationOptions.map((mode: string) => (
              <button key={mode} type="button" className={`mode-pill ${operationMode === mode ? 'active' : ''}`} onClick={() => setOperationMode(mode)}>
                {operationLabelFor(mode)}
              </button>
            ))}
          </div>
        </div>
        <label>
          Risk preset
          <select value={riskPreset} onChange={(e) => setRiskPreset(e.target.value)}>
            {loadedDefaults?.availableRiskPresets?.map((option: string) => (
              <option key={option} value={option}>
                {option}
              </option>
            )) ?? (
              <>
                <option value="CONSERVATIVE">CONSERVATIVE</option>
                <option value="STANDARD">STANDARD</option>
                <option value="AGGRESSIVE">AGGRESSIVE</option>
              </>
            )}
          </select>
        </label>
        <label>
          Max positions
          <input type="number" value={maxPositions} min={1} max={10} onChange={(e) => setMaxPositions(Number(e.target.value))} />
        </label>
        <label>
          Daily drawdown (%)
          <input type="number" value={dailyDd} min={1} max={10} onChange={(e) => setDailyDd(Number(e.target.value))} />
        </label>
        <label>
          Weekly drawdown (%)
          <input type="number" value={weeklyDd} min={3} max={30} onChange={(e) => setWeeklyDd(Number(e.target.value))} />
        </label>
        <label>
          Auto pick top N
          <input type="number" value={autoPickTopN} min={1} max={20} onChange={(e) => setAutoPickTopN(Number(e.target.value))} />
        </label>
      </div>
      {selectionMode === 'MANUAL' && (
        <label className="full-width">
          Manual markets (KRW-BTC,KRW-ETH or BTC,ETH)
          <input value={manualMarkets} onChange={(e) => setManualMarkets(e.target.value)} />
        </label>
      )}

      <div className="advanced-section">
        <div className="advanced-header">
          <div>
            <h3>Advanced tuning</h3>
            <p className="hint">비워두면 기본 전략값을 사용합니다.</p>
          </div>
          <button className="ghost small" onClick={() => setAdvancedOpen((prev: boolean) => !prev)}>{advancedOpen ? '숨기기' : '열기'}</button>
        </div>
        {advancedOpen && (
          <>
            <div className="advanced-grid">
              <label>
                EMA fast
                <input type="number" value={emaFast} placeholder={`${loadedDefaults?.defaultEmaFast ?? 12}`} onChange={(e) => setEmaFast(Number(e.target.value) || '')} />
              </label>
              <label>
                EMA slow
                <input type="number" value={emaSlow} placeholder={`${loadedDefaults?.defaultEmaSlow ?? 26}`} onChange={(e) => setEmaSlow(Number(e.target.value) || '')} />
              </label>
              <label>
                RSI period
                <input type="number" value={rsiPeriod} placeholder={`${loadedDefaults?.defaultRsiPeriod ?? 14}`} onChange={(e) => setRsiPeriod(Number(e.target.value) || '')} />
              </label>
              <label>
                ATR period
                <input type="number" value={atrPeriod} placeholder={`${loadedDefaults?.defaultAtrPeriod ?? 14}`} onChange={(e) => setAtrPeriod(Number(e.target.value) || '')} />
              </label>
              <label>
                BB period
                <input type="number" value={bbPeriod} placeholder={`${loadedDefaults?.defaultBbPeriod ?? 20}`} onChange={(e) => setBbPeriod(Number(e.target.value) || '')} />
              </label>
              <label>
                BB std dev
                <input type="number" step="0.1" value={bbStdDev} placeholder={`${loadedDefaults?.defaultBbStdDev ?? 2.0}`} onChange={(e) => setBbStdDev(Number(e.target.value) || '')} />
              </label>
              <label>
                Trend threshold (0.005 = 0.5%)
                <input type="number" step="0.001" value={trendThreshold} placeholder={`${loadedDefaults?.defaultTrendThreshold ?? 0.005}`} onChange={(e) => setTrendThreshold(Number(e.target.value) || '')} />
              </label>
              <label>
                Volatility high (0.06 = 6%)
                <input type="number" step="0.01" value={volatilityHigh} placeholder={`${loadedDefaults?.defaultVolatilityHigh ?? 0.06}`} onChange={(e) => setVolatilityHigh(Number(e.target.value) || '')} />
              </label>
              <label>
                Trend RSI buy min
                <input type="number" value={trendRsiBuyMin} placeholder={`${loadedDefaults?.defaultTrendRsiBuyMin ?? 52}`} onChange={(e) => setTrendRsiBuyMin(Number(e.target.value) || '')} />
              </label>
              <label>
                Trend RSI sell max
                <input type="number" value={trendRsiSellMax} placeholder={`${loadedDefaults?.defaultTrendRsiSellMax ?? 48}`} onChange={(e) => setTrendRsiSellMax(Number(e.target.value) || '')} />
              </label>
              <label>
                Range RSI buy max
                <input type="number" value={rangeRsiBuyMax} placeholder={`${loadedDefaults?.defaultRangeRsiBuyMax ?? 35}`} onChange={(e) => setRangeRsiBuyMax(Number(e.target.value) || '')} />
              </label>
              <label>
                Range RSI sell min
                <input type="number" value={rangeRsiSellMin} placeholder={`${loadedDefaults?.defaultRangeRsiSellMin ?? 65}`} onChange={(e) => setRangeRsiSellMin(Number(e.target.value) || '')} />
              </label>
            </div>
            <div className="advanced-actions">
              <button className="ghost small" onClick={handleApplyDefaults} type="button">기본값 채우기</button>
              <button className="ghost small" onClick={handleClearOverrides} type="button">비우기</button>
            </div>
          </>
        )}
      </div>
      <div className="button-row">
        <button className="primary" onClick={handleSaveConfig} disabled={loading}>설정 저장</button>
        <button className="ghost" onClick={handleResetPaper} disabled={loading}>모의계좌 리셋</button>
      </div>
    </section>
  )
}

export default AutomationPage
