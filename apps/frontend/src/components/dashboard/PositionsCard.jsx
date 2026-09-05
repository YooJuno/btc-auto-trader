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
      <td className="num">{formatCoin(position.quantity)}</td>
      <td className="num">{formatKRW(position.avgBuyPrice)}</td>
      <td className="num">{formatKRW(position.currentPrice)}</td>
      <td className="num">{formatKRW(position.valuation)}</td>
      <td className={`num ${pnlClass(position.pnl)}`}>{formatKRW(position.pnl)}</td>
      <td className={`num ${pnlClass(position.pnl)}`}>{formatPercent(position.pnlRate)}</td>
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
    <section className="table-card positions-card">
      <div className="table-header">
        <h2>보유 포지션</h2>
        {positions.length > 0 && <span className="pill">{positions.length}</span>}
      </div>
      {authRequired && <p className="status-error">로그인 후 매매 및 설정 기능을 사용할 수 있습니다.</p>}
      {manualTradeNotice && <p className="status-success">{manualTradeNotice}</p>}
      {loading ? (
        <div className="empty-state">불러오는 중…</div>
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
                <th className="num">보유수량</th>
                <th className="num">평균 매수</th>
                <th className="num">현재가</th>
                <th className="num">평가금액</th>
                <th className="num">평가손익</th>
                <th className="num">수익률</th>
                <th />
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
