function SettingsRoute({
  profileValues,
  settingsLoading,
  settingsSaving,
  userSettings,
  userSettingsError,
  userSettingsNotice,
  userRiskProfile,
  userMarketsInput,
  setUserRiskProfile,
  setUserMarketsInput,
  handleSaveMySettings,
  fetchMySettings,
  commonUiPrefs,
  mobileUiPrefs,
  desktopUiPrefs,
  handleRefreshSecChange,
  handleDefaultRouteChange,
  handleTableDensityChange,
  deviceLabel,
  effectiveRouteLabel,
  effectiveDensityLabel,
  pollingIntervalMs,
  exchangeCredentialStatus,
  exchangeCredentialLoading,
  exchangeCredentialSaving,
  exchangeCredentialVerifying,
  exchangeCredentialError,
  exchangeCredentialNotice,
  exchangeAccessKeyInput,
  exchangeSecretKeyInput,
  setExchangeAccessKeyInput,
  setExchangeSecretKeyInput,
  handleSaveExchangeCredentials,
  handleVerifyExchangeCredentials,
  handleDeleteExchangeCredentials,
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
  fetchPerformance,
  performanceMode,
  setPerformanceMode,
  performanceInputs,
  setPerformanceInputs,
  performanceLoading,
  performanceError,
  performance,
  performanceTotal,
  helpers,
  constants,
}) {
  const {
    formatDateTime,
    formatKRW,
    formatPercent,
    toInputValue,
    pnlClass,
    normalizeRouteToken,
    normalizeTableDensity,
    normalizeProfileValue,
    resolvePresetDisplayName,
    updateMarketOverrideInput,
    removeMarketRow,
    applyRatioPresetToMarket,
    clearMarketRatioOverrides,
  } = helpers

  const {
    defaultMarketMaxOrderKrw,
    defaultMarketProfile,
    dashboardRoute,
    settingsRoute,
    uiScopeDesktop,
    uiScopeMobile,
    uiDensityComfortable,
    uiDensityCompact,
    uiRefreshMinSec,
    uiRefreshMaxSec,
  } = constants

  return (
    <section className="workspace-grid workspace-grid--settings">
      <aside className="workspace-side">
        <article className="control-card card--elevated auth-settings-card">
          <div className="card-head">
            <div>
              <h2>내 인터페이스 설정</h2>
              <p className="sub">로그인 사용자별 기본 리스크 프로필, 관심 마켓, 기기별 화면 옵션을 저장합니다.</p>
            </div>
            <span className="pill">USER</span>
          </div>
          {userSettingsError && <p className="status-error">{userSettingsError}</p>}
          {userSettingsNotice && <p className="status-success">{userSettingsNotice}</p>}
          <div className="form-grid auth-settings-grid">
            <label className="form-field">
              <span>리스크 프로필</span>
              <select
                value={userRiskProfile}
                onChange={(event) => setUserRiskProfile(event.target.value)}
                disabled={settingsLoading || settingsSaving}
              >
                {profileValues.map((profile) => (
                  <option key={profile} value={profile}>
                    {profile}
                  </option>
                ))}
              </select>
            </label>
            <label className="form-field">
              <span>관심 마켓</span>
              <input
                type="text"
                value={userMarketsInput}
                onChange={(event) => setUserMarketsInput(event.target.value)}
                placeholder="예: KRW-BTC, KRW-ETH"
                disabled={settingsLoading || settingsSaving}
              />
            </label>
          </div>
          <div className="ui-pref-block">
            <p className="sub compact">기기별 UI 설정</p>
            <div className="form-grid ui-pref-grid">
              <label className="form-field">
                <span>공통 새로고침 주기 (초)</span>
                <input
                  type="number"
                  min={uiRefreshMinSec}
                  max={uiRefreshMaxSec}
                  value={toInputValue(commonUiPrefs.refreshSec)}
                  onChange={handleRefreshSecChange}
                  disabled={settingsLoading || settingsSaving}
                />
              </label>
              <label className="form-field">
                <span>데스크톱 기본 화면</span>
                <select
                  value={normalizeRouteToken(desktopUiPrefs.defaultRoute, dashboardRoute)}
                  onChange={(event) => handleDefaultRouteChange(uiScopeDesktop, event.target.value)}
                  disabled={settingsLoading || settingsSaving}
                >
                  <option value={dashboardRoute}>실시간 현황</option>
                  <option value={settingsRoute}>매매 세팅</option>
                </select>
              </label>
              <label className="form-field">
                <span>데스크톱 테이블 밀도</span>
                <select
                  value={normalizeTableDensity(desktopUiPrefs.tableDensity, uiDensityComfortable)}
                  onChange={(event) => handleTableDensityChange(uiScopeDesktop, event.target.value)}
                  disabled={settingsLoading || settingsSaving}
                >
                  <option value={uiDensityComfortable}>컴포터블</option>
                  <option value={uiDensityCompact}>컴팩트</option>
                </select>
              </label>
              <label className="form-field">
                <span>모바일 기본 화면</span>
                <select
                  value={normalizeRouteToken(mobileUiPrefs.defaultRoute, dashboardRoute)}
                  onChange={(event) => handleDefaultRouteChange(uiScopeMobile, event.target.value)}
                  disabled={settingsLoading || settingsSaving}
                >
                  <option value={dashboardRoute}>실시간 현황</option>
                  <option value={settingsRoute}>매매 세팅</option>
                </select>
              </label>
              <label className="form-field">
                <span>모바일 테이블 밀도</span>
                <select
                  value={normalizeTableDensity(mobileUiPrefs.tableDensity, uiDensityCompact)}
                  onChange={(event) => handleTableDensityChange(uiScopeMobile, event.target.value)}
                  disabled={settingsLoading || settingsSaving}
                >
                  <option value={uiDensityCompact}>컴팩트</option>
                  <option value={uiDensityComfortable}>컴포터블</option>
                </select>
              </label>
            </div>
            <p className="sub compact">
              현재 접속: {deviceLabel} / 적용 화면: {effectiveRouteLabel} / 적용 밀도: {effectiveDensityLabel} / 자동 갱신: {Math.round(pollingIntervalMs / 1000)}초
            </p>
          </div>
          <p className="sub compact">마켓 코드는 쉼표로 구분해 입력하세요. 형식 예: KRW-BTC</p>
          <div className="button-row">
            <button
              className="primary-button"
              type="button"
              onClick={handleSaveMySettings}
              disabled={settingsLoading || settingsSaving}
            >
              {settingsSaving ? '저장 중...' : '내 설정 저장'}
            </button>
            <button
              className="ghost-button"
              type="button"
              onClick={fetchMySettings}
              disabled={settingsLoading || settingsSaving}
            >
              {settingsLoading ? '불러오는 중...' : '다시 불러오기'}
            </button>
          </div>
          {userSettings?.updatedAt && (
            <p className="sub compact">마지막 저장 {formatDateTime(userSettings.updatedAt)}</p>
          )}
        </article>

        <article className="control-card card--elevated auth-settings-card">
          <div className="card-head">
            <div>
              <h2>거래소 API 키</h2>
              <p className="sub">사용자별 Upbit API 키를 저장/검증합니다.</p>
            </div>
            <span className="pill">
              {exchangeCredentialStatus?.configured
                ? '등록됨'
                : exchangeCredentialStatus?.usingDefaultCredentials
                  ? '기본키 사용'
                  : '미등록'}
            </span>
          </div>
          {exchangeCredentialError && <p className="status-error">{exchangeCredentialError}</p>}
          {exchangeCredentialNotice && <p className="status-success">{exchangeCredentialNotice}</p>}
          <div className="form-grid auth-settings-grid">
            <label className="form-field">
              <span>Access Key</span>
              <input
                type="text"
                value={exchangeAccessKeyInput}
                onChange={(event) => setExchangeAccessKeyInput(event.target.value)}
                placeholder="Upbit Access Key"
                disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
              />
            </label>
            <label className="form-field">
              <span>Secret Key</span>
              <input
                type="password"
                value={exchangeSecretKeyInput}
                onChange={(event) => setExchangeSecretKeyInput(event.target.value)}
                placeholder="Upbit Secret Key"
                disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
              />
            </label>
          </div>
          <div className="button-row">
            <button
              className="primary-button"
              type="button"
              onClick={handleSaveExchangeCredentials}
              disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
            >
              {exchangeCredentialSaving ? '저장 중...' : '키 저장'}
            </button>
            <button
              className="ghost-button"
              type="button"
              onClick={handleVerifyExchangeCredentials}
              disabled={exchangeCredentialLoading || exchangeCredentialSaving || exchangeCredentialVerifying}
            >
              {exchangeCredentialVerifying ? '검증 중...' : '키 검증'}
            </button>
            <button
              className="ghost-button"
              type="button"
              onClick={handleDeleteExchangeCredentials}
              disabled={exchangeCredentialSaving || exchangeCredentialVerifying || !exchangeCredentialStatus?.configured}
            >
              키 삭제
            </button>
          </div>
          {exchangeCredentialStatus?.updatedAt && (
            <p className="sub compact">마지막 저장 {formatDateTime(exchangeCredentialStatus.updatedAt)}</p>
          )}
        </article>

        <article className="control-card card--elevated market-card">
          <div className="card-head">
            <div>
              <h2>마켓별 설정</h2>
              <p className="sub">마켓별 cap/profile 저장 + 행별 토글에서 비율 override 저장을 관리합니다.</p>
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
          <div className="market-add-row">
            <div className="market-add-input-wrap">
              <input
                type="text"
                value={newMarketInput}
                placeholder="코인명/심볼/마켓코드 검색 (예: 이더리움, ETH, KRW-ETH)"
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
              disabled={marketConfigLoading || marketConfigSaving}
            >
              마켓 추가
            </button>
          </div>
          {marketConfigLoading ? (
            <div className="empty-state">마켓 설정을 불러오는 중입니다…</div>
          ) : marketRows.length === 0 ? (
            <div className="empty-state">설정 가능한 마켓이 없습니다.</div>
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
                const effectiveProfileLabel = normalizeProfileValue(row.profile) || defaultMarketProfile
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
                          placeholder={defaultMarketMaxOrderKrw}
                          value={row.maxOrderKrw}
                          onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'maxOrderKrw', event.target.value)}
                        />
                      </label>
                      <label className="market-inline-field">
                        <select
                          value={normalizeProfileValue(row.profile) || defaultMarketProfile}
                          onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'profile', event.target.value)}
                        >
                          {profileValues.map((profile) => (
                            <option key={profile} value={profile}>
                              {profile}
                            </option>
                          ))}
                        </select>
                      </label>
                      <label className="market-toggle-field">
                        <input
                          type="checkbox"
                          checked={Boolean(row.tradePaused)}
                          onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'tradePaused', event.target.checked)}
                        />
                        <span className={row.tradePaused ? 'is-paused' : ''}>
                          {row.tradePaused ? '일시정지' : '매매중'}
                        </span>
                      </label>
                      <button
                        className="market-remove-button"
                        onClick={() => removeMarketRow(
                          setMarketRows,
                          row.market,
                          setMarketConfigNotice,
                          setMarketConfigError,
                          setSelectedRatioPresetByMarket
                        )}
                        disabled={marketConfigSaving}
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
                        <div className="form-grid market-ratio-grid">
                          <label className="form-field">
                            <span>익절 %</span>
                            <input
                              type="number"
                              step="0.1"
                              placeholder={toInputValue(strategy?.takeProfitPct)}
                              value={row.takeProfitPct}
                              onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'takeProfitPct', event.target.value)}
                            />
                          </label>
                          <label className="form-field">
                            <span>손절 %</span>
                            <input
                              type="number"
                              step="0.1"
                              placeholder={toInputValue(strategy?.stopLossPct)}
                              value={row.stopLossPct}
                              onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'stopLossPct', event.target.value)}
                            />
                          </label>
                          <label className="form-field">
                            <span>트레일링 %</span>
                            <input
                              type="number"
                              step="0.1"
                              placeholder={toInputValue(strategy?.trailingStopPct)}
                              value={row.trailingStopPct}
                              onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'trailingStopPct', event.target.value)}
                            />
                          </label>
                          <label className="form-field">
                            <span>부분 익절 %</span>
                            <input
                              type="number"
                              step="1"
                              placeholder={toInputValue(strategy?.partialTakeProfitPct)}
                              value={row.partialTakeProfitPct}
                              onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'partialTakeProfitPct', event.target.value)}
                            />
                          </label>
                          <label className="form-field">
                            <span>손절/트레일링 매도 %</span>
                            <input
                              type="number"
                              step="1"
                              placeholder={toInputValue(strategy?.stopExitPct)}
                              value={row.stopExitPct}
                              onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'stopExitPct', event.target.value)}
                            />
                          </label>
                          <label className="form-field">
                            <span>추세 이탈 매도 %</span>
                            <input
                              type="number"
                              step="1"
                              placeholder={toInputValue(strategy?.trendExitPct)}
                              value={row.trendExitPct}
                              onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'trendExitPct', event.target.value)}
                            />
                          </label>
                          <label className="form-field">
                            <span>모멘텀 역전 매도 %</span>
                            <input
                              type="number"
                              step="1"
                              placeholder={toInputValue(strategy?.momentumExitPct)}
                              value={row.momentumExitPct}
                              onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'momentumExitPct', event.target.value)}
                            />
                          </label>
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
          <p className="sub compact">빈 값은 글로벌 전략 설정값을 사용합니다.</p>
          <div className="button-row">
            <button
              className="primary-button"
              onClick={onSaveMarketOverrides}
              disabled={marketConfigLoading || marketConfigSaving || !marketRowsDirty}
            >
              {marketConfigSaving ? '저장 중...' : marketRowsDirty ? '마켓 설정 저장' : '변경사항 없음'}
            </button>
            <button
              className="ghost-button"
              onClick={() => handleMarketReload()}
              disabled={marketConfigSaving}
            >
              다시 불러오기
            </button>
          </div>
        </article>

        <article className="control-card card--elevated performance-card">
          <div className="card-head">
            <div>
              <h2>기간 수익 분석</h2>
              <p className="sub">직접 기간/연도/월 기준 추정 실현손익을 조회합니다.</p>
            </div>
            <span className="pill">ESTIMATED</span>
          </div>

          <div className="mode-row">
            <button
              className={`ghost-button ${performanceMode === 'range' ? 'active' : ''}`}
              onClick={() => setPerformanceMode('range')}
            >
              직접 기간
            </button>
            <button
              className={`ghost-button ${performanceMode === 'year' ? 'active' : ''}`}
              onClick={() => setPerformanceMode('year')}
            >
              연도별
            </button>
            <button
              className={`ghost-button ${performanceMode === 'month' ? 'active' : ''}`}
              onClick={() => setPerformanceMode('month')}
            >
              월별
            </button>
          </div>

          {performanceMode === 'range' ? (
            <div className="filter-row">
              <label className="form-field">
                <span>시작일</span>
                <input
                  type="date"
                  value={performanceInputs.from}
                  onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, from: event.target.value }))}
                />
              </label>
              <label className="form-field">
                <span>종료일</span>
                <input
                  type="date"
                  value={performanceInputs.to}
                  onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, to: event.target.value }))}
                />
              </label>
            </div>
          ) : performanceMode === 'year' ? (
            <div className="filter-row filter-row--single">
              <label className="form-field">
                <span>연도</span>
                <input
                  type="number"
                  min="2009"
                  max="2100"
                  value={performanceInputs.year}
                  onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, year: event.target.value }))}
                />
              </label>
            </div>
          ) : (
            <div className="filter-row">
              <label className="form-field">
                <span>연도</span>
                <input
                  type="number"
                  min="2009"
                  max="2100"
                  value={performanceInputs.year}
                  onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, year: event.target.value }))}
                />
              </label>
              <label className="form-field">
                <span>월</span>
                <input
                  type="number"
                  min="1"
                  max="12"
                  value={performanceInputs.month}
                  onChange={(event) => setPerformanceInputs((prev) => ({ ...prev, month: event.target.value }))}
                />
              </label>
            </div>
          )}

          <div className="button-row">
            <button
              className="primary-button"
              onClick={() => fetchPerformance()}
              disabled={performanceLoading}
            >
              {performanceLoading ? '조회 중...' : '수익 조회'}
            </button>
          </div>

          {performanceError && <p className="status-error">{performanceError}</p>}

          {performance && (
            <>
              <p className="sub compact">
                조회 구간 {performance.from} ~ {performance.to} ({performance.timezone})
              </p>
              <div className="performance-summary-grid">
                <div className="performance-mini">
                  <span>실현손익</span>
                  <strong className={`mono ${pnlClass(performanceTotal?.estimatedRealizedPnlKrw)}`}>
                    {formatKRW(performanceTotal?.estimatedRealizedPnlKrw)} KRW
                  </strong>
                </div>
                <div className="performance-mini">
                  <span>순현금흐름</span>
                  <strong className={`mono ${pnlClass(performanceTotal?.netCashFlowKrw)}`}>
                    {formatKRW(performanceTotal?.netCashFlowKrw)} KRW
                  </strong>
                </div>
                <div className="performance-mini">
                  <span>매수/매도</span>
                  <strong className="mono">
                    {formatKRW(performanceTotal?.buyNotionalKrw)} / {formatKRW(performanceTotal?.sellNotionalKrw)}
                  </strong>
                </div>
                <div className="performance-mini">
                  <span>매도 승률</span>
                  <strong className="mono">{formatPercent(performanceTotal?.sellWinRate)}</strong>
                </div>
              </div>

              <div className="performance-table-grid">
                <div className="table-wrapper">
                  <table>
                    <thead>
                      <tr>
                        <th>연도</th>
                        <th>실현손익</th>
                        <th>순현금흐름</th>
                        <th>승률</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(performance.yearly ?? []).length === 0 ? (
                        <tr>
                          <td colSpan={4} className="empty-cell">연도 데이터 없음</td>
                        </tr>
                      ) : (performance.yearly ?? []).map((row) => (
                        <tr key={`year-${row.period}`}>
                          <td className="mono">{row.period}</td>
                          <td className={`mono ${pnlClass(row.estimatedRealizedPnlKrw)}`}>{formatKRW(row.estimatedRealizedPnlKrw)}</td>
                          <td className={`mono ${pnlClass(row.netCashFlowKrw)}`}>{formatKRW(row.netCashFlowKrw)}</td>
                          <td className="mono">{formatPercent(row.sellWinRate)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="table-wrapper">
                  <table>
                    <thead>
                      <tr>
                        <th>월</th>
                        <th>실현손익</th>
                        <th>순현금흐름</th>
                        <th>승률</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(performance.monthly ?? []).length === 0 ? (
                        <tr>
                          <td colSpan={4} className="empty-cell">월 데이터 없음</td>
                        </tr>
                      ) : (performance.monthly ?? []).map((row) => (
                        <tr key={`month-${row.period}`}>
                          <td className="mono">{row.period}</td>
                          <td className={`mono ${pnlClass(row.estimatedRealizedPnlKrw)}`}>{formatKRW(row.estimatedRealizedPnlKrw)}</td>
                          <td className={`mono ${pnlClass(row.netCashFlowKrw)}`}>{formatKRW(row.netCashFlowKrw)}</td>
                          <td className="mono">{formatPercent(row.sellWinRate)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </>
          )}
        </article>
      </aside>
    </section>
  )
}

export default SettingsRoute
