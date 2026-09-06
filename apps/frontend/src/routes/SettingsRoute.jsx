import { memo } from 'react'
import MarketOverridesCard from '../components/settings/MarketOverridesCard.jsx'
import UserPreferencesCard from '../components/settings/UserPreferencesCard.jsx'

function SettingsRoute({
  userPreferences,
  marketOverrides,
  authenticated = true,
  readOnly = false,
}) {
  return (
    <>
        {authenticated ? (
          <>
            <MarketOverridesCard {...marketOverrides} readOnly={readOnly} />
            <UserPreferencesCard {...userPreferences} />
          </>
        ) : (
          <article className="control-card">
            <div className="card-head">
              <div>
                <h2>조회 전용 모드</h2>
                <p className="sub">로그인 전에는 매매 세팅 조회만 가능하고, 저장/조작은 비활성화됩니다.</p>
              </div>
            </div>
          </article>
        )}
      </>
  )
}

export default memo(SettingsRoute)
