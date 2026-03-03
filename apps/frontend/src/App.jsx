import { useCallback, useEffect, useState } from 'react'
import './App.css'
import DashboardRoute from './routes/DashboardRoute.jsx'
import SettingsRoute from './routes/SettingsRoute.jsx'
import OnboardingRoute from './routes/OnboardingRoute.jsx'
import AdminUsersRoute from './routes/AdminUsersRoute.jsx'
import AuthGate from './components/auth/AuthGate.jsx'
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
  ONBOARDING_ROUTE,
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
  const [onboardingState, setOnboardingState] = useState(null)
  const [onboardingBusy, setOnboardingBusy] = useState(false)
  const [onboardingError, setOnboardingError] = useState(null)
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
    resetAdminState,
  } = useAdminUsers(authUser)
  const [activeRoute, setActiveRoute] = useState(() => resolveAppRoute(window.location.pathname))

  const fetchBootstrap = useCallback(async () => {
    if (!authUser?.id) {
      return null
    }
    setBootstrapLoading(true)
    setBootstrapLoaded(false)
    setOnboardingError(null)
    try {
      const data = await requestJson('/api/me/bootstrap', {}, '초기화 정보 조회 실패')
      const settings = data?.settings ?? null
      setFeatureFlags(data?.features && typeof data.features === 'object' ? data.features : {})
      setOnboardingState(data?.onboarding ?? null)
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
      setOnboardingError(err?.message ?? '초기화 정보 조회 실패')
      return null
    } finally {
      setBootstrapLoading(false)
      setBootstrapLoaded(true)
    }
  }, [applyBootstrapExchangeCredentials, applyBootstrapSettings, authUser?.id, setAuthUser])

  const patchOnboardingState = useCallback(async (payload) => {
    const data = await requestJson(
      '/api/me/onboarding',
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      },
      '온보딩 상태 저장 실패'
    )
    setOnboardingState(data)
    return data
  }, [])

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
    setOnboardingState(null)
    setOnboardingError(null)
    resetUserAccountState()
    resetAdminState()
    fetchAuthProviders()
    checkAuthSession()
  }, [checkAuthSession, fetchAuthProviders, resetAdminState, resetUserAccountState, setAuthError, setAuthUser])

  const handleCompleteOnboardingProfile = useCallback(async () => {
    setOnboardingBusy(true)
    setOnboardingError(null)
    try {
      const saved = await handleSaveMySettings()
      if (!saved) {
        throw new Error('프로필 저장에 실패했습니다.')
      }
      await patchOnboardingState({ profileCompleted: true })
    } catch (err) {
      setOnboardingError(err?.message ?? '온보딩 1단계 저장 실패')
    } finally {
      setOnboardingBusy(false)
    }
  }, [handleSaveMySettings, patchOnboardingState])

  const handleCompleteOnboardingCredentials = useCallback(async () => {
    setOnboardingBusy(true)
    setOnboardingError(null)
    try {
      const saved = await handleSaveExchangeCredentials()
      if (!saved) {
        throw new Error('거래소 키 저장에 실패했습니다.')
      }
      const verified = await handleVerifyExchangeCredentials()
      if (!verified) {
        throw new Error('거래소 키 검증에 실패했습니다.')
      }
      await patchOnboardingState({ credentialsCompleted: true })
    } catch (err) {
      setOnboardingError(err?.message ?? '온보딩 2단계 저장 실패')
    } finally {
      setOnboardingBusy(false)
    }
  }, [handleSaveExchangeCredentials, handleVerifyExchangeCredentials, patchOnboardingState])

  const handleCompleteOnboardingStrategy = useCallback(async () => {
    setOnboardingBusy(true)
    setOnboardingError(null)
    try {
      await patchOnboardingState({ strategyCompleted: true })
    } catch (err) {
      setOnboardingError(err?.message ?? '온보딩 3단계 저장 실패')
    } finally {
      setOnboardingBusy(false)
    }
  }, [patchOnboardingState])

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
      setOnboardingState(null)
      setFeatureFlags({})
      setBootstrapLoading(false)
      setBootstrapLoaded(false)
      return
    }
    fetchBootstrap()
  }, [authUser?.id, fetchBootstrap, resetUserAccountState])

  const onboardingFeatureEnabled = Boolean(
    featureFlags?.['feature.onboarding.enabled'] ?? featureFlags?.onboardingEnabled ?? false
  )
  const adminApprovalFeatureEnabled = Boolean(
    featureFlags?.['feature.admin-approval.enabled'] ?? featureFlags?.adminApprovalEnabled ?? false
  )
  const onboardingCompleted = Boolean(onboardingState?.completed)
  const onboardingRequired = onboardingFeatureEnabled && !onboardingCompleted
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
    bootstrapLoading,
    bootstrapLoaded,
    onboardingRequired,
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
    if (!authUser || bootstrapLoading || !bootstrapLoaded) {
      return
    }
    if (onboardingRequired && activeRoute !== ONBOARDING_ROUTE) {
      navigateRoute(ONBOARDING_ROUTE)
      return
    }
    if (activeRoute === ADMIN_USERS_ROUTE && !canAccessAdmin) {
      navigateRoute(DASHBOARD_ROUTE)
      return
    }
    if (!onboardingRequired && activeRoute === ONBOARDING_ROUTE) {
      navigateRoute(DASHBOARD_ROUTE)
    }
  }, [
    activeRoute,
    authUser,
    bootstrapLoaded,
    bootstrapLoading,
    canAccessAdmin,
    navigateRoute,
    onboardingRequired,
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

  const userPreferencesProps = {
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
  }

  const exchangeCredentialsProps = {
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
  }

  const marketOverridesProps = {
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
  }

  const performanceProps = {
    fetchPerformance,
    performanceMode,
    setPerformanceMode,
    performanceInputs,
    setPerformanceInputs,
    performanceLoading,
    performanceError,
    performance,
    performanceTotal,
  }

  if (authChecking) {
    return <AuthGate checking authError={null} authProviders={[]} onProviderLogin={handleProviderLogin} />
  }

  if (!authUser) {
    return (
      <AuthGate
        checking={false}
        authError={authError}
        authProviders={authProviders}
        onProviderLogin={handleProviderLogin}
      />
    )
  }

  if (bootstrapLoading || !bootstrapLoaded) {
    return (
      <div className={`app ${tableDensityClass}`}>
        <section className="page-context">
          <div>
            <h2>초기화 중</h2>
            <p className="sub">사용자 설정과 온보딩 상태를 확인하고 있습니다.</p>
            {onboardingError && <p className="status-error">{onboardingError}</p>}
          </div>
          <span className="pill">LOADING</span>
        </section>
      </div>
    )
  }

  return (
    <div className={`app ${tableDensityClass}`}>
      <AppHeader
        activeRoute={activeRoute}
        onNavigateRoute={navigateRoute}
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
        canAccessAdmin={canAccessAdmin}
        onLogout={handleLogout}
      />

      <PageContextBanner activeRoute={activeRoute} />

      {onboardingRequired ? (
        <OnboardingRoute
          onboarding={onboardingState}
          userRiskProfile={userRiskProfile}
          userMarketsInput={userMarketsInput}
          setUserRiskProfile={setUserRiskProfile}
          setUserMarketsInput={setUserMarketsInput}
          onCompleteProfile={handleCompleteOnboardingProfile}
          exchangeAccessKeyInput={exchangeAccessKeyInput}
          exchangeSecretKeyInput={exchangeSecretKeyInput}
          setExchangeAccessKeyInput={setExchangeAccessKeyInput}
          setExchangeSecretKeyInput={setExchangeSecretKeyInput}
          onCompleteCredentials={handleCompleteOnboardingCredentials}
          onCompleteStrategy={handleCompleteOnboardingStrategy}
          onFinish={() => navigateRoute(DASHBOARD_ROUTE)}
          busy={onboardingBusy}
          error={onboardingError}
        />
      ) : activeRoute === ADMIN_USERS_ROUTE && canAccessAdmin ? (
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
        />
      ) : activeRoute === SETTINGS_ROUTE ? (
        <SettingsRoute
          userPreferences={userPreferencesProps}
          exchangeCredentials={exchangeCredentialsProps}
          marketOverrides={marketOverridesProps}
          performance={performanceProps}
        />
      ) : (
        <DashboardRoute
          cash={cash}
          totals={totals}
          loading={loading}
          positions={positions}
          manualTradeNotice={manualTradeNotice}
          mergedOrderHistory={mergedOrderHistory}
          feedError={feedError}
          onOpenManualTrade={openManualTrade}
          formatters={{
            formatKRW,
            formatCoin,
            formatPercent,
            formatDateTime,
            formatOrderStatus,
            formatFixed,
            truncateText,
            pnlClass,
          }}
        />
      )}

      <ManualTradeModal
        open={manualTradeOpen}
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
        formatters={{ formatCoin, formatKRW, toInputValue }}
      />
    </div>
  )
}

export default App
