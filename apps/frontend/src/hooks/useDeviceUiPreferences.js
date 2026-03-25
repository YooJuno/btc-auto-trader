import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  DASHBOARD_ROUTE,
  PROFILE_ROUTE,
  SETTINGS_ROUTE,
  UI_DENSITY_COMFORTABLE,
  UI_DENSITY_COMPACT,
  UI_SCOPE_COMMON,
  UI_SCOPE_DESKTOP,
  UI_SCOPE_MOBILE,
} from '../constants/tradingUi.js'
import {
  buildUiPrefsPayload,
  detectDeviceKind,
  normalizePathname,
  normalizePollingIntervalMs,
  normalizeRefreshSeconds,
  normalizeRouteToken,
  normalizeTableDensity,
  resolveEffectiveUiPrefs,
  updateUiPrefsSectionValue,
} from '../utils/tradingUi.js'

export const useDeviceUiPreferences = ({
  userUiPrefs,
  setUserUiPrefs,
  authUser,
  userSettings,
  navigateRoute,
}) => {
  const [deviceKind, setDeviceKind] = useState(() => detectDeviceKind())
  const defaultRouteAppliedRef = useRef(false)

  const authSessionKey = useMemo(() => {
    if (!authUser) {
      return null
    }
    const provider = String(authUser.provider ?? '').trim()
    const providerUserId = String(authUser.providerUserId ?? '').trim()
    const email = String(authUser.email ?? '').trim()
    return `${provider}:${providerUserId}:${email}`
  }, [authUser])

  const normalizedUserUiPrefs = useMemo(() => buildUiPrefsPayload(userUiPrefs), [userUiPrefs])
  const effectiveUiPrefs = useMemo(
    () => resolveEffectiveUiPrefs(normalizedUserUiPrefs, deviceKind),
    [normalizedUserUiPrefs, deviceKind]
  )
  const pollingIntervalMs = useMemo(
    () => normalizePollingIntervalMs(effectiveUiPrefs.refreshSec),
    [effectiveUiPrefs.refreshSec]
  )
  const tableDensityClass = effectiveUiPrefs.tableDensity === UI_DENSITY_COMPACT
    ? 'app--density-compact'
    : 'app--density-comfortable'

  const setUiPrefValue = useCallback((scope, key, value) => {
    setUserUiPrefs((prev) => updateUiPrefsSectionValue(prev, scope, key, value))
  }, [setUserUiPrefs])

  const handleRefreshSecChange = useCallback((event) => {
    const nextValue = normalizeRefreshSeconds(event.target.value)
    setUiPrefValue(UI_SCOPE_COMMON, 'refreshSec', nextValue)
  }, [setUiPrefValue])

  const handleDefaultRouteChange = useCallback((scope, value) => {
    const nextValue = normalizeRouteToken(value, DASHBOARD_ROUTE)
    setUiPrefValue(scope, 'defaultRoute', nextValue)
  }, [setUiPrefValue])

  const handleTableDensityChange = useCallback((scope, value) => {
    const nextValue = normalizeTableDensity(value, UI_DENSITY_COMFORTABLE)
    setUiPrefValue(scope, 'tableDensity', nextValue)
  }, [setUiPrefValue])

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined
    }
    const mediaQuery = window.matchMedia('(max-width: 860px)')
    const handleMediaChange = (event) => {
      setDeviceKind(event.matches ? UI_SCOPE_MOBILE : UI_SCOPE_DESKTOP)
    }
    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', handleMediaChange)
      return () => mediaQuery.removeEventListener('change', handleMediaChange)
    }
    mediaQuery.addListener(handleMediaChange)
    return () => mediaQuery.removeListener(handleMediaChange)
  }, [])

  useEffect(() => {
    defaultRouteAppliedRef.current = false
  }, [authSessionKey])

  useEffect(() => {
    if (!authUser || !userSettings || defaultRouteAppliedRef.current) {
      return
    }
    const currentPath = normalizePathname(window.location.pathname)
    if (currentPath === '/') {
      navigateRoute(effectiveUiPrefs.defaultRoute)
    }
    defaultRouteAppliedRef.current = true
  }, [authUser, effectiveUiPrefs.defaultRoute, navigateRoute, userSettings])

  const commonUiPrefs = normalizedUserUiPrefs?.[UI_SCOPE_COMMON] ?? {}
  const mobileUiPrefs = normalizedUserUiPrefs?.[UI_SCOPE_MOBILE] ?? {}
  const desktopUiPrefs = normalizedUserUiPrefs?.[UI_SCOPE_DESKTOP] ?? {}
  const deviceLabel = deviceKind === UI_SCOPE_MOBILE ? '스마트폰' : 'PC'
  const effectiveRouteLabel = effectiveUiPrefs.defaultRoute === SETTINGS_ROUTE
    ? '매매 세팅'
    : effectiveUiPrefs.defaultRoute === PROFILE_ROUTE
      ? '개인 정보'
      : '실시간 현황'
  const effectiveDensityLabel = effectiveUiPrefs.tableDensity === UI_DENSITY_COMPACT ? '컴팩트' : '컴포터블'

  return {
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
  }
}
