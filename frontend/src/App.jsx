import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'

function App() {
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

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

  useEffect(() => {
    fetchSummary(false)
    const timer = setInterval(() => fetchSummary(true), 2000)
    return () => clearInterval(timer)
  }, [fetchSummary])

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
