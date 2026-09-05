import { memo, useMemo, useState } from 'react'

/*
 * The decision feed.
 *
 * Every tick the engine persists its action, its reason and an indicator snapshot to trade_decisions.
 * The frontend fetched that endpoint with includeSkips=false and rendered one field of it, so the single
 * most useful thing the product knows — why no trade happened — never reached the screen. An idle bot and
 * a broken bot looked identical.
 *
 * Reason codes come from AutoTradeService/UnifiedTrendSignalModel. Anything unmapped falls through
 * verbatim rather than being hidden.
 */
const REASON_LABELS = {
  // entry rejections
  'no trend': '추세 없음 (단기MA ≤ 장기MA)',
  'no trend slope': '추세 기울기 데이터 부족',
  trend_weakening: '장기MA 기울기 하락',
  overextended: '장기MA 대비 과열',
  no_adx: 'ADX 데이터 부족',
  weak_trend: 'ADX 미달 (추세 약함)',
  no_volume: '거래량 데이터 부족',
  low_volume: '거래량 미달',
  'no breakout': '돌파 실패 (전고점 미돌파)',
  'insufficient candles': '캔들 데이터 부족',
  'insufficient cash': '주문 가능 금액 부족',
  'below min order': '최소 주문금액 미만',
  market_buy_cap_reached: '마켓별 매수 한도 도달',
  market_paused: '해당 마켓 일시정지',
  // guards
  backoff: 'API 오류 후 대기 중',
  cooldown: '주문 쿨다운',
  pending: '체결 대기 주문 있음',
  stop_loss_guard: '연속 손절 보호 잠금',
  stop_loss_cooldown: '손절 후 재진입 대기',
  reentry_cooldown: '청산 후 재진입 대기',
  // exits
  stop_loss: '손절',
  trailing_stop: '트레일링 스톱',
  donchian_exit: '채널 하단 이탈 청산',
  take_profit_partial: '부분 익절',
  trend_break: '추세 이탈 청산',
  momentum_reversal: '모멘텀 역전 청산',
  panic_exit: '긴급 청산',
  // entries
  trend_breakout: '추세 돌파 진입',
  'no signal': '조건 미충족 (대기)',
  'no available balance': '매도 가능 수량 없음',
  'price unavailable': '현재가 조회 실패',
}

const describeReason = (reason) => {
  if (!reason) return '-'
  if (REASON_LABELS[reason]) return REASON_LABELS[reason]
  // Composite reasons carry a suffix, e.g. htf_filter:<why>, risk_off_regime:<why>, daily_loss_limit:...
  const [head, ...rest] = String(reason).split(':')
  const tail = rest.join(':')
  if (head === 'htf_filter') return `상위 시간대 추세 불일치${tail ? ` (${tail})` : ''}`
  if (head === 'risk_off_regime') return `리스크오프 국면${tail ? ` (${tail})` : ''}`
  if (head === 'daily_loss_limit' || head.startsWith('daily_loss')) return '일일 손실 한도 도달'
  if (REASON_LABELS[head]) return `${REASON_LABELS[head]}${tail ? ` (${tail})` : ''}`
  return reason
}

const actionClass = (action) => {
  const normalized = String(action ?? '').toUpperCase()
  if (normalized === 'BUY') return 'decision-row--buy'
  if (normalized === 'SELL') return 'decision-row--sell'
  if (normalized === 'ERROR') return 'decision-row--error'
  return 'decision-row--skip'
}

function DecisionFeedCard({ decisions, decisionError, formatTime }) {
  const [showSkips, setShowSkips] = useState(true)

  const rows = useMemo(() => {
    const list = Array.isArray(decisions) ? decisions : []
    if (showSkips) return list
    return list.filter((item) => String(item?.action ?? '').toUpperCase() !== 'SKIP')
  }, [decisions, showSkips])

  const lastAt = decisions?.[0]?.executedAt

  return (
    <section className="table-card">
      <div className="table-header">
        <h2>엔진 판단 로그</h2>
        <label className="decision-filter">
          <input
            type="checkbox"
            checked={showSkips}
            onChange={(event) => setShowSkips(event.target.checked)}
          />
          미체결 사유 포함
        </label>
      </div>

      {decisionError && <p className="status-error">{decisionError}</p>}

      {rows.length === 0 ? (
        <div className="empty-state">
          {decisions?.length > 0
            ? '매매 실행 기록이 없습니다. 미체결 사유를 켜면 판단 근거가 보입니다.'
            : '판단 로그가 없습니다. 엔진이 실행되면 매 틱의 판단 근거가 기록됩니다.'}
        </div>
      ) : (
        <div className="decision-feed">
          {rows.map((item, index) => (
            <div
              key={item?.id ?? `${item?.market}-${item?.executedAt}-${index}`}
              className={`decision-row ${actionClass(item?.action)}`}
            >
              <span className="decision-row__time">{formatTime(item?.executedAt)}</span>
              <span className="decision-row__market">{item?.market ?? '-'}</span>
              <span className="decision-row__reason" title={item?.reason ?? undefined}>
                {describeReason(item?.reason)}
              </span>
              <span className="decision-row__act">{item?.action ?? '-'}</span>
            </div>
          ))}
        </div>
      )}

      {lastAt && (
        <p className="sub compact">마지막 판단 {formatTime(lastAt)}</p>
      )}
    </section>
  )
}

export default memo(DecisionFeedCard)
