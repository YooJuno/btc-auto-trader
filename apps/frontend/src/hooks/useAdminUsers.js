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

  const deleteAdminUser = useCallback(async (userId, email) => {
    if (!userId) {
      return
    }
    const label = email || `ID ${userId}`
    const confirmed = window.confirm(
      `${label} 사용자를 삭제합니다.\n연결된 사용자 설정/키/온보딩 데이터와 전용 테넌트 DB까지 함께 삭제됩니다.\n계속할까요?`
    )
    if (!confirmed) {
      return
    }

    setAdminError(null)
    setAdminNotice(null)
    try {
      const payload = await requestJson(
        `/api/admin/users/${userId}`,
        { method: 'DELETE' },
        '사용자 삭제 실패'
      )
      const tenantInfo = payload?.tenantDatabaseDropped && payload?.tenantDatabase
        ? ` (테넌트 DB ${payload.tenantDatabase} 삭제 완료)`
        : ''
      setAdminNotice(`사용자 ${label}를 삭제했습니다.${tenantInfo}`)
      fetchAdminUsers()
    } catch (err) {
      setAdminError(err?.message ?? '사용자 삭제 실패')
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
    deleteAdminUser,
    resetAdminState,
  }
}
