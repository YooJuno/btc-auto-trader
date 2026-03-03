function OnboardingRoute({
  onboarding,
  userRiskProfile,
  userMarketsInput,
  setUserRiskProfile,
  setUserMarketsInput,
  onCompleteProfile,
  exchangeAccessKeyInput,
  exchangeSecretKeyInput,
  setExchangeAccessKeyInput,
  setExchangeSecretKeyInput,
  onCompleteCredentials,
  onCompleteStrategy,
  onFinish,
  busy,
  error,
}) {
  const profileDone = Boolean(onboarding?.profileCompleted)
  const credentialDone = Boolean(onboarding?.credentialsCompleted)
  const strategyDone = Boolean(onboarding?.strategyCompleted)
  const completed = Boolean(onboarding?.completed)

  return (
    <section className="workspace-grid workspace-grid--settings">
      <aside className="workspace-side">
        <article className="control-card card--elevated">
          <div className="card-head">
            <div>
              <h2>온보딩 1/3: 프로필</h2>
              <p className="sub">리스크 프로필과 관심 마켓을 저장합니다.</p>
            </div>
            <span className={`pill ${profileDone ? '' : 'pill-warning'}`}>{profileDone ? '완료' : '대기'}</span>
          </div>
          <div className="form-grid auth-settings-grid">
            <label className="form-field">
              <span>리스크 프로필</span>
              <select value={userRiskProfile} onChange={(event) => setUserRiskProfile(event.target.value)}>
                <option value="BALANCED">BALANCED</option>
                <option value="AGGRESSIVE">AGGRESSIVE</option>
                <option value="CONSERVATIVE">CONSERVATIVE</option>
              </select>
            </label>
            <label className="form-field">
              <span>관심 마켓</span>
              <input
                type="text"
                value={userMarketsInput}
                onChange={(event) => setUserMarketsInput(event.target.value)}
                placeholder="예: KRW-BTC, KRW-ETH"
              />
            </label>
          </div>
          <div className="button-row">
            <button className="primary-button" type="button" onClick={onCompleteProfile} disabled={busy}>
              {busy ? '처리 중...' : '1단계 저장 및 완료'}
            </button>
          </div>
        </article>

        <article className="control-card card--elevated">
          <div className="card-head">
            <div>
              <h2>온보딩 2/3: 거래소 키</h2>
              <p className="sub">Upbit API 키 저장 후 검증합니다.</p>
            </div>
            <span className={`pill ${credentialDone ? '' : 'pill-warning'}`}>{credentialDone ? '완료' : '대기'}</span>
          </div>
          <div className="form-grid auth-settings-grid">
            <label className="form-field">
              <span>Access Key</span>
              <input
                type="text"
                value={exchangeAccessKeyInput}
                onChange={(event) => setExchangeAccessKeyInput(event.target.value)}
                placeholder="Upbit Access Key"
                disabled={!profileDone || busy}
              />
            </label>
            <label className="form-field">
              <span>Secret Key</span>
              <input
                type="password"
                value={exchangeSecretKeyInput}
                onChange={(event) => setExchangeSecretKeyInput(event.target.value)}
                placeholder="Upbit Secret Key"
                disabled={!profileDone || busy}
              />
            </label>
          </div>
          <div className="button-row">
            <button className="primary-button" type="button" onClick={onCompleteCredentials} disabled={!profileDone || busy}>
              {busy ? '처리 중...' : '2단계 저장/검증 후 완료'}
            </button>
          </div>
        </article>

        <article className="control-card card--elevated">
          <div className="card-head">
            <div>
              <h2>온보딩 3/3: 전략 기본값</h2>
              <p className="sub">초기 전략값 확인 후 첫 화면 진입을 확정합니다.</p>
            </div>
            <span className={`pill ${strategyDone ? '' : 'pill-warning'}`}>{strategyDone ? '완료' : '대기'}</span>
          </div>
          <div className="button-row">
            <button
              className="primary-button"
              type="button"
              onClick={onCompleteStrategy}
              disabled={!credentialDone || busy}
            >
              {busy ? '처리 중...' : '3단계 완료'}
            </button>
            <button
              className="ghost-button"
              type="button"
              onClick={onFinish}
              disabled={!completed || busy}
            >
              대시보드 이동
            </button>
          </div>
          {error && <p className="status-error">{error}</p>}
        </article>
      </aside>
    </section>
  )
}

export default OnboardingRoute
