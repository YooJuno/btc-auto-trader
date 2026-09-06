function AdminUsersRoute({
  loading,
  error,
  notice,
  query,
  setQuery,
  statusFilter,
  setStatusFilter,
  users,
  page,
  totalPages,
  totalElements,
  hasNext,
  hasPrevious,
  onRefresh,
  onPreviousPage,
  onNextPage,
  onApprove,
  onSuspend,
  onDelete,
}) {
  const pageLabel = totalElements > 0 ? `${page + 1} / ${Math.max(totalPages, 1)}` : '0 / 0'

  return (
    <>
        <article className="table-card">
          <div className="card-head">
            <div>
              <h2>관리자 사용자 승인</h2>
              <p className="sub">승인 상태(PENDING/APPROVED/SUSPENDED)와 키 등록 상태를 관리합니다.</p>
            </div>
          </div>

          <div className="form-grid">
            <label className="form-field">
              <span>검색</span>
              <input
                type="text"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="email/display/provider 검색"
              />
            </label>
            <label className="form-field">
              <span>상태 필터</span>
              <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                <option value="">전체</option>
                <option value="PENDING">PENDING</option>
                <option value="APPROVED">APPROVED</option>
                <option value="SUSPENDED">SUSPENDED</option>
              </select>
            </label>
          </div>

          <div className="button-row">
            <button className="ghost-button" type="button" onClick={onRefresh} disabled={loading}>
              {loading ? '불러오는 중...' : '조회'}
            </button>
          </div>

          {error && <p className="status-error">{error}</p>}
          {notice && <p className="status-success">{notice}</p>}

          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>사용자</th>
                  <th>마지막 로그인</th>
                  <th>승인상태</th>
                  <th>키등록</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {users.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="empty-cell">조회된 사용자가 없습니다.</td>
                  </tr>
                ) : (
                  users.map((item) => (
                    <tr key={item.userId}>
                      <td>
                        <div className="market">
                          <span className="market__coin">{item.email || '-'}</span>
                          <span className="market__pair">{item.displayName || '-'}</span>
                        </div>
                      </td>
                      <td className="mono">{item.lastLoginAt ? new Date(item.lastLoginAt).toLocaleString('ko-KR', { hour12: false }) : '-'}</td>
                      <td className="mono">{item.approvalStatus}</td>
                      <td>{item.credentialConfigured ? 'Y' : 'N'}</td>
                      <td>
                        <div className="button-row">
                          <button className="table-action-button" type="button" onClick={() => onApprove(item.userId)}>
                            승인
                          </button>
                          <button className="ghost-button" type="button" onClick={() => onSuspend(item.userId)}>
                            중지
                          </button>
                          <button className="danger-button" type="button" onClick={() => onDelete(item.userId, item.email)}>
                            삭제
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="admin-pagination">
            <p className="sub">총 {totalElements}명 · 페이지 {pageLabel}</p>
            <div className="button-row">
              <button className="ghost-button" type="button" onClick={onPreviousPage} disabled={loading || !hasPrevious}>
                이전
              </button>
              <button className="ghost-button" type="button" onClick={onNextPage} disabled={loading || !hasNext}>
                다음
              </button>
            </div>
          </div>
        </article>
      </>
  )
}

export default AdminUsersRoute
