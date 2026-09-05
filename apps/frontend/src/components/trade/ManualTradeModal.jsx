import { useState } from 'react'

/*
 * Manual order ticket.
 *
 * Two things this screen previously got wrong: the side sat in an uncoloured <select> next to a neutral
 * confirm button, so a trader was one unlabelled click away from an inverted order; and a real-money
 * market order submitted with no confirmation at all, while deleting an API key did confirm.
 */
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
  // The confirmation is bound to the exact ticket it was given for, so changing market, side or order
  // type invalidates it automatically — no effect, and no way to confirm one order and send another.
  const ticketKey = `${market}|${side}|${type}`
  const [confirmedTicket, setConfirmedTicket] = useState(null)
  const confirming = open && confirmedTicket === ticketKey

  if (!open) {
    return null
  }

  const isBuy = side === 'BUY'
  const sideLabel = isBuy ? '매수' : '매도'
  const summary = isBuy
    ? `${market} ${type === 'MARKET' ? '시장가' : '지정가'} 매수 · ${type === 'MARKET' ? `${formatKRW(funds)} KRW` : `${toInputValue(volume)} @ ${formatKRW(price)}`}`
    : `${market} ${type === 'MARKET' ? '시장가' : '지정가'} 매도 · ${toInputValue(volume)}${type === 'LIMIT' ? ` @ ${formatKRW(price)}` : ''}`

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="trade-modal"
        role="dialog"
        aria-modal="true"
        aria-label="수동 매매"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="card-head">
          <h2>수동 매매</h2>
          <button className="ghost-button" type="button" onClick={onClose} disabled={busy}>
            닫기
          </button>
        </div>

        <div className="trade-meta-row">
          <span>
            마켓 <b>{market}</b>
          </span>
          <span>
            보유 <b>{formatCoin(position?.quantity)}</b>
          </span>
          <span>
            현금 <b>{formatKRW(cashKrw)}</b> KRW
          </span>
        </div>

        <div className="side-toggle" role="group" aria-label="매수/매도 선택">
          <button
            type="button"
            className={isBuy ? 'is-buy-active' : ''}
            aria-pressed={isBuy}
            onClick={() => setSide('BUY')}
          >
            매수
          </button>
          <button
            type="button"
            className={!isBuy ? 'is-sell-active' : ''}
            aria-pressed={!isBuy}
            onClick={() => setSide('SELL')}
          >
            매도
          </button>
        </div>

        <div className="form-grid trade-form-grid">
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
                />
              </label>
            </>
          )}
          {type === 'MARKET' && isBuy && (
            <label className="form-field">
              <span>매수 금액 (KRW)</span>
              <input
                type="number"
                min="0"
                step="1000"
                value={funds}
                onChange={(event) => setFunds(event.target.value)}
              />
            </label>
          )}
          {type === 'MARKET' && !isBuy && (
            <label className="form-field">
              <span>매도 수량</span>
              <div className="trade-volume-row">
                <input
                  type="number"
                  min="0"
                  step="0.00000001"
                  value={volume}
                  onChange={(event) => setVolume(event.target.value)}
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

        {confirming ? (
          <div className="confirm-box">
            <span>
              실제 자금으로 <b>{sideLabel}</b> 주문을 전송합니다. 되돌릴 수 없습니다.
              <br />
              {summary}
            </span>
            <div className="button-row">
              <button
                className={isBuy ? 'danger-button' : 'primary-button'}
                type="button"
                onClick={onSubmit}
                disabled={busy}
              >
                {busy ? '주문 전송 중…' : `${sideLabel} 확정`}
              </button>
              <button
                className="ghost-button"
                type="button"
                onClick={() => setConfirmedTicket(null)}
                disabled={busy}
              >
                취소
              </button>
            </div>
          </div>
        ) : (
          <div className="button-row">
            <button
              className="primary-button"
              type="button"
              onClick={() => setConfirmedTicket(ticketKey)}
              disabled={busy}
            >
              {sideLabel} 주문
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export default ManualTradeModal
