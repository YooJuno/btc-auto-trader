import { memo } from 'react'
import ExchangeCredentialsCard from '../components/settings/ExchangeCredentialsCard.jsx'
import MarketOverridesCard from '../components/settings/MarketOverridesCard.jsx'
import PerformanceCard from '../components/settings/PerformanceCard.jsx'
import UserPreferencesCard from '../components/settings/UserPreferencesCard.jsx'

function SettingsRoute({
  userPreferences,
  exchangeCredentials,
  marketOverrides,
  performance,
  authenticated = true,
  readOnly = false,
}) {
  return (
    <section className="workspace-grid workspace-grid--settings">
      <aside className="workspace-side">
        {authenticated ? (
          <>
            <UserPreferencesCard {...userPreferences} />
            <ExchangeCredentialsCard {...exchangeCredentials} />
          </>
        ) : (
          <article className="control-card card--elevated">
            <div className="card-head">
              <div>
                <h2>조회 전용 모드</h2>
                <p className="sub">로그인 전에는 매매 세팅 조회만 가능하고, 저장/조작은 비활성화됩니다.</p>
              </div>
              <span className="pill">READ ONLY</span>
            </div>
          </article>
        )}
        <MarketOverridesCard {...marketOverrides} readOnly={readOnly} />
        {authenticated && <PerformanceCard {...performance} />}
      </aside>
    </section>
  )
}

export default memo(SettingsRoute)
