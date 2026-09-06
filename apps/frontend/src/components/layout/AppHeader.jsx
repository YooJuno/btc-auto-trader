/*
 * Header: identity of the app, state of the engine, and the two controls that change it.
 *
 * Navigation moved to the left rail and ambient status to the bottom bar, so this row now carries only
 * what an operator acts on. That separation is the point — 긴급 청산 sitting next to a page tab at the
 * same visual weight is how the wrong button gets clicked.
 */
function AppHeader({
  authChecking,
  authProviders,
  authError,
  onProviderLogin,
  engineError,
  engineBusy,
  engineStatus,
  engineKnown,
  tradingMode,
  onEngineToggle,
  onPanic,
  panicBusy,
  hasOpenPositions,
  authUser,
  onLogout,
  approvalStatus,
}) {
  const authenticated = Boolean(authUser?.id)
  const userLabel = authenticated
    ? (authUser?.displayName || authUser?.email || `${authUser?.provider}:${authUser?.providerUserId}`)
    : '게스트'
  const providers = Array.isArray(authProviders) ? authProviders.filter(Boolean) : []
  const loginLabel = authChecking ? '확인 중' : providers.length === 0 ? '로그인 설정 필요' : null

  // Three states, not two. Collapsing "unknown" into "OFF" showed a live engine as stopped and offered
  // to start it — the most dangerous thing this header could get wrong.
  const engineDotClass = !engineKnown ? 'engine-dot--unknown' : engineStatus ? 'engine-dot--on' : 'engine-dot--off'
  const engineLabel = !engineKnown ? '상태 불명' : engineStatus ? '가동 중' : '정지'

  return (
    <>
      <header className="topbar">
        <div className="topbar__brand">
          <span className="topbar__mark" aria-hidden="true" />
          <span className="topbar__name">BTC AUTO TRADER</span>
          {tradingMode && (
            <span className={`mode-badge ${tradingMode === 'PAPER' ? 'mode-badge--paper' : 'mode-badge--live'}`}>
              {tradingMode === 'PAPER' ? '모의' : '실계좌'}
            </span>
          )}
        </div>

        <span className="topbar__spacer" />

        {authenticated ? (
          <div className="topbar__actions">
            <span className="engine-control" title="자동매매 엔진 상태">
              <span className={`engine-dot ${engineDotClass}`} aria-hidden="true" />
              <span className="engine-state-label">엔진 {engineLabel}</span>
            </span>
            <button
              type="button"
              className="engine-toggle-btn"
              onClick={onEngineToggle}
              disabled={engineBusy}
            >
              {engineBusy ? '처리 중' : engineStatus ? '중지' : '시작'}
            </button>
            <button
              type="button"
              className="panic-btn"
              onClick={onPanic}
              disabled={panicBusy}
              title="엔진을 중지하고 보유 코인을 전량 시장가 매도합니다"
            >
              {panicBusy ? '청산 중' : '긴급 청산'}
            </button>

            <span className="topbar__divider" aria-hidden="true" />

            <span className="topbar__user" title={authUser?.email ?? undefined}>
              {userLabel}
              {approvalStatus && approvalStatus !== '-' && (
                <b className={`approval-status ${approvalStatus === 'ADMIN' ? 'approval-status--admin' : ''}`}>
                  {approvalStatus}
                </b>
              )}
            </span>
            <button className="ghost-button" type="button" onClick={onLogout}>
              로그아웃
            </button>
          </div>
        ) : (
          <div className="topbar__actions">
            {loginLabel ? (
              <button type="button" className="primary-button" disabled>{loginLabel}</button>
            ) : (
              providers.map((provider, index) => (
                <button
                  key={provider?.id ?? provider?.authorizationUrl ?? index}
                  type="button"
                  className={index === 0 ? 'primary-button' : 'ghost-button'}
                  onClick={() => onProviderLogin(provider?.authorizationUrl)}
                  disabled={authChecking || !provider?.authorizationUrl}
                >
                  {provider?.name ? `${provider.name} 로그인` : '로그인'}
                </button>
              ))
            )}
          </div>
        )}
      </header>

      {/* Alerts sit directly under the bar that caused them rather than inside it, so the bar keeps a
          fixed height and the page below does not jump as conditions come and go. */}
      {authenticated && !engineKnown && (
        <p className="banner banner--warn">
          엔진 상태를 확인할 수 없습니다. 실제로는 동작 중일 수 있으니 시작/중지 전에 서버 연결을 확인하세요.
        </p>
      )}
      {authenticated && engineError && <p className="banner banner--error">{engineError}</p>}
      {authenticated && engineKnown && !engineStatus && hasOpenPositions && (
        <p className="banner banner--error">
          엔진이 정지된 상태에서 보유 포지션이 있습니다. 손절·트레일링이 동작하지 않습니다.
        </p>
      )}
      {!authenticated && authError && <p className="banner banner--error">{authError}</p>}
    </>
  )
}

export default AppHeader
