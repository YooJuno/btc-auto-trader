import { useCallback, useEffect, useState } from 'react'

const AUTH_REQUEST_TIMEOUT_MS = 8000

function fetchWithTimeout(input, init = {}, timeoutMs = AUTH_REQUEST_TIMEOUT_MS) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)
  const mergedInit = { ...init, signal: controller.signal }

  return fetch(input, mergedInit).finally(() => {
    window.clearTimeout(timeoutId)
  })
}

export function useAuthSession() {
  const [authChecking, setAuthChecking] = useState(true)
  const [authUser, setAuthUser] = useState(null)
  const [authProviders, setAuthProviders] = useState([])
  const [authError, setAuthError] = useState(null)

  const fetchAuthProviders = useCallback(async () => {
    try {
      const response = await fetchWithTimeout('/api/auth/providers')
      if (!response.ok) {
        throw new Error(`로그인 공급자 조회 오류 ${response.status}`)
      }
      const data = await response.json()
      setAuthProviders(Array.isArray(data) ? data : [])
    } catch {
      setAuthProviders([])
    }
  }, [])

  const checkAuthSession = useCallback(async () => {
    setAuthChecking(true)
    setAuthError(null)
    try {
      const response = await fetchWithTimeout('/api/me')
      if (response.status === 401) {
        setAuthUser(null)
        return
      }
      if (!response.ok) {
        throw new Error(`로그인 상태 확인 오류 ${response.status}`)
      }
      const data = await response.json()
      setAuthUser(data)
    } catch (err) {
      setAuthUser(null)
      if (err?.name === 'AbortError') {
        setAuthError('로그인 상태 확인 시간이 초과되었습니다. 백엔드 연결을 확인해주세요.')
      } else {
        setAuthError(err?.message ?? '로그인 상태 확인 실패')
      }
    } finally {
      setAuthChecking(false)
    }
  }, [])

  const handleProviderLogin = useCallback((authorizationUrl) => {
    if (!authorizationUrl) {
      return
    }
    window.location.assign(authorizationUrl)
  }, [])

  useEffect(() => {
    const query = new URLSearchParams(window.location.search)
    if (query.get('loginError') === 'true') {
      setAuthError('OAuth 로그인에 실패했습니다. 다시 시도해주세요.')
    } else {
      setAuthError(null)
    }
    fetchAuthProviders()
    checkAuthSession()
  }, [checkAuthSession, fetchAuthProviders])

  return {
    authChecking,
    authUser,
    authProviders,
    authError,
    setAuthUser,
    setAuthError,
    fetchAuthProviders,
    checkAuthSession,
    handleProviderLogin,
  }
}
