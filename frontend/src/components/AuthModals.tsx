import React from 'react'

const AuthModals: React.FC<any> = ({
  loginOpen,
  setLoginOpen,
  registerOpen,
  setRegisterOpen,
  email,
  setEmail,
  password,
  setPassword,
  authError,
  setAuthError,
  loading,
  handleLogin,
  handleRegister,
}) => {
  return (
    <>
      {loginOpen && (
        <div className="modal-backdrop" onClick={() => setLoginOpen(false)}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>로그인</h3>
                <p>실시간 추천과 모의매매 성과를 연결합니다.</p>
              </div>
              <button className="ghost small" onClick={() => setLoginOpen(false)}>
                닫기
              </button>
            </div>
            <div className="form-grid">
              <label>
                Email
                <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
              </label>
              <label>
                Password
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="8+ characters" />
              </label>
            </div>
            {authError && <div className="alert">{authError}</div>}
            <div className="button-row">
              <button className="primary" onClick={handleLogin} disabled={loading}>
                로그인
              </button>
              <button
                className="ghost"
                onClick={() => {
                  setAuthError('')
                  setLoginOpen(false)
                  setRegisterOpen(true)
                }}
                disabled={loading}
              >
                회원가입으로
              </button>
            </div>
          </div>
        </div>
      )}

      {registerOpen && (
        <div className="modal-backdrop" onClick={() => setRegisterOpen(false)}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>회원가입</h3>
                <p>이메일로 자동 테넌트를 생성합니다.</p>
              </div>
              <button className="ghost small" onClick={() => setRegisterOpen(false)}>
                닫기
              </button>
            </div>
            <div className="form-grid">
              <label>
                Email
                <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
              </label>
              <label>
                Password
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="8+ characters" />
              </label>
            </div>
            {authError && <div className="alert">{authError}</div>}
            <div className="button-row">
              <button className="primary" onClick={handleRegister} disabled={loading}>
                회원가입
              </button>
              <button
                className="ghost"
                onClick={() => {
                  setAuthError('')
                  setRegisterOpen(false)
                  setLoginOpen(true)
                }}
                disabled={loading}
              >
                로그인으로
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default AuthModals
