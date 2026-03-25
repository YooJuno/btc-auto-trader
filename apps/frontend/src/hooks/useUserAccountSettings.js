import { useCallback, useState } from 'react'
import {
  buildApiErrorMessage,
  buildUiPrefsPayload,
  normalizeMarket,
  normalizeProfileValue,
  parseUserMarketsInput,
} from '../utils/tradingUi.js'
import { DEFAULT_MARKET_PROFILE } from '../constants/tradingUi.js'

export function useUserAccountSettings(authUser) {
  const [settingsLoading, setSettingsLoading] = useState(false)
  const [settingsSaving, setSettingsSaving] = useState(false)
  const [userSettings, setUserSettings] = useState(null)
  const [userSettingsError, setUserSettingsError] = useState(null)
  const [userSettingsNotice, setUserSettingsNotice] = useState(null)
  const [userRiskProfile, setUserRiskProfile] = useState(DEFAULT_MARKET_PROFILE)
  const [userMarketsInput, setUserMarketsInput] = useState('')
  const [userUiPrefs, setUserUiPrefs] = useState({})

  const [exchangeCredentialStatus, setExchangeCredentialStatus] = useState(null)
  const [exchangeCredentialLoading, setExchangeCredentialLoading] = useState(false)
  const [exchangeCredentialSaving, setExchangeCredentialSaving] = useState(false)
  const [exchangeCredentialVerifying, setExchangeCredentialVerifying] = useState(false)
  const [exchangeCredentialError, setExchangeCredentialError] = useState(null)
  const [exchangeCredentialNotice, setExchangeCredentialNotice] = useState(null)
  const [exchangeAccessKeyInput, setExchangeAccessKeyInput] = useState('')
  const [exchangeSecretKeyInput, setExchangeSecretKeyInput] = useState('')

  const resetUserAccountState = useCallback(() => {
    setUserSettings(null)
    setUserSettingsError(null)
    setUserSettingsNotice(null)
    setUserRiskProfile(DEFAULT_MARKET_PROFILE)
    setUserMarketsInput('')
    setUserUiPrefs({})
    setExchangeCredentialStatus(null)
    setExchangeCredentialError(null)
    setExchangeCredentialNotice(null)
    setExchangeAccessKeyInput('')
    setExchangeSecretKeyInput('')
  }, [])

  const applyBootstrapSettings = useCallback((settings) => {
    if (!settings) {
      return
    }
    const markets = Array.isArray(settings?.markets) ? settings.markets.map(normalizeMarket).filter(Boolean) : []
    setUserSettings(settings)
    setUserRiskProfile(normalizeProfileValue(settings?.riskProfile) || DEFAULT_MARKET_PROFILE)
    setUserMarketsInput(markets.join(', '))
    setUserUiPrefs(buildUiPrefsPayload(settings?.uiPrefs))
  }, [])

  const applyBootstrapExchangeCredentials = useCallback((exchangeCredentials) => {
    if (!exchangeCredentials) {
      return
    }
    setExchangeCredentialStatus(exchangeCredentials)
  }, [])

  const syncUserMarkets = useCallback((markets) => {
    const normalized = Array.isArray(markets) ? markets.map(normalizeMarket).filter(Boolean) : []
    setUserSettings((prev) => ({
      ...(prev ?? {}),
      markets: normalized,
    }))
    setUserMarketsInput(normalized.join(', '))
  }, [])

  const fetchMySettings = useCallback(async () => {
    if (!authUser) {
      return
    }
    setSettingsLoading(true)
    setUserSettingsError(null)
    setUserSettingsNotice(null)
    try {
      const response = await fetch('/api/me/settings')
      if (!response.ok) {
        throw new Error(`내 설정 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      applyBootstrapSettings(data)
    } catch (err) {
      setUserSettingsError(err?.message ?? '내 설정 조회 실패')
    } finally {
      setSettingsLoading(false)
    }
  }, [applyBootstrapSettings, authUser])

  const handleSaveMySettings = useCallback(async () => {
    setSettingsSaving(true)
    setUserSettingsError(null)
    setUserSettingsNotice(null)
    try {
      const payload = {
        markets: parseUserMarketsInput(userMarketsInput),
        riskProfile: normalizeProfileValue(userRiskProfile) || DEFAULT_MARKET_PROFILE,
        uiPrefs: buildUiPrefsPayload(userUiPrefs),
      }
      const response = await fetch('/api/me/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (!response.ok) {
        const errorPayload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(errorPayload, `내 설정 저장 오류 ${response.status}`)
        throw new Error(message)
      }
      const data = await response.json()
      applyBootstrapSettings(data)
      setUserSettingsNotice('내 인터페이스 설정을 저장했습니다.')
      return true
    } catch (err) {
      setUserSettingsError(err?.message ?? '내 설정 저장 실패')
      return false
    } finally {
      setSettingsSaving(false)
    }
  }, [applyBootstrapSettings, userMarketsInput, userRiskProfile, userUiPrefs])

  const fetchExchangeCredentialStatus = useCallback(async () => {
    if (!authUser) {
      return
    }
    setExchangeCredentialLoading(true)
    setExchangeCredentialError(null)
    try {
      const response = await fetch('/api/me/exchange-credentials')
      if (!response.ok) {
        throw new Error(`거래소 키 상태 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setExchangeCredentialStatus(data)
    } catch (err) {
      setExchangeCredentialStatus(null)
      setExchangeCredentialError(err?.message ?? '거래소 키 상태 조회 실패')
    } finally {
      setExchangeCredentialLoading(false)
    }
  }, [authUser])

  const handleSaveExchangeCredentials = useCallback(async () => {
    if (!exchangeAccessKeyInput.trim() || !exchangeSecretKeyInput.trim()) {
      setExchangeCredentialError('access key와 secret key를 모두 입력해주세요.')
      return false
    }

    setExchangeCredentialSaving(true)
    setExchangeCredentialError(null)
    setExchangeCredentialNotice(null)
    try {
      const response = await fetch('/api/me/exchange-credentials', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          accessKey: exchangeAccessKeyInput.trim(),
          secretKey: exchangeSecretKeyInput.trim(),
        }),
      })
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(payload, `거래소 키 저장 실패 ${response.status}`)
        throw new Error(message)
      }
      const data = await response.json()
      setExchangeCredentialStatus(data)
      setExchangeSecretKeyInput('')
      setExchangeCredentialNotice('거래소 API 키를 저장했습니다.')
      return true
    } catch (err) {
      setExchangeCredentialError(err?.message ?? '거래소 키 저장 실패')
      return false
    } finally {
      setExchangeCredentialSaving(false)
    }
  }, [exchangeAccessKeyInput, exchangeSecretKeyInput])

  const handleVerifyExchangeCredentials = useCallback(async () => {
    setExchangeCredentialVerifying(true)
    setExchangeCredentialError(null)
    setExchangeCredentialNotice(null)
    try {
      const response = await fetch('/api/me/exchange-credentials/verify', { method: 'POST' })
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(payload, `거래소 키 검증 실패 ${response.status}`)
        throw new Error(message)
      }
      const data = await response.json()
      const accountCount = Number.isFinite(data?.accountCount) ? data.accountCount : 0
      setExchangeCredentialNotice(`거래소 키 검증 성공 (${accountCount}개 계좌 조회)`)
      fetchExchangeCredentialStatus()
      return true
    } catch (err) {
      setExchangeCredentialError(err?.message ?? '거래소 키 검증 실패')
      return false
    } finally {
      setExchangeCredentialVerifying(false)
    }
  }, [fetchExchangeCredentialStatus])

  const handleDeleteExchangeCredentials = useCallback(async () => {
    if (!window.confirm('저장된 거래소 API 키를 삭제할까요?')) {
      return
    }
    setExchangeCredentialSaving(true)
    setExchangeCredentialError(null)
    setExchangeCredentialNotice(null)
    try {
      const response = await fetch('/api/me/exchange-credentials', { method: 'DELETE' })
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        const message = buildApiErrorMessage(payload, `거래소 키 삭제 실패 ${response.status}`)
        throw new Error(message)
      }
      setExchangeCredentialStatus(null)
      setExchangeAccessKeyInput('')
      setExchangeSecretKeyInput('')
      setExchangeCredentialNotice('저장된 거래소 API 키를 삭제했습니다.')
      fetchExchangeCredentialStatus()
    } catch (err) {
      setExchangeCredentialError(err?.message ?? '거래소 키 삭제 실패')
    } finally {
      setExchangeCredentialSaving(false)
    }
  }, [fetchExchangeCredentialStatus])

  return {
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
    fetchExchangeCredentialStatus,
    handleSaveExchangeCredentials,
    handleVerifyExchangeCredentials,
    handleDeleteExchangeCredentials,
    applyBootstrapSettings,
    applyBootstrapExchangeCredentials,
    syncUserMarkets,
    resetUserAccountState,
  }
}
