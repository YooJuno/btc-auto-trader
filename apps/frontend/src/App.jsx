import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'
import DashboardRoute from './routes/DashboardRoute.jsx'
import AppHeader from './components/layout/AppHeader.jsx'
import PageContextBanner from './components/layout/PageContextBanner.jsx'
import ManualTradeModal from './components/trade/ManualTradeModal.jsx'
import { useDeviceUiPreferences } from './hooks/useDeviceUiPreferences.js'
import { useAuthSession } from './hooks/useAuthSession.js'
import { useUserAccountSettings } from './hooks/useUserAccountSettings.js'
import { useAdminUsers } from './hooks/useAdminUsers.js'
import { useTradingWorkspace } from './hooks/useTradingWorkspace.js'
import {
  ADMIN_USERS_ROUTE,
  DASHBOARD_ROUTE,
  SETTINGS_ROUTE,
} from './constants/tradingUi.js'
import {
  formatCoin,
  formatDateTime,
  formatFixed,
  formatKRW,
  formatOrderStatus,
  formatPercent,
  pnlClass,
  resolveAppPath,
  resolveAppRoute,
  toInputValue,
  truncateText,
} from './utils/tradingUi.js'
import { requestJson } from './utils/apiClient.js'

const SettingsRoute = lazy(() => import('./routes/SettingsRoute.jsx'))
const AdminUsersRoute = lazy(() => import('./routes/AdminUsersRoute.jsx'))
const DASHBOARD_FORMATTERS = {
  formatKRW,
  formatCoin,
  formatPercent,
  formatDateTime,
  formatOrderStatus,
  formatFixed,
  truncateText,
  pnlClass,
}
const MANUAL_TRADE_FORMATTERS = {
  formatCoin,
  formatKRW,
  toInputValue,
}

function App() {
  const {
    authChecking,
    authUser,
    authProviders,
    authError,
    setAuthUser,
    setAuthError,
    fetchAuthProviders,
    checkAuthSession,
    handleProviderLogin,
  } = useAuthSession()
  const [bootstrapLoading, setBootstrapLoading] = useState(false)
  const [bootstrapLoaded, setBootstrapLoaded] = useState(false)
  const [featureFlags, setFeatureFlags] = useState({})
  const [bootstrapError, setBootstrapError] = useState(null)
  const {
    settingsLoading,
    settingsSaving,
    userSettings,
    userSettingsError,
    userSettingsNotice,
    userRiskProfile,
    userMarketsInput,
    userUiPrefs,
    setUserRiskProfile,
    setUserMarketsInput,
    setUserUiPrefs,
    fetchMySettings,
    handleSaveMySettings,
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
    applyBootstrapSettings,
    applyBootstrapExchangeCredentials,
    resetUserAccountState,
  } = useUserAccountSettings(authUser)
  const {
    adminLoading,
    adminError,
    adminNotice,
    adminQuery,
    adminStatusFilter,
    adminUsers,
    setAdminQuery,
    setAdminStatusFilter,
    fetchAdminUsers,
    updateApprovalStatus,
    deleteAdminUser,
    resetAdminState,
  } = useAdminUsers(authUser)
  const [activeRoute, setActiveRoute] = useState(() => resolveAppRoute(window.location.pathname))

  const fetchBootstrap = useCallback(async () => {
    if (!authUser?.id) {
      return null
    }
    setBootstrapLoading(true)
    setBootstrapLoaded(false)
    setBootstrapError(null)
    try {
      const data = await requestJson('/api/me/bootstrap', {}, '초기화 정보 조회 실패')
      const settings = data?.settings ?? null
      setFeatureFlags(data?.features && typeof data.features === 'object' ? data.features : {})
      setAuthUser((prev) => ({
        ...(prev ?? {}),
        ...(data?.user ?? {}),
      }))
      if (settings) {
        applyBootstrapSettings(settings)
      }
      if (data?.exchangeCredentials) {
        applyBootstrapExchangeCredentials(data.exchangeCredentials)
      }
      return data
    } catch (err) {
      setBootstrapError(err?.message ?? '초기화 정보 조회 실패')
      return null
    } finally {
      setBootstrapLoading(false)
      setBootstrapLoaded(true)
    }
  }, [applyBootstrapExchangeCredentials, applyBootstrapSettings, authUser?.id, setAuthUser])

  const handleLogout = useCallback(async () => {
    try {
      await fetch('/api/auth/logout', { method: 'POST' })
    } catch {
      // no-op
    }

    setAuthUser(null)
    setAuthError(null)
    setBootstrapLoading(false)
    setBootstrapLoaded(false)
    setFeatureFlags({})
    setBootstrapError(null)
    resetUserAccountState()
    resetAdminState()
    fetchAuthProviders()
    checkAuthSession()
  }, [checkAuthSession, fetchAuthProviders, resetAdminState, resetUserAccountState, setAuthError, setAuthUser])

  const navigateRoute = useCallback((route) => {
    const nextPath = resolveAppPath(route)
    if (window.location.pathname !== nextPath) {
      window.history.pushState({}, '', nextPath)
    }
    setActiveRoute(resolveAppRoute(nextPath))
  }, [])

  const {
    commonUiPrefs,
    mobileUiPrefs,
    desktopUiPrefs,
    deviceLabel,
    effectiveRouteLabel,
    effectiveDensityLabel,
    pollingIntervalMs,
    tableDensityClass,
    handleRefreshSecChange,
    handleDefaultRouteChange,
    handleTableDensityChange,
  } = useDeviceUiPreferences({
    userUiPrefs,
    setUserUiPrefs,
    authUser,
    userSettings,
    navigateRoute,
  })

  useEffect(() => {
    const handlePopstate = () => {
      setActiveRoute(resolveAppRoute(window.location.pathname))
    }

    window.addEventListener('popstate', handlePopstate)
    return () => {
      window.removeEventListener('popstate', handlePopstate)
    }
  }, [])

  useEffect(() => {
    if (!authUser?.id) {
      resetUserAccountState()
      setFeatureFlags({})
      setBootstrapLoading(false)
      setBootstrapLoaded(false)
      return
    }
    fetchBootstrap()
  }, [authUser?.id, fetchBootstrap, resetUserAccountState])

  const adminApprovalFeatureEnabled = Boolean(
    featureFlags?.['feature.admin-approval.enabled'] ?? featureFlags?.adminApprovalEnabled ?? false
  )
  const canAccessAdmin = Boolean(authUser?.owner) && adminApprovalFeatureEnabled
  const approvalStatus = authUser?.approvalStatus ?? '-'

  const {
    loading,
    engineStatus,
    engineBusy,
    engineError,
    strategy,
    strategyError,
    ratioError,
    presetError,
    ratioPresets,
    selectedRatioPresetByMarket,
    marketRows,
    marketConfigSaving,
    marketConfigLoading,
    marketConfigError,
    marketConfigNotice,
    newMarketInput,
    marketSuggestOpen,
    marketSuggestIndex,
    expandedMarket,
    manualTradeOpen,
    manualTradeMarket,
    manualTradeSide,
    manualTradeType,
    manualTradePrice,
    manualTradeVolume,
    manualTradeFunds,
    manualTradeBusy,
    manualTradeError,
    manualTradeNotice,
    mergedOrderHistory,
    feedError,
    performance,
    performanceMode,
    performanceInputs,
    performanceLoading,
    performanceError,
    positions,
    cash,
    totals,
    updatedAt,
    connectionClass,
    connectionLabel,
    engineClass,
    marketRowsDirty,
    marketSuggestions,
    performanceTotal,
    manualTradePosition,
    cashKrw,
    setRatioError,
    setSelectedRatioPresetByMarket,
    setMarketRows,
    setMarketConfigError,
    setMarketConfigNotice,
    setNewMarketInput,
    setMarketSuggestOpen,
    setMarketSuggestIndex,
    setExpandedMarket,
    setPerformanceMode,
    setPerformanceInputs,
    setManualTradeSide,
    setManualTradeType,
    setManualTradePrice,
    setManualTradeVolume,
    setManualTradeFunds,
    fetchPerformance,
    handleSelectMarketSuggestion,
    handleAddMarket,
    handleMarketReload,
    handleSaveMarketOverrides,
    openManualTrade,
    closeManualTrade,
    handleManualTradeSubmit,
    handleEngineStart,
    handleEngineStop,
  } = useTradingWorkspace({
    authUser,
    activeRoute,
    bootstrapLoading,
    bootstrapLoaded,
    pollingIntervalMs,
  })

  const handleEngineToggle = useCallback(() => {
    if (engineStatus) {
      handleEngineStop()
      return
    }
    handleEngineStart()
  }, [engineStatus, handleEngineStart, handleEngineStop])

  useEffect(() => {
    if (!authUser) {
      if (activeRoute !== DASHBOARD_ROUTE) {
        navigateRoute(DASHBOARD_ROUTE)
      }
      return
    }
    if (bootstrapLoading || !bootstrapLoaded) {
      return
    }
    if (activeRoute === ADMIN_USERS_ROUTE && !canAccessAdmin) {
      navigateRoute(DASHBOARD_ROUTE)
    }
  }, [
    activeRoute,
    authUser,
    bootstrapLoaded,
    bootstrapLoading,
    canAccessAdmin,
    navigateRoute,
  ])

  useEffect(() => {
    if (!authUser?.owner) {
      resetAdminState()
      return
    }
    if (activeRoute === ADMIN_USERS_ROUTE) {
      fetchAdminUsers()
    }
  }, [activeRoute, authUser?.owner, fetchAdminUsers, resetAdminState])

  const userPreferencesProps = useMemo(() => ({
    settingsLoading,
    settingsSaving,
    userSettings,
    userSettingsError,
    userSettingsNotice,
    userRiskProfile,
    userMarketsInput,
    setUserRiskProfile,
    setUserMarketsInput,
    handleSaveMySettings,
    fetchMySettings,
    commonUiPrefs,
    mobileUiPrefs,
    desktopUiPrefs,
    handleRefreshSecChange,
    handleDefaultRouteChange,
    handleTableDensityChange,
    deviceLabel,
    effectiveRouteLabel,
    effectiveDensityLabel,
    pollingIntervalMs,
  }), [
    commonUiPrefs,
    desktopUiPrefs,
    deviceLabel,
    effectiveDensityLabel,
    effectiveRouteLabel,
    fetchMySettings,
    handleDefaultRouteChange,
    handleRefreshSecChange,
    handleSaveMySettings,
    handleTableDensityChange,
    mobileUiPrefs,
    pollingIntervalMs,
    setUserMarketsInput,
    setUserRiskProfile,
    settingsLoading,
    settingsSaving,
    userMarketsInput,
    userRiskProfile,
    userSettings,
    userSettingsError,
    userSettingsNotice,
  ])

  const exchangeCredentialsProps = useMemo(() => ({
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
  }), [
    exchangeAccessKeyInput,
    exchangeCredentialError,
    exchangeCredentialLoading,
    exchangeCredentialNotice,
    exchangeCredentialSaving,
    exchangeCredentialStatus,
    exchangeCredentialVerifying,
    exchangeSecretKeyInput,
    handleDeleteExchangeCredentials,
    handleSaveExchangeCredentials,
    handleVerifyExchangeCredentials,
    setExchangeAccessKeyInput,
    setExchangeSecretKeyInput,
  ])

  const marketOverridesProps = useMemo(() => ({
    strategyError,
    ratioError,
    presetError,
    ratioPresets,
    selectedRatioPresetByMarket,
    setSelectedRatioPresetByMarket,
    marketRows,
    marketConfigSaving,
    marketConfigLoading,
    marketConfigError,
    marketConfigNotice,
    marketRowsDirty,
    newMarketInput,
    setNewMarketInput,
    marketSuggestions,
    marketSuggestOpen,
    setMarketSuggestOpen,
    marketSuggestIndex,
    setMarketSuggestIndex,
    expandedMarket,
    setExpandedMarket,
    strategy,
    setRatioError,
    setMarketRows,
    setMarketConfigError,
    setMarketConfigNotice,
    handleSelectMarketSuggestion,
    handleAddMarket,
    handleMarketReload,
    onSaveMarketOverrides: handleSaveMarketOverrides,
  }), [
    expandedMarket,
    handleAddMarket,
    handleMarketReload,
    handleSaveMarketOverrides,
    handleSelectMarketSuggestion,
    marketConfigError,
    marketConfigLoading,
    marketConfigNotice,
    marketConfigSaving,
    marketRows,
    marketRowsDirty,
    marketSuggestIndex,
    marketSuggestOpen,
    marketSuggestions,
    newMarketInput,
    presetError,
    ratioError,
    ratioPresets,
    setExpandedMarket,
    setMarketConfigError,
    setMarketConfigNotice,
    setMarketRows,
    setMarketSuggestIndex,
    setMarketSuggestOpen,
    setNewMarketInput,
    setRatioError,
    setSelectedRatioPresetByMarket,
    selectedRatioPresetByMarket,
    strategy,
    strategyError,
  ])

  const performanceProps = useMemo(() => ({
    fetchPerformance,
    performanceMode,
    setPerformanceMode,
    performanceInputs,
    setPerformanceInputs,
    performanceLoading,
    performanceError,
    performance,
    performanceTotal,
  }), [
    fetchPerformance,
    performance,
    performanceError,
    performanceInputs,
    performanceLoading,
    performanceMode,
    performanceTotal,
    setPerformanceInputs,
    setPerformanceMode,
  ])

  const authenticated = Boolean(authUser?.id)
  const effectiveRoute = authenticated ? activeRoute : DASHBOARD_ROUTE
  const routeLoadingFallback = (
    <section className="table-card">
      <div className="empty-state">화면을 불러오는 중입니다…</div>
    </section>
  )

  if (authenticated && (bootstrapLoading || !bootstrapLoaded)) {
    return (
      <div className={`app ${tableDensityClass}`}>
        <section className="page-context">
          <div>
            <h2>초기화 중</h2>
            <p className="sub">사용자 설정을 확인하고 있습니다.</p>
            {bootstrapError && <p className="status-error">{bootstrapError}</p>}
          </div>
          <span className="pill">LOADING</span>
        </section>
      </div>
    )
  }

  return (
    <div className={`app ${tableDensityClass}`}>
      <AppHeader
        activeRoute={effectiveRoute}
        onNavigateRoute={navigateRoute}
        authChecking={authChecking}
        authProviders={authProviders}
        authError={authError}
        onProviderLogin={handleProviderLogin}
        engineClass={engineClass}
        engineError={engineError}
        engineBusy={engineBusy}
        engineStatus={engineStatus}
        onEngineToggle={handleEngineToggle}
        updatedAt={updatedAt}
        authUser={authUser}
        connectionClass={connectionClass}
        connectionLabel={connectionLabel}
        approvalStatus={approvalStatus}
        canAccessAdmin={authenticated && canAccessAdmin}
        onLogout={handleLogout}
      />

      <PageContextBanner activeRoute={effectiveRoute} />

      {effectiveRoute === ADMIN_USERS_ROUTE && authenticated && canAccessAdmin ? (
        <Suspense fallback={routeLoadingFallback}>
          <AdminUsersRoute
            loading={adminLoading}
            error={adminError}
            notice={adminNotice}
            query={adminQuery}
            setQuery={setAdminQuery}
            statusFilter={adminStatusFilter}
            setStatusFilter={setAdminStatusFilter}
            users={adminUsers}
            onRefresh={fetchAdminUsers}
            onApprove={(userId) => updateApprovalStatus(userId, 'APPROVED')}
            onSuspend={(userId) => updateApprovalStatus(userId, 'SUSPENDED')}
            onDelete={deleteAdminUser}
          />
        </Suspense>
      ) : effectiveRoute === SETTINGS_ROUTE && authenticated ? (
        <Suspense fallback={routeLoadingFallback}>
          <SettingsRoute
            userPreferences={userPreferencesProps}
            exchangeCredentials={exchangeCredentialsProps}
            marketOverrides={marketOverridesProps}
            performance={performanceProps}
          />
        </Suspense>
      ) : (
        <DashboardRoute
          authRequired={!authenticated}
          cash={cash}
          totals={totals}
          loading={loading}
          positions={positions}
          manualTradeNotice={manualTradeNotice}
          mergedOrderHistory={mergedOrderHistory}
          feedError={feedError}
          onOpenManualTrade={openManualTrade}
          formatters={DASHBOARD_FORMATTERS}
        />
      )}

      <ManualTradeModal
        open={authenticated && manualTradeOpen}
        busy={manualTradeBusy}
        market={manualTradeMarket}
        side={manualTradeSide}
        type={manualTradeType}
        price={manualTradePrice}
        volume={manualTradeVolume}
        funds={manualTradeFunds}
        position={manualTradePosition}
        cashKrw={cashKrw}
        error={manualTradeError}
        onClose={closeManualTrade}
        onSubmit={handleManualTradeSubmit}
        setSide={setManualTradeSide}
        setType={setManualTradeType}
        setPrice={setManualTradePrice}
        setVolume={setManualTradeVolume}
        setFunds={setManualTradeFunds}
        formatters={MANUAL_TRADE_FORMATTERS}
      />
    </div>
  )
}

export default App
