import React from 'react'
import { NavLink } from 'react-router-dom'

const Header: React.FC<any> = ({
  marketFilter,
  setMarketFilter,
  searchOptions,
  setFocusedMarket,
  setChartMode,
  streamStatus,
  streamLabel,
  isAuthed,
  setAuthError,
  setRegisterOpen,
  setLoginOpen,
  handleLogout,
}) => {
  return (
    <header className="topbar">
      <div className="brand">
        <div className="brand-logo">BT</div>
        <div>
          <p className="brand-title">BTC Auto Trader</p>
          <p className="brand-subtitle">KRW-first / Strategy lab / Paper trading</p>
        </div>
      </div>
      <nav className="topnav">
        <NavLink to="/" className={({ isActive }) => `nav-pill ${isActive ? 'active' : ''}`} end>
          Dashboard
        </NavLink>
        <NavLink to="/holdings" className={({ isActive }) => `nav-pill ${isActive ? 'active' : ''}`}>
          Holdings
        </NavLink>
        <NavLink to="/automation" className={({ isActive }) => `nav-pill ${isActive ? 'active' : ''}`}>
          Automation
        </NavLink>
      </nav>
      <div className="top-actions">
        <label className="search-box">
          <span>Search market</span>
          <input
            list="market-options"
            value={marketFilter}
            onChange={(event) => {
              const value = event.target.value.toUpperCase()
              setMarketFilter(value)
              if (searchOptions.includes(value)) {
                setFocusedMarket(value)
                setChartMode('candles')
              }
            }}
            placeholder="KRW-BTC"
          />
          <datalist id="market-options">
            {searchOptions.map((item: string) => (
              <option key={item} value={item} />
            ))}
          </datalist>
        </label>
        <span className={`stream-chip ${streamStatus}`}>{streamLabel}</span>
        <span
          className={`status-chip ${
            streamStatus === 'live' ? 'online' : streamStatus === 'reconnecting' ? 'reconnecting' : 'offline'
          }`}
        >
          {streamStatus === 'live' ? 'Upbit Online' : streamStatus === 'reconnecting' ? 'Upbit Reconnecting' : 'Upbit Offline'}
        </span>
        {!isAuthed && (
          <button
            className="primary small login-button"
            onClick={() => {
              setAuthError('')
              setRegisterOpen(false)
              setLoginOpen(true)
            }}
          >
            로그인
          </button>
        )}
        {!isAuthed && (
          <button
            className="ghost small login-button"
            onClick={() => {
              setAuthError('')
              setLoginOpen(false)
              setRegisterOpen(true)
            }}
          >
            회원가입
          </button>
        )}
        {isAuthed && (
          <button className="ghost" onClick={handleLogout}>
            로그아웃
          </button>
        )}
      </div>
    </header>
  )
}

export default Header
