function AuthGate({ checking, authError, authProviders, onProviderLogin }) {
  if (checking) {
    return (
      <div className="auth-gate">
        <div className="auth-gate__card">
          <p className="eyebrow">BTC AUTO TRADER</p>
          <h2>로그인 상태 확인 중</h2>
          <p className="sub">세션을 확인하고 있습니다.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-gate">
      <div className="auth-gate__card">
        <p className="eyebrow">BTC AUTO TRADER</p>
        <h2>로그인이 필요합니다</h2>
        <p className="sub">OAuth 로그인 후 사용자별 인터페이스 설정을 불러옵니다.</p>
        {authError && <p className="status-error">{authError}</p>}
        {authProviders.length === 0 ? (
          <p className="status-error">사용 가능한 OAuth 공급자가 없습니다. 백엔드 OAuth 설정을 확인해주세요.</p>
        ) : (
          <div className="button-row auth-provider-row">
            {authProviders.map((provider) => (
              <button
                key={provider.id}
                className="primary-button"
                type="button"
                onClick={() => onProviderLogin(provider.authorizationUrl)}
              >
                {provider.name} 로그인
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export default AuthGate
