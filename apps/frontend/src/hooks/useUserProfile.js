import { useCallback, useEffect, useState } from 'react'
import { requestJson } from '../utils/apiClient.js'

export function useUserProfile(authUser, setAuthUser) {
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileError, setProfileError] = useState(null)
  const [profileNotice, setProfileNotice] = useState(null)
  const [displayNameInput, setDisplayNameInput] = useState('')

  useEffect(() => {
    setDisplayNameInput(authUser?.displayName ?? '')
  }, [authUser?.id, authUser?.displayName])

  useEffect(() => {
    setProfileSaving(false)
    setProfileError(null)
    setProfileNotice(null)
  }, [authUser?.id])

  const handleSaveMyProfile = useCallback(async () => {
    if (!authUser?.id) {
      return false
    }
    setProfileSaving(true)
    setProfileError(null)
    setProfileNotice(null)
    try {
      const data = await requestJson(
        '/api/me/profile',
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            displayName: displayNameInput,
          }),
        },
        '프로필 저장 실패'
      )
      setAuthUser((prev) => ({
        ...(prev ?? {}),
        ...(data ?? {}),
      }))
      setDisplayNameInput(data?.displayName ?? '')
      setProfileNotice('개인 정보를 저장했습니다.')
      return true
    } catch (err) {
      setProfileError(err?.message ?? '프로필 저장 실패')
      return false
    } finally {
      setProfileSaving(false)
    }
  }, [authUser?.id, displayNameInput, setAuthUser])

  return {
    profileSaving,
    profileError,
    profileNotice,
    displayNameInput,
    setDisplayNameInput,
    handleSaveMyProfile,
  }
}
