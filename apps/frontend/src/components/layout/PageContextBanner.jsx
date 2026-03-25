import {
  ADMIN_USERS_ROUTE,
  DASHBOARD_ROUTE,
  PROFILE_ROUTE,
} from '../../constants/tradingUi.js'

function PageContextBanner({ activeRoute }) {
  const isDashboard = activeRoute === DASHBOARD_ROUTE
  const isAdmin = activeRoute === ADMIN_USERS_ROUTE
  const isProfile = activeRoute === PROFILE_ROUTE

  return (
    <section className="page-context">
      <div>
        <h2>{isAdmin ? '관리자 승인 센터' : isDashboard ? '실시간 매매 현황' : isProfile ? '개인 정보 센터' : '매매 세팅 센터'}</h2>
        <p className="sub">
          {isAdmin
            ? '사용자 승인 상태를 관리하고 거래 접근 권한을 제어합니다.'
            : isDashboard
            ? '현재 자산, 포지션, 주문 로그를 한 화면에서 확인합니다.'
            : isProfile
            ? '닉네임, 계정 정보, 거래소 API 키처럼 개인 단위 정보를 따로 관리합니다.'
            : '마켓별 전략 파라미터와 개인 화면 기본값을 안전하게 관리합니다.'}
        </p>
      </div>
      <span className="pill">
        {isAdmin ? 'ADMIN' : isDashboard ? 'LIVE' : isProfile ? 'PROFILE' : 'SETTINGS'}
      </span>
    </section>
  )
}

export default PageContextBanner
