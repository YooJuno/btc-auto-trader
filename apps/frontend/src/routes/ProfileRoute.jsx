import { memo } from 'react'
import ProfileIdentityCard from '../components/profile/ProfileIdentityCard.jsx'
import ExchangeCredentialsCard from '../components/settings/ExchangeCredentialsCard.jsx'

function ProfileRoute({
  profile,
  exchangeCredentials,
  authenticated = true,
}) {
  return (
    <section className="workspace-grid workspace-grid--settings">
      <aside className="workspace-side">
        {authenticated ? (
          <>
            <ProfileIdentityCard {...profile} />
            <ExchangeCredentialsCard {...exchangeCredentials} />
          </>
        ) : (
          <article className="control-card">
            <div className="card-head">
              <div>
                <h2>개인 정보</h2>
                <p className="sub">닉네임과 거래소 API 키는 로그인 후 관리할 수 있습니다.</p>
              </div>
              <span className="pill">LOGIN</span>
            </div>
          </article>
        )}
      </aside>
    </section>
  )
}

export default memo(ProfileRoute)
