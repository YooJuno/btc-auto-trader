/*
 * Bottom status bar.
 *
 * Connection state and last-update time are ambient facts: needed constantly, urgent never. Holding
 * them in the header meant the row that also carries 엔진 시작 and 긴급 청산 was competing for
 * attention with a timestamp. Down here they are always readable and never in the way.
 */
function AppStatusBar({ connectionClass, connectionLabel, updatedAt, tradingMode, marketCount, authenticated }) {
  return (
    <footer className="statusbar">
      <span className={`statusbar__dot statusbar__dot--${connectionClass}`} aria-hidden="true" />
      <span className="statusbar__item">{connectionLabel}</span>

      {authenticated && (
        <>
          <span className="statusbar__sep" aria-hidden="true" />
          <span className="statusbar__item">
            갱신 <b className="mono">{updatedAt}</b>
          </span>
          {marketCount > 0 && (
            <>
              <span className="statusbar__sep" aria-hidden="true" />
              <span className="statusbar__item">
                보유 <b className="mono">{marketCount}</b>
              </span>
            </>
          )}
        </>
      )}

      <span className="statusbar__spacer" />

      {tradingMode && (
        <span className="statusbar__item statusbar__mode">
          {tradingMode === 'PAPER' ? '모의매매' : '실계좌'}
        </span>
      )}
    </footer>
  )
}

export default AppStatusBar
