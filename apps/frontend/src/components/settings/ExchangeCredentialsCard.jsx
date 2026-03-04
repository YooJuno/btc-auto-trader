import { memo } from 'react'
import { formatDateTime } from '../../utils/tradingUi.js'

function ExchangeCredentialsCard({
  exchangeCredentialStatus,
  exchangeCredentialLoading,
  exchangeCredentialSaving,
  exchangeCredentialVerifying,
  exchangeCredentialError,
  exchangeCredentialNotice,
  exchangeAccessKeyInput,
  exchangeSecretKeyInput,
  setExchangeAccessKeyInput,
  setExchangeSecretKeyInput,
  handleSaveExchangeCredentials,
  handleVerifyExchangeCredentials,
  handleDeleteExchangeCredentials,
}) {
  return (
    <article className="control-card card--elevated auth-settings-card">
      <div className="card-head">
        <div>
          <h2>거래소 API 키</h2>
          <p className="sub">사용자별 Upbit API 키를 저장/검증합니다.</p>
        </div>
        <span className="pill">
          {exchangeCredentialStatus?.configured
            ? '등록됨'
            : exchangeCredentialStatus?.usingDefaultCredentials
              ? '기본키 사용'
              : '미등록'}
        </span>
      </div>
      {exchangeCredentialError && <p className="status-error">{exchangeCredentialError}</p>}
      {exchangeCredentialNotice && <p className="status-success">{exchangeCredentialNotice}</p>}
      <div className="form-grid auth-settings-grid">
        <label className="form-field">
          <span>Access Key</span>
          <input
            type="text"
            value={exchangeAccessKeyInput}
            onChange={(event) => setExchangeAccessKeyInput(event.target.value)}
            placeholder="Upbit Access Key"
            disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
          />
        </label>
        <label className="form-field">
          <span>Secret Key</span>
          <input
            type="password"
            value={exchangeSecretKeyInput}
            onChange={(event) => setExchangeSecretKeyInput(event.target.value)}
            placeholder="Upbit Secret Key"
            disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
          />
        </label>
      </div>
      <div className="button-row">
        <button
          className="primary-button"
          type="button"
          onClick={handleSaveExchangeCredentials}
          disabled={exchangeCredentialSaving || exchangeCredentialVerifying}
        >
          {exchangeCredentialSaving ? '저장 중...' : '키 저장'}
        </button>
        <button
          className="ghost-button"
          type="button"
          onClick={handleVerifyExchangeCredentials}
          disabled={exchangeCredentialLoading || exchangeCredentialSaving || exchangeCredentialVerifying}
        >
          {exchangeCredentialVerifying ? '검증 중...' : '키 검증'}
        </button>
        <button
          className="ghost-button"
          type="button"
          onClick={handleDeleteExchangeCredentials}
          disabled={exchangeCredentialSaving || exchangeCredentialVerifying || !exchangeCredentialStatus?.configured}
        >
          키 삭제
        </button>
      </div>
      {exchangeCredentialStatus?.updatedAt && (
        <p className="sub compact">마지막 저장 {formatDateTime(exchangeCredentialStatus.updatedAt)}</p>
      )}
    </article>
  )
}

export default memo(ExchangeCredentialsCard)
