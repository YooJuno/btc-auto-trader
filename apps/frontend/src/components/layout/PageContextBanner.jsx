import {
  ADMIN_USERS_ROUTE,
  DASHBOARD_ROUTE,
  ONBOARDING_ROUTE,
} from '../../constants/tradingUi.js'

function PageContextBanner({ activeRoute }) {
  const isDashboard = activeRoute === DASHBOARD_ROUTE
  const isOnboarding = activeRoute === ONBOARDING_ROUTE
  const isAdmin = activeRoute === ADMIN_USERS_ROUTE

  return (
    <section className="page-context">
      <div>
        <h2>{isOnboarding ? '첫 설정 온보딩' : isAdmin ? '관리자 승인 센터' : isDashboard ? '실시간 매매 현황' : '매매 세팅 센터'}</h2>
        <p className="sub">
          {isOnboarding
            ? '프로필/거래소 키/전략 초기값을 완료해야 대시보드 접근이 가능합니다.'
            : isAdmin
              ? '사용자 승인 상태를 관리하고 거래 접근 권한을 제어합니다.'
              : isDashboard
            ? '현재 자산, 포지션, 주문 로그를 한 화면에서 빠르게 확인합니다.'
            : '사용자 설정, 거래소 키, 마켓별 전략 파라미터를 안전하게 관리합니다.'}
        </p>
      </div>
      <span className="pill">
        {isOnboarding ? 'ONBOARDING' : isAdmin ? 'ADMIN' : isDashboard ? 'LIVE' : 'SETTINGS'}
      </span>
    </section>
  )
}

export default PageContextBanner
