import ExchangeCredentialsCard from '../components/settings/ExchangeCredentialsCard.jsx'
import MarketOverridesCard from '../components/settings/MarketOverridesCard.jsx'
import PerformanceCard from '../components/settings/PerformanceCard.jsx'
import UserPreferencesCard from '../components/settings/UserPreferencesCard.jsx'

function SettingsRoute({ userPreferences, exchangeCredentials, marketOverrides, performance }) {
  return (
    <section className="workspace-grid workspace-grid--settings">
      <aside className="workspace-side">
        <UserPreferencesCard {...userPreferences} />
        <ExchangeCredentialsCard {...exchangeCredentials} />
        <MarketOverridesCard {...marketOverrides} />
        <PerformanceCard {...performance} />
      </aside>
    </section>
  )
}

export default SettingsRoute
