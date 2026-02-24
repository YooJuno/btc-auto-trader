import { DASHBOARD_ROUTE } from '../../constants/tradingUi.js'

function PageContextBanner({ activeRoute }) {
  const isDashboard = activeRoute === DASHBOARD_ROUTE

  return (
    <section className="page-context">
      <div>
        <h2>{isDashboard ? '실시간 매매 현황' : '매매 세팅 센터'}</h2>
        <p className="sub">
          {isDashboard
            ? '현재 자산, 포지션, 주문 로그를 한 화면에서 빠르게 확인합니다.'
            : '사용자 설정, 거래소 키, 마켓별 전략 파라미터를 안전하게 관리합니다.'}
        </p>
      </div>
      <span className="pill">{isDashboard ? 'LIVE' : 'SETTINGS'}</span>
    </section>
  )
}

export default PageContextBanner
