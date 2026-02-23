function ManualTradeModal({
  open,
  busy,
  market,
  side,
  type,
  price,
  volume,
  funds,
  position,
  cashKrw,
  error,
  onClose,
  onSubmit,
  setSide,
  setType,
  setPrice,
  setVolume,
  setFunds,
  formatters,
}) {
  const { formatCoin, formatKRW, toInputValue } = formatters

  if (!open) {
    return null
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="trade-modal" onClick={(event) => event.stopPropagation()}>
        <div className="card-head">
          <div>
            <h2>수동 매매</h2>
            <p className="sub">시장가/지정가 주문을 직접 넣습니다.</p>
          </div>
          <button className="ghost-button" type="button" onClick={onClose} disabled={busy}>
            닫기
          </button>
        </div>

        <div className="trade-meta-row">
          <span>마켓 {market}</span>
          <span>보유 {formatCoin(position?.quantity)}</span>
          <span>현금 {formatKRW(cashKrw)} KRW</span>
        </div>

        <div className="form-grid trade-form-grid">
          <label className="form-field">
            <span>구분</span>
            <select value={side} onChange={(event) => setSide(event.target.value)}>
              <option value="BUY">매수</option>
              <option value="SELL">매도</option>
            </select>
          </label>
          <label className="form-field">
            <span>주문방식</span>
            <select value={type} onChange={(event) => setType(event.target.value)}>
              <option value="MARKET">시장가</option>
              <option value="LIMIT">지정가</option>
            </select>
          </label>
          {type === 'LIMIT' && (
            <>
              <label className="form-field">
                <span>지정가 (KRW)</span>
                <input
                  type="number"
                  min="0"
                  step="0.1"
                  value={price}
                  onChange={(event) => setPrice(event.target.value)}
                  placeholder="예: 101500000"
                />
              </label>
              <label className="form-field">
                <span>수량</span>
                <input
                  type="number"
                  min="0"
                  step="0.00000001"
                  value={volume}
                  onChange={(event) => setVolume(event.target.value)}
                  placeholder="예: 0.001"
                />
              </label>
            </>
          )}
          {type === 'MARKET' && side === 'BUY' && (
            <label className="form-field">
              <span>매수 금액 (KRW)</span>
              <input
                type="number"
                min="0"
                step="1000"
                value={funds}
                onChange={(event) => setFunds(event.target.value)}
                placeholder="예: 30000"
              />
            </label>
          )}
          {type === 'MARKET' && side === 'SELL' && (
            <label className="form-field">
              <span>매도 수량</span>
              <div className="trade-volume-row">
                <input
                  type="number"
                  min="0"
                  step="0.00000001"
                  value={volume}
                  onChange={(event) => setVolume(event.target.value)}
                  placeholder="예: 0.001"
                />
                <button
                  className="ghost-button"
                  type="button"
                  onClick={() => setVolume(toInputValue(position?.quantity))}
                >
                  전량
                </button>
              </div>
            </label>
          )}
        </div>

        {error && <p className="status-error">{error}</p>}

        <div className="button-row">
          <button className="primary-button" type="button" onClick={onSubmit} disabled={busy}>
            {busy ? '주문 중...' : '주문 실행'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default ManualTradeModal
