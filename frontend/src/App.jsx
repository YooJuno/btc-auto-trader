import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'

function App() {
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [engineStatus, setEngineStatus] = useState(null)
  const [engineBusy, setEngineBusy] = useState(false)
  const [engineError, setEngineError] = useState(null)
  const [tickResult, setTickResult] = useState(null)
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

  const fetchSummary = useCallback(async (isRefresh = false) => {
    if (isRefresh) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }
    setError(null)

    try {
      const response = await fetch('/api/portfolio/summary')
      if (!response.ok) {
        throw new Error(`서버 응답 ${response.status}`)
      }
      const data = await response.json()
      setSummary(data)
    } catch (err) {
      setError(err?.message ?? '요청 실패')
    } finally {
      if (isRefresh) {
        setRefreshing(false)
      } else {
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
    } catch (err) {
      setStrategyError(err?.message ?? '전략 조회 실패')
    }
  }, [])

  useEffect(() => {
    fetchSummary(false)
    fetchEngineStatus()
    fetchStrategy()
    const timer = setInterval(() => fetchSummary(true), 2000)
    return () => clearInterval(timer)
  }, [fetchSummary, fetchEngineStatus, fetchStrategy])

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

  const statusClass = error ? 'error' : loading || refreshing ? 'loading' : 'ok'
  const statusLabel = error ? '오류' : loading ? '불러오는 중' : refreshing ? '갱신 중' : '정상'
  const engineLabel = engineStatus ? 'ON' : 'OFF'
  const engineClass = engineStatus ? 'ok' : 'error'
  const profileLabel = strategy?.profile ?? '—'

  return (
    <div className="app">
      <header className="app__header">
        <div>
          <p className="eyebrow">BTC AUTO TRADER</p>
          <h1>Sundal</h1>
          <p className="sub">
            Work Day & Night for your financial free life
          </p>
        </div>
        <div className="status-card">
          <div className="status-row">
            <span>업데이트</span>
            <strong className="mono">{updatedAt}</strong>
          </div>
        </div>
      </header>

      <section className="control-grid">
        <div className="control-card">
          <div className="card-head">
            <div>
              <h2>자동매매 제어</h2>
              <p className="sub">엔진 상태와 수동 실행을 관리합니다.</p>
            </div>
            <span className={`status ${engineClass}`}>ENGINE {engineLabel}</span>
          </div>
          {engineError && <p className="status-error">{engineError}</p>}
          <div className="button-row">
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
            <button
              className="ghost-button"
              onClick={() => handleEngineTick(setTickResult, setEngineError, setEngineBusy)}
              disabled={engineBusy}
            >
              1회 실행
            </button>
          </div>
          {tickResult && (
            <div className="result-row">
              <span>최근 실행</span>
              <strong className="mono">{tickResult}</strong>
            </div>
          )}
        </div>

        <div className="control-card">
          <div className="card-head">
            <div>
              <h2>비율 설정</h2>
              <p className="sub">익절/손절/부분 매도 비율을 조정합니다.</p>
            </div>
            <span className="pill">PROFILE {profileLabel}</span>
          </div>
          {strategyError && <p className="status-error">{strategyError}</p>}
          {ratioError && <p className="status-error">{ratioError}</p>}
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
              onClick={() => handleRatioSave(ratioInputs, setRatioSaving, setRatioError, setStrategy)}
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

      <section className="table-card">
        <div className="table-header">
          <div>
            <h2>보유 코인</h2>
            <p className="sub">현재가 기준 평가와 수익률을 표시합니다.</p>
          </div>
          <span className={`status ${statusClass}`}>{statusLabel}</span>
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
                    <td className={`mono ${pnlClass(position.pnl)}`}>
                      {formatKRW(position.pnl)}
                    </td>
                    <td className={`mono ${pnlClass(position.pnl)}`}>
                      {formatPercent(position.pnlRate)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
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

const handleEngineTick = async (setTickResult, setEngineError, setEngineBusy) => {
  if (!window.confirm('수동 실행을 하시겠습니까? 주문이 발생할 수 있습니다.')) {
    return
  }
  setEngineBusy(true)
  setEngineError(null)
  try {
    const response = await fetch('/api/engine/tick', { method: 'POST' })
    if (!response.ok) {
      const payload = await response.json().catch(() => null)
      const reason = payload?.actions?.[0]?.reason
      throw new Error(reason ? `실행 실패: ${reason}` : `실행 실패 ${response.status}`)
    }
    const data = await response.json()
    const action = data?.actions?.[0]
    if (action) {
      const label = `${action.action} - ${action.reason ?? 'ok'}`
      setTickResult(label)
    } else {
      setTickResult('실행 완료')
    }
  } catch (err) {
    setEngineError(err?.message ?? '실행 실패')
  } finally {
    setEngineBusy(false)
  }
}

const handleRatioSave = async (inputs, setRatioSaving, setRatioError, setStrategy) => {
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
  } catch (err) {
    setRatioError(err?.message ?? '비율 저장 실패')
  } finally {
    setRatioSaving(false)
  }
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

const toInputValue = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
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
