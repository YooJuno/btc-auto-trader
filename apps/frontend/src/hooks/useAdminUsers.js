import { useCallback, useRef, useState } from 'react'
import { requestJson } from '../utils/apiClient.js'

const DEFAULT_ADMIN_PAGE_SIZE = 20

export function useAdminUsers(authUser) {
  const [adminLoading, setAdminLoading] = useState(false)
  const [adminError, setAdminError] = useState(null)
  const [adminNotice, setAdminNotice] = useState(null)
  const [adminQueryValue, setAdminQueryValue] = useState('')
  const [adminStatusFilterValue, setAdminStatusFilterValue] = useState('')
  const [adminUsers, setAdminUsers] = useState([])
  const [adminPage, setAdminPage] = useState(0)
  const [adminTotalPages, setAdminTotalPages] = useState(0)
  const [adminTotalElements, setAdminTotalElements] = useState(0)
  const [adminHasNext, setAdminHasNext] = useState(false)
  const requestStateRef = useRef({
    query: '',
    status: '',
    page: 0,
  })

  const resetAdminState = useCallback(() => {
    setAdminUsers([])
    setAdminError(null)
    setAdminNotice(null)
    setAdminQueryValue('')
    setAdminStatusFilterValue('')
    setAdminPage(0)
    setAdminTotalPages(0)
    setAdminTotalElements(0)
    setAdminHasNext(false)
    requestStateRef.current = {
      query: '',
      status: '',
      page: 0,
    }
  }, [])

  const fetchAdminUsers = useCallback(async (overrides = {}) => {
    if (!authUser?.owner) {
      return
    }
    const nextQuery = typeof overrides.query === 'string'
      ? overrides.query
      : requestStateRef.current.query
    const nextStatus = typeof overrides.status === 'string'
      ? overrides.status
      : requestStateRef.current.status
    const nextPage = Number.isFinite(overrides.page)
      ? Math.max(0, overrides.page)
      : requestStateRef.current.page
    requestStateRef.current = {
      query: nextQuery,
      status: nextStatus,
      page: nextPage,
    }
    setAdminLoading(true)
    setAdminError(null)
    try {
      const params = new URLSearchParams()
      if (nextQuery.trim()) {
        params.set('q', nextQuery.trim())
      }
      if (nextStatus.trim()) {
        params.set('status', nextStatus.trim())
      }
      params.set('page', String(nextPage))
      params.set('size', String(DEFAULT_ADMIN_PAGE_SIZE))
      const query = params.toString()
      const data = await requestJson(
        `/api/admin/users/page${query ? `?${query}` : ''}`,
        {},
        '관리자 사용자 조회 실패'
      )
      const items = Array.isArray(data?.items) ? data.items : []
      const resolvedPage = Number.isFinite(data?.page)
        ? Math.max(0, data.page)
        : nextPage
      setAdminUsers(items)
      setAdminPage(resolvedPage)
      setAdminTotalPages(Number.isFinite(data?.totalPages) ? Math.max(0, data.totalPages) : 0)
      setAdminTotalElements(Number.isFinite(data?.totalElements) ? Math.max(0, data.totalElements) : items.length)
      setAdminHasNext(Boolean(data?.hasNext))
      requestStateRef.current = {
        query: nextQuery,
        status: nextStatus,
        page: resolvedPage,
      }
    } catch (err) {
      setAdminUsers([])
      setAdminPage(0)
      setAdminTotalPages(0)
      setAdminTotalElements(0)
      setAdminHasNext(false)
      setAdminError(err?.message ?? '관리자 사용자 조회 실패')
    } finally {
      setAdminLoading(false)
    }
  }, [authUser?.owner])

  const setAdminQuery = useCallback((value) => {
    const nextValue = typeof value === 'string' ? value : ''
    setAdminQueryValue(nextValue)
    setAdminPage(0)
    requestStateRef.current = {
      ...requestStateRef.current,
      query: nextValue,
      page: 0,
    }
  }, [])

  const setAdminStatusFilter = useCallback((value) => {
    const nextValue = typeof value === 'string' ? value : ''
    setAdminStatusFilterValue(nextValue)
    setAdminPage(0)
    requestStateRef.current = {
      ...requestStateRef.current,
      status: nextValue,
      page: 0,
    }
  }, [])

  const goToAdminPage = useCallback((page) => {
    fetchAdminUsers({ page })
  }, [fetchAdminUsers])

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
      await fetchAdminUsers()
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
      `${label} 사용자를 삭제합니다.\n연결된 사용자 설정/키와 전용 테넌트 DB까지 함께 삭제됩니다.\n계속할까요?`
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
      await fetchAdminUsers()
    } catch (err) {
      setAdminError(err?.message ?? '사용자 삭제 실패')
    }
  }, [fetchAdminUsers])

  return {
    adminLoading,
    adminError,
    adminNotice,
    adminQuery: adminQueryValue,
    adminStatusFilter: adminStatusFilterValue,
    adminUsers,
    adminPage,
    adminTotalPages,
    adminTotalElements,
    adminHasNext,
    adminHasPrevious: adminPage > 0,
    setAdminQuery,
    setAdminStatusFilter,
    fetchAdminUsers,
    goToAdminPage,
    updateApprovalStatus,
    deleteAdminUser,
    resetAdminState,
  }
}
