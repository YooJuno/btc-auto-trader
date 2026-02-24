function DashboardRoute({
  cash,
  totals,
  loading,
  positions,
  manualTradeNotice,
  mergedOrderHistory,
  feedError,
  onOpenManualTrade,
  formatters,
}) {
  const {
    formatKRW,
    formatCoin,
    formatPercent,
    formatDateTime,
    formatOrderStatus,
    formatFixed,
    truncateText,
    pnlClass,
  } = formatters

  return (
    <>
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

      <section className="workspace-grid workspace-grid--status">
        <div className="workspace-main">
          <section className="table-card table-card--elevated positions-card">
            <div className="table-header">
              <div>
                <h2>보유 코인</h2>
                <p className="sub">현재가 기준 평가와 수익률을 표시합니다.</p>
              </div>
            </div>
            {manualTradeNotice && <p className="status-success">{manualTradeNotice}</p>}
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
                      <th>매매</th>
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
                        <td>
                          <button
                            className="table-action-button"
                            type="button"
                            onClick={() => onOpenManualTrade(position.market, 'SELL')}
                          >
                            매매
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <article className="table-card card--elevated order-card">
            <div className="table-header">
              <div>
                <h2>최근 주문 로그</h2>
                <p className="sub">주문 상태 + 매매 사유/지표 스냅샷 통합</p>
              </div>
            </div>
            {feedError && <p className="status-error">{feedError}</p>}
            {mergedOrderHistory.length === 0 ? (
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
                      <th>사유</th>
                      <th>RSI</th>
                      <th>MACD</th>
                      <th>MA Slope%</th>
                      <th>가격</th>
                      <th>프로필</th>
                      <th>오류</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mergedOrderHistory.map((order) => {
                      const decision = order.decision
                      return (
                        <tr key={order.id}>
                          <td className="mono small">{formatDateTime(order.requestedAt)}</td>
                          <td>{order.market}</td>
                          <td className={order.side === 'BUY' ? 'positive' : 'negative'}>{order.side}</td>
                          <td>
                            <span className="mono">{formatOrderStatus(order.requestStatus, order.state)}</span>
                          </td>
                          <td className="mono">{formatCoin(order.volume)}</td>
                          <td className="mono">{formatKRW(order.funds)}</td>
                          <td className="mono small">{decision?.reason ?? '-'}</td>
                          <td className="mono">{formatFixed(decision?.rsi, 2)}</td>
                          <td className="mono">{formatFixed(decision?.macdHistogram, 4)}</td>
                          <td className="mono">{formatFixed(decision?.maLongSlopePct, 3)}</td>
                          <td className="mono">{formatKRW(decision?.price)}</td>
                          <td>{decision?.profile ?? '-'}</td>
                          <td className="mono small">{truncateText(order.errorMessage, 36)}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </article>
        </div>
      </section>
    </>
  )
}

export default DashboardRoute
