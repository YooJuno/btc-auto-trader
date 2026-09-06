import { memo } from 'react'
import { formatDateTime } from '../../utils/tradingUi.js'

const formatProviderLabel = (provider, providerUserId) => {
  const providerText = String(provider ?? '').trim()
  const subjectText = String(providerUserId ?? '').trim()
  if (providerText && subjectText) {
    return `${providerText.toUpperCase()} / ${subjectText}`
  }
  if (providerText) {
    return providerText.toUpperCase()
  }
  return '-'
}

function ProfileIdentityCard({
  authUser,
  approvalStatus,
  profileSaving,
  profileError,
  profileNotice,
  displayNameInput,
  setDisplayNameInput,
  handleSaveMyProfile,
}) {
  const nicknameLabel = displayNameInput.trim() || authUser?.displayName?.trim() || authUser?.email || '미설정'

  return (
    <article className="control-card profile-card">
      <div className="card-head">
        <div>
          <h2>내 프로필</h2>
          <p className="sub">닉네임과 계정 정보를 관리하고, 아래에서 거래소 API 키를 별도로 등록합니다.</p>
        </div>
      </div>
      {profileError && <p className="status-error">{profileError}</p>}
      {profileNotice && <p className="status-success">{profileNotice}</p>}
      <div className="form-grid auth-settings-grid">
        <label className="form-field">
          <span>닉네임</span>
          <input
            type="text"
            value={displayNameInput}
            onChange={(event) => setDisplayNameInput(event.target.value)}
            placeholder="화면에 표시할 이름"
            disabled={profileSaving}
            maxLength={160}
          />
        </label>
        <label className="form-field">
          <span>이메일</span>
          <input
            type="text"
            value={authUser?.email ?? ''}
            disabled
            readOnly
          />
        </label>
      </div>
      <p className="sub compact">
        현재 표시 이름: {nicknameLabel}
      </p>
      <div className="profile-meta-grid">
        <div className="profile-meta-item">
          <span>로그인 방식</span>
          <strong className="mono">{formatProviderLabel(authUser?.provider, authUser?.providerUserId)}</strong>
        </div>
        <div className="profile-meta-item">
          <span>승인 상태</span>
          <strong className="mono">{approvalStatus || '-'}</strong>
        </div>
        <div className="profile-meta-item">
          <span>가입일</span>
          <strong className="mono">{authUser?.createdAt ? formatDateTime(authUser.createdAt) : '-'}</strong>
        </div>
        <div className="profile-meta-item">
          <span>최근 로그인</span>
          <strong className="mono">{authUser?.lastLoginAt ? formatDateTime(authUser.lastLoginAt) : '-'}</strong>
        </div>
      </div>
      <div className="button-row">
        <button
          className="primary-button"
          type="button"
          onClick={handleSaveMyProfile}
          disabled={profileSaving}
        >
          {profileSaving ? '저장 중...' : '프로필 저장'}
        </button>
      </div>
    </article>
  )
}

export default memo(ProfileIdentityCard)
