import { memo } from 'react'
import {
  DEFAULT_MARKET_MAX_ORDER_KRW,
  DEFAULT_MARKET_PROFILE,
  PROFILE_VALUES,
} from '../../constants/tradingUi.js'
import {
  applyRatioPresetToMarket,
  clearMarketRatioOverrides,
  normalizeProfileValue,
  removeMarketRow,
  resolvePresetDisplayName,
  toInputValue,
  updateMarketOverrideInput,
} from '../../utils/tradingUi.js'

// An explicitly entered 0 disables that exit. Blank means "inherit the default", which is not the same.
const isDisabledRatio = (value) => {
  if (value === null || value === undefined || String(value).trim() === '') {
    return false
  }
  return Number(value) === 0
}

function MarketOverridesCard({
  strategyError,
  ratioError,
  presetError,
  ratioPresets,
  selectedRatioPresetByMarket,
  setSelectedRatioPresetByMarket,
  marketRows,
  marketConfigSaving,
  marketConfigLoading,
  marketConfigError,
  marketConfigNotice,
  marketRowsDirty,
  newMarketInput,
  setNewMarketInput,
  marketSuggestions,
  marketSuggestOpen,
  setMarketSuggestOpen,
  marketSuggestIndex,
  setMarketSuggestIndex,
  expandedMarket,
  setExpandedMarket,
  strategy,
  setRatioError,
  setMarketRows,
  setMarketConfigError,
  setMarketConfigNotice,
  handleSelectMarketSuggestion,
  handleAddMarket,
  handleMarketReload,
  onSaveMarketOverrides,
  readOnly = false,
}) {
  return (
    <article className="control-card market-card">
      <div className="card-head">
        <div>
          <h2>마켓별 설정</h2>
          <p className="sub">여러 마켓을 동시에 자동매매하고, 마켓별 주문 한도와 개별 전략 값을 따로 저장합니다.</p>
        </div>
        <span className={`pill ${marketRowsDirty ? 'pill-warning' : ''}`}>
          {marketRowsDirty ? '변경 있음' : '저장됨'}
        </span>
      </div>
      {strategyError && <p className="status-error">{strategyError}</p>}
      {ratioError && <p className="status-error">{ratioError}</p>}
      {presetError && <p className="status-error">{presetError}</p>}
      {marketConfigError && <p className="status-error">{marketConfigError}</p>}
      {marketConfigNotice && <p className="status-success">{marketConfigNotice}</p>}
      {readOnly && <p className="sub compact">로그인 전 조회 전용 모드입니다. 변경 사항은 저장되지 않습니다.</p>}
      <div className="market-add-row">
        <div className="market-add-input-wrap">
          <input
            type="text"
            value={newMarketInput}
            placeholder="코인명/심볼/마켓코드 검색 (예: 이더리움, ETH, KRW-ETH)"
            disabled={readOnly}
            onFocus={() => {
              if (marketSuggestions.length > 0) {
                setMarketSuggestOpen(true)
              }
            }}
            onBlur={() => {
              window.setTimeout(() => setMarketSuggestOpen(false), 120)
            }}
            onChange={(event) => {
              setNewMarketInput(event.target.value)
              setMarketSuggestOpen(true)
              setMarketSuggestIndex(0)
            }}
            onKeyDown={(event) => {
              if (event.key === 'ArrowDown' && marketSuggestions.length > 0) {
                event.preventDefault()
                setMarketSuggestOpen(true)
                setMarketSuggestIndex((prev) => (prev + 1) % marketSuggestions.length)
                return
              }
              if (event.key === 'ArrowUp' && marketSuggestions.length > 0) {
                event.preventDefault()
                setMarketSuggestOpen(true)
                setMarketSuggestIndex((prev) => (prev - 1 + marketSuggestions.length) % marketSuggestions.length)
                return
              }
              if (event.key === 'Escape') {
                setMarketSuggestOpen(false)
                return
              }
              if (event.key === 'Enter') {
                event.preventDefault()
                if (marketSuggestOpen && marketSuggestions.length > 0) {
                  const selected = marketSuggestions[Math.max(0, Math.min(marketSuggestIndex, marketSuggestions.length - 1))]
                  if (selected?.market) {
                    handleSelectMarketSuggestion(selected.market)
                    return
                  }
                }
                handleAddMarket()
              }
            }}
          />
          {marketSuggestOpen && marketSuggestions.length > 0 && (
            <div className="market-suggest-list">
              {marketSuggestions.map((item, index) => (
                <button
                  key={item.market}
                  className={`market-suggest-item ${index === marketSuggestIndex ? 'active' : ''}`}
                  type="button"
                  onMouseDown={(event) => {
                    event.preventDefault()
                    handleSelectMarketSuggestion(item.market)
                  }}
                >
                  <strong>{item.market}</strong>
                  <span>{item.koreanName || item.englishName || item.ticker}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <button
          className="ghost-button"
          onClick={() => handleAddMarket()}
          disabled={readOnly || marketConfigLoading || marketConfigSaving}
          type="button"
        >
          마켓 추가
        </button>
      </div>
      {marketConfigLoading ? (
        <div className="empty-state">마켓 설정을 불러오는 중입니다…</div>
      ) : marketRows.length === 0 ? (
        <div className="empty-state">등록된 마켓이 없습니다. 비워 두면 자동매매는 실행되지 않습니다.</div>
      ) : (
        <div className="market-override-list">
          <div className="market-grid-header">
            <span>마켓</span>
            <span>최대 매수 KRW</span>
            <span>프로필</span>
            <span>자동매매</span>
            <span>관리</span>
          </div>
          {marketRows.map((row) => {
            const expanded = expandedMarket === row.market
            const effectiveProfileLabel = normalizeProfileValue(row.profile) || DEFAULT_MARKET_PROFILE
            return (
              <div className={`market-override-row ${expanded ? 'expanded' : ''}`} key={row.market}>
                <div className="market-override-main">
                  <div className="market-symbol">
                    <button
                      className={`market-expand-button ${expanded ? 'open' : ''}`}
                      onClick={() => setExpandedMarket((prev) => (prev === row.market ? null : row.market))}
                      aria-label={expanded ? `${row.market} 비율 설정 닫기` : `${row.market} 비율 설정 열기`}
                      type="button"
                    >
                      <span>▾</span>
                    </button>
                    <strong>{row.market}</strong>
                  </div>
                  <label className="market-inline-field">
                    <input
                      type="number"
                      step="1000"
                      min="0"
                      placeholder={strategy?.maxOrderKrw ? toInputValue(strategy.maxOrderKrw) : DEFAULT_MARKET_MAX_ORDER_KRW}
                      value={row.maxOrderKrw}
                      disabled={readOnly}
                      onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'maxOrderKrw', event.target.value)}
                    />
                  </label>
                  <label className="market-inline-field">
                    <select
                      value={normalizeProfileValue(row.profile) || DEFAULT_MARKET_PROFILE}
                      disabled={readOnly}
                      onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'profile', event.target.value)}
                    >
                      {PROFILE_VALUES.map((profile) => (
                        <option key={profile} value={profile}>
                          {profile}
                        </option>
                      ))}
                    </select>
                  </label>
                  <div className="market-toggle-field">
                    <span className={`market-status-pill ${row.tradePaused ? 'is-paused' : 'is-active'}`}>
                      {row.tradePaused ? '일시정지' : '활성'}
                    </span>
                    <button
                      type="button"
                      className={`market-toggle-button ${row.tradePaused ? 'is-resume' : 'is-pause'}`}
                      onClick={() => updateMarketOverrideInput(setMarketRows, row.market, 'tradePaused', !row.tradePaused)}
                      disabled={readOnly}
                      aria-pressed={row.tradePaused}
                    >
                      {row.tradePaused ? '재개' : '일시정지'}
                    </button>
                  </div>
                  <button
                    className="market-remove-button"
                    onClick={() => removeMarketRow(
                      setMarketRows,
                      row.market,
                      setMarketConfigNotice,
                      setMarketConfigError,
                      setSelectedRatioPresetByMarket
                    )}
                    disabled={readOnly || marketConfigSaving}
                    type="button"
                  >
                    제거
                  </button>
                </div>

                <div className={`market-ratio-panel ${expanded ? 'open' : ''}`}>
                  <div className="market-ratio-panel-inner">
                    <div className="market-ratio-head">
                      <h3>{row.market} 비율 설정</h3>
                      <span className="pill">PROFILE {effectiveProfileLabel}</span>
                    </div>
                    <p className="sub compact">빈 값은 전역 전략 비율을 사용하고, 입력한 값만 이 마켓 override로 저장됩니다.</p>
                    <div className="preset-row">
                      {ratioPresets.length === 0 ? (
                        <p className="sub compact">등록된 프리셋이 없습니다.</p>
                      ) : ratioPresets.map((preset) => (
                        <button
                          key={`${row.market}-${preset.code}`}
                          className={`ghost-button ${selectedRatioPresetByMarket[row.market] === preset.code ? 'active' : ''}`}
                          onClick={() => applyRatioPresetToMarket(
                            preset,
                            row.market,
                            setMarketRows,
                            setSelectedRatioPresetByMarket,
                            setRatioError
                          )}
                          disabled={readOnly}
                          type="button"
                        >
                          {preset.displayName} 비율 적용
                        </button>
                      ))}
                    </div>
                    {selectedRatioPresetByMarket[row.market] && (
                      <p className="sub compact">
                        {resolvePresetDisplayName(ratioPresets, selectedRatioPresetByMarket[row.market])} 프리셋이
                        입력값에 적용되었습니다. 아래 마켓 설정 저장 버튼을 눌러야 서버 반영됩니다.
                      </p>
                    )}
                    {/*
                      Two different kinds of number used to sit in one undifferentiated grid with
                      confusable Korean labels: 손절 % is a PRICE threshold, 손절/트레일링 매도 % is a
                      POSITION FRACTION. Setting the latter to 0 disarmed the stop for that market with no
                      warning anywhere. They are now separated and 0 is called out explicitly.
                    */}
                    <div className="field-group">
                      <span className="field-group__title">청산 트리거 — 가격 기준 %</span>
                      <div className="form-grid market-ratio-grid">
                        <label className="form-field">
                          <span>익절 %</span>
                          <input
                            type="number"
                            step="0.1"
                            placeholder={toInputValue(strategy?.takeProfitPct)}
                            value={row.takeProfitPct}
                            disabled={readOnly}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'takeProfitPct', event.target.value)}
                          />
                          <span className="field-hint">평균 매수가 대비 상승 시 부분 익절</span>
                        </label>
                        <label className="form-field">
                          <span>손절 %</span>
                          <input
                            type="number"
                            step="0.1"
                            placeholder={toInputValue(strategy?.stopLossPct)}
                            value={row.stopLossPct}
                            disabled={readOnly}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'stopLossPct', event.target.value)}
                          />
                          <span className="field-hint">평균 매수가 대비 하락 시 전량 손절</span>
                        </label>
                        <label className="form-field">
                          <span>트레일링 %</span>
                          <input
                            type="number"
                            step="0.1"
                            placeholder={toInputValue(strategy?.trailingStopPct)}
                            value={row.trailingStopPct}
                            disabled={readOnly}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'trailingStopPct', event.target.value)}
                          />
                          <span className="field-hint">진입 후 최고가 대비 하락폭</span>
                        </label>
                      </div>
                    </div>

                    <div className="field-group">
                      <span className="field-group__title">청산 비중 — 보유 수량 기준 %</span>
                      <div className="form-grid market-ratio-grid">
                        <label className="form-field">
                          <span>부분 익절 매도 %</span>
                          <input
                            type="number"
                            step="1"
                            placeholder={toInputValue(strategy?.partialTakeProfitPct)}
                            value={row.partialTakeProfitPct}
                            disabled={readOnly}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'partialTakeProfitPct', event.target.value)}
                          />
                          {isDisabledRatio(row.partialTakeProfitPct) && (
                            <span className="field-warning">0 이면 익절이 실행되지 않습니다.</span>
                          )}
                        </label>
                        <label className="form-field">
                          <span>손절/트레일링 매도 %</span>
                          <input
                            type="number"
                            step="1"
                            placeholder={toInputValue(strategy?.stopExitPct)}
                            value={row.stopExitPct}
                            disabled={readOnly}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'stopExitPct', event.target.value)}
                          />
                          <span className="field-hint">비워두면 전량(100%) 매도</span>
                        </label>
                        <label className="form-field">
                          <span>추세 이탈 매도 %</span>
                          <input
                            type="number"
                            step="1"
                            placeholder={toInputValue(strategy?.trendExitPct)}
                            value={row.trendExitPct}
                            disabled={readOnly}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'trendExitPct', event.target.value)}
                          />
                          {isDisabledRatio(row.trendExitPct) && (
                            <span className="field-warning">0 이면 추세 이탈 청산이 비활성화됩니다.</span>
                          )}
                        </label>
                        <label className="form-field">
                          <span>모멘텀 역전 매도 %</span>
                          <input
                            type="number"
                            step="1"
                            placeholder={toInputValue(strategy?.momentumExitPct)}
                            value={row.momentumExitPct}
                            disabled={readOnly}
                            onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'momentumExitPct', event.target.value)}
                          />
                          {isDisabledRatio(row.momentumExitPct) && (
                            <span className="field-warning">0 이면 모멘텀 청산이 비활성화됩니다.</span>
                          )}
                        </label>
                      </div>
                    </div>
                    <div className="button-row">
                      <button
                        className="ghost-button"
                        onClick={() => clearMarketRatioOverrides(
                          setMarketRows,
                          row.market,
                          setSelectedRatioPresetByMarket,
                          setRatioError
                        )}
                        disabled={readOnly}
                        type="button"
                      >
                        이 마켓 비율 초기화
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}
      <p className="sub compact">이 목록이 비어 있으면 자동매매는 멈추고, 하나 이상 있으면 등록된 마켓들을 동시에 대상으로 사용합니다.</p>
      <div className="button-row">
        <button
          className="primary-button"
          onClick={onSaveMarketOverrides}
          disabled={readOnly || marketConfigLoading || marketConfigSaving || !marketRowsDirty}
          type="button"
        >
          {readOnly
            ? '로그인 후 저장 가능'
            : marketConfigSaving
            ? '저장 중...'
            : marketRowsDirty
            ? '마켓 설정 저장'
            : '변경사항 없음'}
        </button>
        <button
          className="ghost-button"
          onClick={() => handleMarketReload()}
          disabled={marketConfigSaving}
          type="button"
        >
          다시 불러오기
        </button>
      </div>
    </article>
  )
}

export default memo(MarketOverridesCard)
