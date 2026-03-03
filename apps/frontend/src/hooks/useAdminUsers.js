import { useCallback, useState } from 'react'
import { requestJson } from '../utils/apiClient.js'

export function useAdminUsers(authUser) {
  const [adminLoading, setAdminLoading] = useState(false)
  const [adminError, setAdminError] = useState(null)
  const [adminNotice, setAdminNotice] = useState(null)
  const [adminQuery, setAdminQuery] = useState('')
  const [adminStatusFilter, setAdminStatusFilter] = useState('')
  const [adminUsers, setAdminUsers] = useState([])

  const resetAdminState = useCallback(() => {
    setAdminUsers([])
    setAdminError(null)
    setAdminNotice(null)
    setAdminQuery('')
    setAdminStatusFilter('')
  }, [])

  const fetchAdminUsers = useCallback(async () => {
    if (!authUser?.owner) {
      return
    }
    setAdminLoading(true)
    setAdminError(null)
    try {
      const params = new URLSearchParams()
      if (adminQuery.trim()) {
        params.set('q', adminQuery.trim())
      }
      if (adminStatusFilter.trim()) {
        params.set('status', adminStatusFilter.trim())
      }
      const query = params.toString()
      const data = await requestJson(
        `/api/admin/users${query ? `?${query}` : ''}`,
        {},
        '관리자 사용자 조회 실패'
      )
      setAdminUsers(Array.isArray(data) ? data : [])
    } catch (err) {
      setAdminUsers([])
      setAdminError(err?.message ?? '관리자 사용자 조회 실패')
    } finally {
      setAdminLoading(false)
    }
  }, [adminQuery, adminStatusFilter, authUser?.owner])

  const updateApprovalStatus = useCallback(async (userId, status) => {
    setAdminError(null)
    setAdminNotice(null)
    try {
      await requestJson(
        `/api/admin/users/${userId}/approval`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ status }),
        },
        '승인 상태 변경 실패'
      )
      setAdminNotice(`사용자 승인 상태를 ${status}로 변경했습니다.`)
      fetchAdminUsers()
    } catch (err) {
      setAdminError(err?.message ?? '승인 상태 변경 실패')
    }
  }, [fetchAdminUsers])

  return {
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
  }
}
