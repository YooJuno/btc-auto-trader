import { memo } from 'react'

const PositionRow = memo(function PositionRow({
  position,
  authRequired,
  onOpenManualTrade,
  formatKRW,
  formatCoin,
  formatPercent,
  pnlClass,
}) {
  return (
    <tr>
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
      <td>
        <button
          className="table-action-button"
          type="button"
          disabled={authRequired}
          onClick={() => onOpenManualTrade(position.market, 'SELL')}
        >
          매매
        </button>
      </td>
    </tr>
  )
})

function PositionsCard({
  authRequired,
  loading,
  positions,
  summaryError,
  manualTradeNotice,
  onOpenManualTrade,
  formatKRW,
  formatCoin,
  formatPercent,
  pnlClass,
}) {
  return (
    <section className="table-card table-card--elevated positions-card">
      <div className="table-header">
        <div>
          <h2>보유 코인</h2>
          <p className="sub">현재가 기준 평가와 수익률을 표시합니다.</p>
        </div>
      </div>
      {authRequired && <p className="status-error">로그인 후 매매 및 설정 기능을 사용할 수 있습니다.</p>}
      {manualTradeNotice && <p className="status-success">{manualTradeNotice}</p>}
      {loading ? (
        <div className="empty-state">데이터를 불러오는 중입니다…</div>
      ) : summaryError && positions.length === 0 ? (
        <div className="empty-state">보유 코인 정보를 가져오지 못했습니다.</div>
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
                <th>매매</th>
              </tr>
            </thead>
            <tbody>
              {positions.map((position) => (
                <PositionRow
                  key={position.market}
                  position={position}
                  authRequired={authRequired}
                  onOpenManualTrade={onOpenManualTrade}
                  formatKRW={formatKRW}
                  formatCoin={formatCoin}
                  formatPercent={formatPercent}
                  pnlClass={pnlClass}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

export default memo(PositionsCard)
