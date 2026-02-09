import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import './App.css'

const PROFILE_VALUES = ['AGGRESSIVE', 'BALANCED', 'CONSERVATIVE']
const MARKET_CODE_PATTERN = /^[A-Z]{2,10}-[A-Z0-9]{2,15}$/

function App() {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [serverConnected, setServerConnected] = useState(null)

  const [engineStatus, setEngineStatus] = useState(null)
  const [engineBusy, setEngineBusy] = useState(false)
  const [engineError, setEngineError] = useState(null)

  const [strategy, setStrategy] = useState(null)
  const [strategyError, setStrategyError] = useState(null)
  const [ratioInputs, setRatioInputs] = useState({
    takeProfitPct: '',
    stopLossPct: '',
    trailingStopPct: '',
    partialTakeProfitPct: '',
    stopExitPct: '',
    trendExitPct: '',
    momentumExitPct: '',
  })
  const [ratioSaving, setRatioSaving] = useState(false)
  const [ratioError, setRatioError] = useState(null)
  const [presetError, setPresetError] = useState(null)
  const [ratioPresets, setRatioPresets] = useState([])
  const [selectedRatioPreset, setSelectedRatioPreset] = useState(null)
  const [marketRows, setMarketRows] = useState([])
  const [marketConfigSaving, setMarketConfigSaving] = useState(false)
  const [marketConfigLoading, setMarketConfigLoading] = useState(false)
  const [marketConfigError, setMarketConfigError] = useState(null)
  const [marketConfigNotice, setMarketConfigNotice] = useState(null)
  const [marketRowsBaseline, setMarketRowsBaseline] = useState('')
  const [newMarketInput, setNewMarketInput] = useState('')

  const [orderHistory, setOrderHistory] = useState([])
  const [decisionHistory, setDecisionHistory] = useState([])
  const [feedError, setFeedError] = useState(null)
  const [alerts, setAlerts] = useState([])
  const lastOrderIdRef = useRef(null)
  const lastDecisionIdRef = useRef(null)

  const fetchSummary = useCallback(async (isRefresh = false) => {
    if (!isRefresh) {
      setLoading(true)
    }
    try {
      const response = await fetch('/api/portfolio/summary')
      if (!response.ok) {
        throw new Error(`서버 응답 ${response.status}`)
      }
      const data = await response.json()
      setSummary(data)
      setServerConnected(true)
    } catch (err) {
      setServerConnected(false)
    } finally {
      if (!isRefresh) {
        setLoading(false)
      }
    }
  }, [])

  const fetchEngineStatus = useCallback(async () => {
    try {
      const response = await fetch('/api/engine/status')
      if (!response.ok) {
        throw new Error(`엔진 상태 오류 ${response.status}`)
      }
      const data = await response.json()
      setEngineStatus(Boolean(data?.running))
    } catch (err) {
      setEngineError(err?.message ?? '엔진 상태 조회 실패')
    }
  }, [])

  const fetchStrategy = useCallback(async () => {
    setStrategyError(null)
    try {
      const response = await fetch('/api/strategy')
      if (!response.ok) {
        throw new Error(`전략 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setStrategy(data)
      setRatioInputs({
        takeProfitPct: toInputValue(data?.takeProfitPct),
        stopLossPct: toInputValue(data?.stopLossPct),
        trailingStopPct: toInputValue(data?.trailingStopPct),
        partialTakeProfitPct: toInputValue(data?.partialTakeProfitPct),
        stopExitPct: toInputValue(data?.stopExitPct),
        trendExitPct: toInputValue(data?.trendExitPct),
        momentumExitPct: toInputValue(data?.momentumExitPct),
      })
      setSelectedRatioPreset(null)
    } catch (err) {
      setStrategyError(err?.message ?? '전략 조회 실패')
    }
  }, [])

  const fetchRatioPresets = useCallback(async () => {
    setPresetError(null)
    try {
      const response = await fetch('/api/strategy/presets')
      if (!response.ok) {
        throw new Error(`프리셋 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setRatioPresets(normalizeRatioPresets(data))
    } catch (err) {
      setRatioPresets([])
      setPresetError(err?.message ?? '프리셋 조회 실패')
    }
  }, [])

  const fetchOrderHistory = useCallback(async () => {
    try {
      const response = await fetch('/api/order/history?limit=30')
      if (!response.ok) {
        throw new Error(`주문 로그 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setOrderHistory(Array.isArray(data) ? data : [])
      appendOrderAlerts(data, lastOrderIdRef, setAlerts)
      setFeedError(null)
    } catch (err) {
      setFeedError(err?.message ?? '주문 로그 조회 실패')
    }
  }, [])

  const fetchDecisionHistory = useCallback(async () => {
    try {
      const response = await fetch('/api/engine/decisions?limit=30&includeSkips=false')
      if (!response.ok) {
        throw new Error(`의사결정 로그 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      const allItems = Array.isArray(data) ? data : []
      const tradeOnlyItems = allItems.filter((decision) => {
        const action = String(decision?.action ?? '').toUpperCase()
        return action === 'BUY' || action === 'SELL'
      })
      setDecisionHistory(tradeOnlyItems)
      appendDecisionAlerts(tradeOnlyItems, lastDecisionIdRef, setAlerts)
      setFeedError(null)
    } catch (err) {
      setFeedError(err?.message ?? '의사결정 로그 조회 실패')
    }
  }, [])

  const fetchMarketOverrides = useCallback(async () => {
    setMarketConfigLoading(true)
    setMarketConfigError(null)
    setMarketConfigNotice(null)
    try {
      const response = await fetch('/api/strategy/market-overrides')
      if (!response.ok) {
        throw new Error(`마켓 설정 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      const rows = buildMarketOverrideRows(data)
      setMarketRows(rows)
      setMarketRowsBaseline(buildMarketOverrideSignature(rows))
      setNewMarketInput('')
    } catch (err) {
      setMarketConfigError(err?.message ?? '마켓 설정 조회 실패')
    } finally {
      setMarketConfigLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchSummary(false)
    fetchEngineStatus()
    fetchStrategy()
    fetchRatioPresets()
    fetchMarketOverrides()
    fetchOrderHistory()
    fetchDecisionHistory()

    const summaryTimer = setInterval(() => fetchSummary(true), 2000)
    const engineTimer = setInterval(() => fetchEngineStatus(), 2000)
    const feedTimer = setInterval(() => {
      fetchOrderHistory()
      fetchDecisionHistory()
    }, 3000)

    return () => {
      clearInterval(summaryTimer)
      clearInterval(engineTimer)
      clearInterval(feedTimer)
    }
  }, [fetchSummary, fetchEngineStatus, fetchStrategy, fetchRatioPresets, fetchMarketOverrides, fetchOrderHistory, fetchDecisionHistory])

  const positions = useMemo(() => {
    if (!summary?.positions) {
      return []
    }
    return [...summary.positions].sort((a, b) => (b?.valuation ?? 0) - (a?.valuation ?? 0))
  }, [summary])

  const cash = summary?.cash
  const totals = summary?.totals
  const updatedAt = summary?.queriedAt
    ? new Date(summary.queriedAt).toLocaleString('ko-KR', { hour12: false })
    : '—'

  const connectionClass = serverConnected === null ? 'checking' : serverConnected ? 'connected' : 'disconnected'
  const connectionLabel = serverConnected === null ? '확인중' : serverConnected ? '연결됨' : '끊김'
  const engineLabel = engineStatus ? 'ON' : 'OFF'
  const engineClass = engineStatus ? 'ok' : 'error'
  const profileLabel = strategy?.profile ?? '—'
  const selectedPresetLabel = useMemo(() => {
    if (!selectedRatioPreset) {
      return null
    }
    const found = ratioPresets.find((preset) => preset.code === selectedRatioPreset)
    return found?.displayName ?? selectedRatioPreset
  }, [ratioPresets, selectedRatioPreset])
  const marketRowsDirty = useMemo(
    () => buildMarketOverrideSignature(marketRows) !== marketRowsBaseline,
    [marketRows, marketRowsBaseline]
  )

  const handleMarketReload = useCallback(() => {
    if (marketRowsDirty && !window.confirm('저장하지 않은 변경사항이 있습니다. 서버 설정으로 덮어쓸까요?')) {
      return
    }
    fetchMarketOverrides()
  }, [fetchMarketOverrides, marketRowsDirty])

  useEffect(() => {
    if (marketRowsDirty && marketConfigNotice) {
      setMarketConfigNotice(null)
    }
  }, [marketRowsDirty, marketConfigNotice])

  return (
    <div className="app">
      <header className="app__header">
        <div className="brand-block">
          <p className="eyebrow">BTC AUTO TRADER</p>
          <h1>Sundal</h1>
          <p className="sub">Work Day & Night for your financial free life</p>
        </div>
        <div className="engine-inline-card">
          <div className="engine-inline-head">
            <strong>자동매매</strong>
            <span className={`status ${engineClass}`}>ENGINE {engineLabel}</span>
          </div>
          {engineError && <p className="status-error">{engineError}</p>}
          <div className="engine-inline-actions">
            <button
              className="primary-button"
              onClick={() => handleEngineStart(setEngineStatus, setEngineError, setEngineBusy)}
              disabled={engineBusy || engineStatus}
            >
              엔진 시작
            </button>
            <button
              className="danger-button"
              onClick={() => handleEngineStop(setEngineStatus, setEngineError, setEngineBusy)}
              disabled={engineBusy || !engineStatus}
            >
              엔진 중지
            </button>
          </div>
        </div>
        <div className="status-card">
          <div className="status-row">
            <span>업데이트</span>
            <strong className="mono">{updatedAt}</strong>
          </div>
          <div className="status-connection-row">
            <span>서버 연결</span>
            <span className={`connection-badge ${connectionClass}`}>{connectionLabel}</span>
          </div>
        </div>
      </header>

      <section className="summary-grid">
        <div className="summary-card">
          <h3>보유 KRW</h3>
          <p className="summary-value mono">
            {formatKRW(cash?.total)} <span>KRW</span>
          </p>
          <p className="summary-sub">
            사용 가능 {formatKRW(cash?.balance)} / 예약 {formatKRW(cash?.locked)}
          </p>
        </div>
        <div className="summary-card">
          <h3>코인 평가금액</h3>
          <p className="summary-value mono">
            {formatKRW(totals?.positionValue)} <span>KRW</span>
          </p>
          <p className="summary-sub">매입 {formatKRW(totals?.positionCost)}</p>
        </div>
        <div className="summary-card">
          <h3>포지션 수익</h3>
          <p className={`summary-value mono ${pnlClass(totals?.positionPnl)}`}>
            {formatKRW(totals?.positionPnl)} <span>KRW</span>
          </p>
          <p className="summary-sub">{formatPercent(totals?.positionPnlRate)}</p>
        </div>
        <div className="summary-card">
          <h3>총 자산</h3>
          <p className="summary-value mono">
            {formatKRW(totals?.totalAsset)} <span>KRW</span>
          </p>
          <p className="summary-sub">현금 + 코인 평가 합계</p>
        </div>
      </section>

      <section className="workspace-grid">
        <div className="workspace-main">
          <section className="table-card table-card--elevated positions-card">
            <div className="table-header">
              <div>
                <h2>보유 코인</h2>
                <p className="sub">현재가 기준 평가와 수익률을 표시합니다.</p>
              </div>
            </div>
            {loading ? (
              <div className="empty-state">데이터를 불러오는 중입니다…</div>
            ) : positions.length === 0 ? (
              <div className="empty-state">보유 중인 코인이 없습니다.</div>
            ) : (
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>마켓</th>
                      <th>보유수량</th>
                      <th>평균 매수</th>
                      <th>현재가</th>
                      <th>평가금액</th>
                      <th>수익</th>
                      <th>수익률</th>
                    </tr>
                  </thead>
                  <tbody>
                    {positions.map((position) => (
                      <tr key={position.market}>
                        <td>
                          <div className="market">
                            <span className="market__coin">{position.currency}</span>
                            <span className="market__pair">{position.market}</span>
                          </div>
                        </td>
                        <td className="mono">{formatCoin(position.quantity)}</td>
                        <td className="mono">{formatKRW(position.avgBuyPrice)}</td>
                        <td className="mono">{formatKRW(position.currentPrice)}</td>
                        <td className="mono">{formatKRW(position.valuation)}</td>
                        <td className={`mono ${pnlClass(position.pnl)}`}>{formatKRW(position.pnl)}</td>
                        <td className={`mono ${pnlClass(position.pnl)}`}>{formatPercent(position.pnlRate)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="table-card table-card--elevated decision-card">
            <div className="table-header">
              <div>
                <h2>매매 사유 스냅샷</h2>
                <p className="sub">매수/매도 이유와 당시 지표</p>
              </div>
            </div>
            {decisionHistory.length === 0 ? (
              <div className="empty-state">매수/매도 의사결정 로그가 없습니다.</div>
            ) : (
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>시간</th>
                      <th>마켓</th>
                      <th>액션</th>
                      <th>사유</th>
                      <th>RSI</th>
                      <th>MACD</th>
                      <th>MA Slope%</th>
                      <th>가격</th>
                      <th>프로필</th>
                    </tr>
                  </thead>
                  <tbody>
                    {decisionHistory.map((decision) => (
                      <tr key={decision.id}>
                        <td className="mono small">{formatDateTime(decision.executedAt)}</td>
                        <td>{decision.market}</td>
                        <td className={decision.action === 'BUY' ? 'positive' : decision.action === 'SELL' ? 'negative' : 'neutral'}>
                          {decision.action}
                        </td>
                        <td className="mono small">{decision.reason ?? '-'}</td>
                        <td className="mono">{formatFixed(decision.rsi, 2)}</td>
                        <td className="mono">{formatFixed(decision.macdHistogram, 4)}</td>
                        <td className="mono">{formatFixed(decision.maLongSlopePct, 3)}</td>
                        <td className="mono">{formatKRW(decision.price)}</td>
                        <td>{decision.profile ?? '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <article className="table-card card--elevated feed-card">
            <div className="table-header">
              <div>
                <h2>실시간 알림</h2>
                <p className="sub">최근 체결/의사결정 이벤트</p>
              </div>
            </div>
            {alerts.length === 0 ? (
              <div className="empty-state">새 알림이 없습니다.</div>
            ) : (
              <ul className="alert-list">
                {alerts.map((alert) => (
                  <li key={alert.id} className={`alert-item ${alert.tone}`}>
                    <div>
                      <strong>{alert.message}</strong>
                      <p className="sub compact">{alert.meta}</p>
                    </div>
                    <span className="mono small">{formatTime(alert.time)}</span>
                  </li>
                ))}
              </ul>
            )}
          </article>

          <article className="table-card card--elevated order-card">
            <div className="table-header">
              <div>
                <h2>최근 주문 로그</h2>
                <p className="sub">BUY/SELL 요청 상태와 오류 추적</p>
              </div>
            </div>
            {feedError && <p className="status-error">{feedError}</p>}
            {orderHistory.length === 0 ? (
              <div className="empty-state">주문 로그가 없습니다.</div>
            ) : (
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>시간</th>
                      <th>마켓</th>
                      <th>사이드</th>
                      <th>상태</th>
                      <th>주문량</th>
                      <th>주문금액</th>
                      <th>오류</th>
                    </tr>
                  </thead>
                  <tbody>
                    {orderHistory.map((order) => (
                      <tr key={order.id}>
                        <td className="mono small">{formatDateTime(order.requestedAt)}</td>
                        <td>{order.market}</td>
                        <td className={order.side === 'BUY' ? 'positive' : 'negative'}>{order.side}</td>
                        <td>
                          <span className="mono">{order.requestStatus ?? '-'}</span>
                          <span className="sub compact">{order.state ?? '-'}</span>
                        </td>
                        <td className="mono">{formatCoin(order.volume)}</td>
                        <td className="mono">{formatKRW(order.funds)}</td>
                        <td className="mono small">{truncateText(order.errorMessage, 36)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </article>
        </div>

        <aside className="workspace-side">
          <section className="control-grid">
            <div className="control-card card--elevated strategy-card">
              <div className="card-head">
                <div>
                  <h2>비율 설정</h2>
                  <p className="sub">익절/손절/부분 매도 비율을 조정합니다.</p>
                </div>
                <span className="pill">PROFILE {profileLabel}</span>
              </div>
              {strategyError && <p className="status-error">{strategyError}</p>}
              {ratioError && <p className="status-error">{ratioError}</p>}
              {presetError && <p className="status-error">{presetError}</p>}
              <div className="preset-row">
                {ratioPresets.length === 0 ? (
                  <p className="sub compact">등록된 프리셋이 없습니다.</p>
                ) : ratioPresets.map((preset) => (
                  <button
                    key={preset.code}
                    className={`ghost-button ${selectedRatioPreset === preset.code ? 'active' : ''}`}
                    onClick={() => applyRatioPreset(preset, setRatioInputs, setSelectedRatioPreset, setRatioError)}
                  >
                    {preset.displayName} 비율 적용
                  </button>
                ))}
              </div>
              {selectedPresetLabel && (
                <p className="sub compact">{selectedPresetLabel} 프리셋이 입력값에만 적용되었습니다. 저장 버튼을 눌러야 서버 반영됩니다.</p>
              )}
              <div className="form-grid">
                <label className="form-field">
                  <span>익절 %</span>
                  <input
                    type="number"
                    step="0.1"
                    value={ratioInputs.takeProfitPct}
                    onChange={(event) => updateRatioInput(setRatioInputs, 'takeProfitPct', event.target.value)}
                  />
                </label>
                <label className="form-field">
                  <span>손절 %</span>
                  <input
                    type="number"
                    step="0.1"
                    value={ratioInputs.stopLossPct}
                    onChange={(event) => updateRatioInput(setRatioInputs, 'stopLossPct', event.target.value)}
                  />
                </label>
                <label className="form-field">
                  <span>트레일링 %</span>
                  <input
                    type="number"
                    step="0.1"
                    value={ratioInputs.trailingStopPct}
                    onChange={(event) => updateRatioInput(setRatioInputs, 'trailingStopPct', event.target.value)}
                  />
                </label>
                <label className="form-field">
                  <span>부분 익절 %</span>
                  <input
                    type="number"
                    step="1"
                    value={ratioInputs.partialTakeProfitPct}
                    onChange={(event) => updateRatioInput(setRatioInputs, 'partialTakeProfitPct', event.target.value)}
                  />
                </label>
                <label className="form-field">
                  <span>손절/트레일링 매도 %</span>
                  <input
                    type="number"
                    step="1"
                    value={ratioInputs.stopExitPct}
                    onChange={(event) => updateRatioInput(setRatioInputs, 'stopExitPct', event.target.value)}
                  />
                </label>
                <label className="form-field">
                  <span>추세 이탈 매도 %</span>
                  <input
                    type="number"
                    step="1"
                    value={ratioInputs.trendExitPct}
                    onChange={(event) => updateRatioInput(setRatioInputs, 'trendExitPct', event.target.value)}
                  />
                </label>
                <label className="form-field">
                  <span>모멘텀 역전 매도 %</span>
                  <input
                    type="number"
                    step="1"
                    value={ratioInputs.momentumExitPct}
                    onChange={(event) => updateRatioInput(setRatioInputs, 'momentumExitPct', event.target.value)}
                  />
                </label>
              </div>
              <div className="button-row">
                <button
                  className="primary-button"
                  onClick={() => handleRatioSave(
                    ratioInputs,
                    setRatioSaving,
                    setRatioError,
                    setStrategy,
                    setRatioInputs,
                    setSelectedRatioPreset
                  )}
                  disabled={ratioSaving}
                >
                  비율 저장
                </button>
                <button className="ghost-button" onClick={() => fetchStrategy()}>
                  새로고침
                </button>
              </div>
            </div>
          </section>

          <article className="control-card card--elevated market-card">
            <div className="card-head">
              <div>
                <h2>마켓별 설정</h2>
                <p className="sub">자동매매 대상 마켓과 종목별 cap/profile을 저장합니다.</p>
              </div>
              <span className={`pill ${marketRowsDirty ? 'pill-warning' : ''}`}>
                {marketRowsDirty ? '변경 있음' : '저장됨'}
              </span>
            </div>
            {marketConfigError && <p className="status-error">{marketConfigError}</p>}
            {marketConfigNotice && <p className="status-success">{marketConfigNotice}</p>}
            <div className="market-add-row">
              <input
                type="text"
                value={newMarketInput}
                placeholder="예: KRW-ETH"
                onChange={(event) => setNewMarketInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    addMarketRow(
                      newMarketInput,
                      marketRows,
                      setNewMarketInput,
                      setMarketRows,
                      setMarketConfigError,
                      setMarketConfigNotice
                    )
                  }
                }}
              />
              <button
                className="ghost-button"
                onClick={() => addMarketRow(
                  newMarketInput,
                  marketRows,
                  setNewMarketInput,
                  setMarketRows,
                  setMarketConfigError,
                  setMarketConfigNotice
                )}
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
                {marketRows.map((row) => (
                  <div className="market-override-row" key={row.market}>
                    <div className="market-override-title">
                      <strong>{row.market}</strong>
                      <button
                        className="market-remove-button"
                        onClick={() => removeMarketRow(setMarketRows, row.market, setMarketConfigNotice, setMarketConfigError)}
                        disabled={marketConfigSaving}
                      >
                        제거
                      </button>
                    </div>
                    <label className="form-field">
                      <span>최대 매수 KRW</span>
                      <input
                        type="number"
                        step="1000"
                        min="0"
                        placeholder="기본값 사용"
                        value={row.maxOrderKrw}
                        onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'maxOrderKrw', event.target.value)}
                      />
                    </label>
                    <label className="form-field">
                      <span>프로필</span>
                      <select
                        value={row.profile}
                        onChange={(event) => updateMarketOverrideInput(setMarketRows, row.market, 'profile', event.target.value)}
                      >
                        <option value="">기본값</option>
                        <option value="AGGRESSIVE">AGGRESSIVE</option>
                        <option value="BALANCED">BALANCED</option>
                        <option value="CONSERVATIVE">CONSERVATIVE</option>
                      </select>
                    </label>
                  </div>
                ))}
              </div>
            )}
            <p className="sub compact">빈 값은 글로벌 전략 설정값을 사용합니다.</p>
            <div className="button-row">
              <button
                className="primary-button"
                onClick={() => handleMarketOverridesSave(
                  marketRows,
                  setMarketConfigSaving,
                  setMarketConfigError,
                  setMarketConfigNotice,
                  setMarketRows,
                  setMarketRowsBaseline
                )}
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

        </aside>
      </section>
    </div>
  )
}

const appendOrderAlerts = (orders, lastOrderIdRef, setAlerts) => {
  if (!Array.isArray(orders) || orders.length === 0) {
    return
  }

  const newestId = toNumber(orders[0]?.id)
  if (newestId === null) {
    return
  }

  if (lastOrderIdRef.current === null) {
    lastOrderIdRef.current = newestId
    return
  }

  const baseline = lastOrderIdRef.current
  const newOrders = orders
    .filter((order) => {
      const id = toNumber(order?.id)
      return id !== null && id > baseline && (order?.side === 'BUY' || order?.side === 'SELL')
    })
    .sort((a, b) => a.id - b.id)

  if (newOrders.length === 0) {
    if (newestId > baseline) {
      lastOrderIdRef.current = newestId
    }
    return
  }

  newOrders.forEach((order) => {
    const tone = order.side === 'BUY' ? 'positive' : 'negative'
    const message = `주문 ${order.side} ${order.market}`
    const meta = `${order.requestStatus ?? '-'} / ${order.state ?? '-'} / ${formatDateTime(order.requestedAt)}`
    pushAlert(setAlerts, message, meta, tone)
  })
  lastOrderIdRef.current = newestId
}

const appendDecisionAlerts = (decisions, lastDecisionIdRef, setAlerts) => {
  if (!Array.isArray(decisions) || decisions.length === 0) {
    return
  }

  const newestId = toNumber(decisions[0]?.id)
  if (newestId === null) {
    return
  }

  if (lastDecisionIdRef.current === null) {
    lastDecisionIdRef.current = newestId
    return
  }

  const baseline = lastDecisionIdRef.current
  const newDecisions = decisions
    .filter((decision) => {
      const id = toNumber(decision?.id)
      return id !== null && id > baseline && (decision?.action === 'BUY' || decision?.action === 'SELL')
    })
    .sort((a, b) => a.id - b.id)

  if (newDecisions.length === 0) {
    if (newestId > baseline) {
      lastDecisionIdRef.current = newestId
    }
    return
  }

  newDecisions.forEach((decision) => {
    const tone = decision.action === 'BUY' ? 'positive' : 'negative'
    const message = `신호 ${decision.action} ${decision.market}`
    const meta = `${decision.reason ?? '-'} / ${formatDateTime(decision.executedAt)}`
    pushAlert(setAlerts, message, meta, tone)
  })
  lastDecisionIdRef.current = newestId
}

const pushAlert = (setAlerts, message, meta, tone) => {
  setAlerts((prev) => {
    const next = [{ id: `${Date.now()}-${Math.random()}`, message, meta, tone, time: new Date().toISOString() }, ...prev]
    return next.slice(0, 12)
  })
}

const handleEngineStart = async (setEngineStatus, setEngineError, setEngineBusy) => {
  if (!window.confirm('자동매매 엔진을 시작할까요? 실제 주문이 발생할 수 있습니다.')) {
    return
  }
  setEngineBusy(true)
  setEngineError(null)
  try {
    const response = await fetch('/api/engine/start', { method: 'POST' })
    if (!response.ok) {
      throw new Error(`엔진 시작 실패 ${response.status}`)
    }
    const data = await response.json()
    setEngineStatus(Boolean(data?.running))
  } catch (err) {
    setEngineError(err?.message ?? '엔진 시작 실패')
  } finally {
    setEngineBusy(false)
  }
}

const handleEngineStop = async (setEngineStatus, setEngineError, setEngineBusy) => {
  setEngineBusy(true)
  setEngineError(null)
  try {
    const response = await fetch('/api/engine/stop', { method: 'POST' })
    if (!response.ok) {
      throw new Error(`엔진 중지 실패 ${response.status}`)
    }
    const data = await response.json()
    setEngineStatus(Boolean(data?.running))
  } catch (err) {
    setEngineError(err?.message ?? '엔진 중지 실패')
  } finally {
    setEngineBusy(false)
  }
}

const handleRatioSave = async (
  inputs,
  setRatioSaving,
  setRatioError,
  setStrategy,
  setRatioInputs,
  setSelectedRatioPreset
) => {
  setRatioSaving(true)
  setRatioError(null)
  try {
    const payload = buildRatioPayload(inputs)
    const response = await fetch('/api/strategy/ratios', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    if (!response.ok) {
      const errorPayload = await response.json().catch(() => null)
      const message = errorPayload?.error ? `${errorPayload.error}` : `저장 실패 ${response.status}`
      throw new Error(message)
    }
    const data = await response.json()
    setStrategy(data)
    setRatioInputs({
      takeProfitPct: toInputValue(data?.takeProfitPct),
      stopLossPct: toInputValue(data?.stopLossPct),
      trailingStopPct: toInputValue(data?.trailingStopPct),
      partialTakeProfitPct: toInputValue(data?.partialTakeProfitPct),
      stopExitPct: toInputValue(data?.stopExitPct),
      trendExitPct: toInputValue(data?.trendExitPct),
      momentumExitPct: toInputValue(data?.momentumExitPct),
    })
    setSelectedRatioPreset(null)
  } catch (err) {
    setRatioError(err?.message ?? '비율 저장 실패')
  } finally {
    setRatioSaving(false)
  }
}

const handleMarketOverridesSave = async (
  rows,
  setMarketConfigSaving,
  setMarketConfigError,
  setMarketConfigNotice,
  setMarketRows,
  setMarketRowsBaseline
) => {
  setMarketConfigSaving(true)
  setMarketConfigError(null)
  setMarketConfigNotice(null)
  try {
    const marketsPayload = buildMarketListPayload(rows)
    const marketResponse = await fetch('/api/strategy/markets', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(marketsPayload),
    })
    if (!marketResponse.ok) {
      const errorPayload = await marketResponse.json().catch(() => null)
      const message = buildApiErrorMessage(errorPayload, `마켓 저장 실패 ${marketResponse.status}`)
      throw new Error(message)
    }

    const payload = buildMarketOverridePayload(rows)
    const response = await fetch('/api/strategy/market-overrides', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    if (!response.ok) {
      const errorPayload = await response.json().catch(() => null)
      const message = buildApiErrorMessage(errorPayload, `저장 실패 ${response.status}`)
      throw new Error(message)
    }
    const data = await response.json()
    const nextRows = buildMarketOverrideRows(data)
    setMarketRows(nextRows)
    setMarketRowsBaseline(buildMarketOverrideSignature(nextRows))
    setMarketConfigNotice('마켓/설정이 저장되었습니다.')
  } catch (err) {
    setMarketConfigError(err?.message ?? '마켓 설정 저장 실패')
  } finally {
    setMarketConfigSaving(false)
  }
}

const applyRatioPreset = (preset, setRatioInputs, setSelectedRatioPreset, setRatioError) => {
  if (!preset || !preset.code) {
    return
  }
  setRatioInputs({
    takeProfitPct: toInputValue(preset.takeProfitPct),
    stopLossPct: toInputValue(preset.stopLossPct),
    trailingStopPct: toInputValue(preset.trailingStopPct),
    partialTakeProfitPct: toInputValue(preset.partialTakeProfitPct),
    stopExitPct: toInputValue(preset.stopExitPct),
    trendExitPct: toInputValue(preset.trendExitPct),
    momentumExitPct: toInputValue(preset.momentumExitPct),
  })
  setSelectedRatioPreset(preset.code)
  setRatioError(null)
}

const buildRatioPayload = (inputs) => {
  const payload = {}
  const fields = [
    'takeProfitPct',
    'stopLossPct',
    'trailingStopPct',
    'partialTakeProfitPct',
    'stopExitPct',
    'trendExitPct',
    'momentumExitPct',
  ]
  fields.forEach((field) => {
    const raw = inputs[field]
    if (raw === '' || raw === null || raw === undefined) {
      return
    }
    const value = Number(raw)
    if (Number.isNaN(value) || value < 0 || value > 100) {
      throw new Error(`${field} 값은 0~100 사이여야 합니다.`)
    }
    payload[field] = value
  })
  return payload
}

const updateRatioInput = (setRatioInputs, field, value) => {
  setRatioInputs((prev) => ({
    ...prev,
    [field]: value,
  }))
}

const updateMarketOverrideInput = (setMarketRows, market, field, value) => {
  setMarketRows((prev) => prev.map((row) => {
    if (row.market !== market) {
      return row
    }
    return {
      ...row,
      [field]: value,
    }
  }))
}

const addMarketRow = (
  input,
  rows,
  setNewMarketInput,
  setMarketRows,
  setMarketConfigError,
  setMarketConfigNotice
) => {
  const market = normalizeMarket(input)
  if (!market) {
    setMarketConfigError('마켓 코드를 입력해주세요. 예: KRW-ETH')
    return
  }
  if (!isValidMarketCode(market)) {
    setMarketConfigError('마켓 코드 형식이 올바르지 않습니다. 예: KRW-BTC')
    return
  }
  if (Array.isArray(rows) && rows.some((row) => normalizeMarket(row?.market) === market)) {
    setMarketConfigError(`${market} 는 이미 추가되어 있습니다.`)
    return
  }

  setMarketRows((prev) => [...prev, { market, maxOrderKrw: '', profile: '' }])
  setNewMarketInput('')
  setMarketConfigError(null)
  setMarketConfigNotice(null)
}

const removeMarketRow = (setMarketRows, market, setMarketConfigNotice, setMarketConfigError) => {
  const normalized = normalizeMarket(market)
  if (!normalized) {
    return
  }
  setMarketRows((prev) => prev.filter((row) => normalizeMarket(row?.market) !== normalized))
  setMarketConfigNotice(null)
  setMarketConfigError(null)
}

const normalizeRatioPresets = (payload) => {
  if (!Array.isArray(payload)) {
    return []
  }
  return payload
    .map((item) => {
      const code = normalizePresetCode(item?.code)
      const displayName = typeof item?.displayName === 'string' && item.displayName.trim() ? item.displayName.trim() : code
      if (!code) {
        return null
      }
      return {
        code,
        displayName,
        takeProfitPct: item?.takeProfitPct,
        stopLossPct: item?.stopLossPct,
        trailingStopPct: item?.trailingStopPct,
        partialTakeProfitPct: item?.partialTakeProfitPct,
        stopExitPct: item?.stopExitPct,
        trendExitPct: item?.trendExitPct,
        momentumExitPct: item?.momentumExitPct,
      }
    })
    .filter(Boolean)
}

const buildMarketOverrideRows = (payload) => {
  const configuredMarkets = Array.isArray(payload?.markets) ? payload.markets : []
  const maxOrderKrwByMarket = payload?.maxOrderKrwByMarket ?? {}
  const profileByMarket = payload?.profileByMarket ?? {}

  const orderedMarkets = []
  const seen = new Set()
  configuredMarkets.forEach((market) => {
    const normalized = normalizeMarket(market)
    if (!normalized || seen.has(normalized)) {
      return
    }
    seen.add(normalized)
    orderedMarkets.push(normalized)
  })
  Object.keys(maxOrderKrwByMarket).forEach((market) => {
    const normalized = normalizeMarket(market)
    if (!normalized || seen.has(normalized)) {
      return
    }
    seen.add(normalized)
    orderedMarkets.push(normalized)
  })
  Object.keys(profileByMarket).forEach((market) => {
    const normalized = normalizeMarket(market)
    if (!normalized || seen.has(normalized)) {
      return
    }
    seen.add(normalized)
    orderedMarkets.push(normalized)
  })

  return orderedMarkets.map((market) => ({
    market,
    maxOrderKrw: toInputValue(maxOrderKrwByMarket?.[market]),
    profile: normalizeProfileValue(profileByMarket?.[market]),
  }))
}

const buildMarketOverridePayload = (rows) => {
  const payload = {
    maxOrderKrwByMarket: {},
    profileByMarket: {},
  }
  if (!Array.isArray(rows)) {
    return payload
  }

  rows.forEach((row) => {
    const market = normalizeMarket(row?.market)
    if (!market) {
      return
    }
    const maxOrderKrw = `${row?.maxOrderKrw ?? ''}`.trim()
    if (maxOrderKrw !== '') {
      const value = Number(maxOrderKrw)
      if (Number.isNaN(value) || value <= 0) {
        throw new Error(`${market} 최대 매수 KRW는 0보다 커야 합니다.`)
      }
      payload.maxOrderKrwByMarket[market] = value
    }
    const profile = normalizeProfileValue(row?.profile)
    if (profile !== '') {
      payload.profileByMarket[market] = profile
    }
  })
  return payload
}

const buildMarketListPayload = (rows) => {
  if (!Array.isArray(rows)) {
    throw new Error('마켓 목록이 비어 있습니다.')
  }

  const markets = []
  const seen = new Set()
  rows.forEach((row) => {
    const market = normalizeMarket(row?.market)
    if (!market) {
      return
    }
    if (!isValidMarketCode(market)) {
      throw new Error(`${market} 마켓 코드 형식이 올바르지 않습니다. 예: KRW-BTC`)
    }
    if (seen.has(market)) {
      return
    }
    seen.add(market)
    markets.push(market)
  })

  if (markets.length === 0) {
    throw new Error('최소 1개 이상의 마켓이 필요합니다.')
  }

  return { markets }
}

const buildMarketOverrideSignature = (rows) => {
  if (!Array.isArray(rows)) {
    return ''
  }
  const normalized = rows
    .map((row) => {
      const market = normalizeMarket(row?.market)
      if (!market) {
        return null
      }
      return {
        market,
        maxOrderKrw: normalizeCapForSignature(row?.maxOrderKrw),
        profile: normalizeProfileValue(row?.profile),
      }
    })
    .filter((row) => row !== null)
    .sort((a, b) => a.market.localeCompare(b.market))
  return JSON.stringify(normalized)
}

const normalizeCapForSignature = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  const raw = String(value).trim()
  if (raw === '') {
    return ''
  }
  const numeric = Number(raw)
  return Number.isFinite(numeric) ? String(numeric) : raw
}

const buildApiErrorMessage = (payload, fallback) => {
  if (!payload || typeof payload !== 'object') {
    return fallback
  }
  const base = typeof payload.error === 'string' && payload.error.trim() !== '' ? payload.error.trim() : fallback
  const fields = payload.fields
  if (!fields || typeof fields !== 'object') {
    return base
  }
  const details = Object.entries(fields)
    .map(([field, message]) => `${field}: ${String(message)}`)
    .join(', ')
  if (details === '') {
    return base
  }
  return `${base} (${details})`
}

const normalizeProfileValue = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === '') {
    return ''
  }
  return PROFILE_VALUES.includes(normalized) ? normalized : ''
}

const normalizePresetCode = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === '') {
    return ''
  }
  return normalized
}

const normalizeMarket = (value) => {
  if (value === null || value === undefined) {
    return null
  }
  const normalized = String(value).trim().toUpperCase()
  if (normalized === '') {
    return null
  }
  return normalized
}

const isValidMarketCode = (value) => MARKET_CODE_PATTERN.test(value)

const toInputValue = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
}

const toNumber = (value) => {
  const numeric = Number(value)
  return Number.isNaN(numeric) ? null : numeric
}

const formatKRW = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 0 })
}

const formatCoin = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toLocaleString('en-US', { maximumFractionDigits: 8 })
}

const formatPercent = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return `${(Number(value) * 100).toFixed(2)}%`
}

const formatFixed = (value, digits) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '-'
  }
  return Number(value).toFixed(digits)
}

const formatDateTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleString('ko-KR', { hour12: false })
}

const formatTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleTimeString('ko-KR', { hour12: false })
}

const truncateText = (value, max) => {
  if (!value) {
    return '-'
  }
  if (value.length <= max) {
    return value
  }
  return `${value.slice(0, max)}...`
}

const pnlClass = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return 'neutral'
  }
  if (Number(value) > 0) {
    return 'positive'
  }
  if (Number(value) < 0) {
    return 'negative'
  }
  return 'neutral'
}

export default App
