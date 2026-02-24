import { formatKRW, formatPercent, pnlClass } from '../../utils/tradingUi.js'

function PerformanceCard({
  fetchPerformance,
  performanceMode,
  setPerformanceMode,
  performanceInputs,
  setPerformanceInputs,
  performanceLoading,
  performanceError,
  performance,
  performanceTotal,
}) {
  return (
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
  )
}

export default PerformanceCard
