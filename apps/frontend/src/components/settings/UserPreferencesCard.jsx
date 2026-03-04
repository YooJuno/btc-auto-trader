import { memo } from 'react'
import {
  DASHBOARD_ROUTE,
  PROFILE_VALUES,
  SETTINGS_ROUTE,
  UI_DENSITY_COMFORTABLE,
  UI_DENSITY_COMPACT,
  UI_REFRESH_MAX_SEC,
  UI_REFRESH_MIN_SEC,
  UI_SCOPE_DESKTOP,
  UI_SCOPE_MOBILE,
} from '../../constants/tradingUi.js'
import {
  formatDateTime,
  normalizeRouteToken,
  normalizeTableDensity,
  toInputValue,
} from '../../utils/tradingUi.js'

function UserPreferencesCard({
  settingsLoading,
  settingsSaving,
  userSettings,
  userSettingsError,
  userSettingsNotice,
  userRiskProfile,
  userMarketsInput,
  setUserRiskProfile,
  setUserMarketsInput,
  handleSaveMySettings,
  fetchMySettings,
  commonUiPrefs,
  mobileUiPrefs,
  desktopUiPrefs,
  handleRefreshSecChange,
  handleDefaultRouteChange,
  handleTableDensityChange,
  deviceLabel,
  effectiveRouteLabel,
  effectiveDensityLabel,
  pollingIntervalMs,
}) {
  return (
    <article className="control-card card--elevated auth-settings-card">
      <div className="card-head">
        <div>
          <h2>내 인터페이스 설정</h2>
          <p className="sub">로그인 사용자별 기본 리스크 프로필, 관심 마켓, 기기별 화면 옵션을 저장합니다.</p>
        </div>
        <span className="pill">USER</span>
      </div>
      {userSettingsError && <p className="status-error">{userSettingsError}</p>}
      {userSettingsNotice && <p className="status-success">{userSettingsNotice}</p>}
      <div className="form-grid auth-settings-grid">
        <label className="form-field">
          <span>리스크 프로필</span>
          <select
            value={userRiskProfile}
            onChange={(event) => setUserRiskProfile(event.target.value)}
            disabled={settingsLoading || settingsSaving}
          >
            {PROFILE_VALUES.map((profile) => (
              <option key={profile} value={profile}>
                {profile}
              </option>
            ))}
          </select>
        </label>
        <label className="form-field">
          <span>관심 마켓</span>
          <input
            type="text"
            value={userMarketsInput}
            onChange={(event) => setUserMarketsInput(event.target.value)}
            placeholder="예: KRW-BTC, KRW-ETH"
            disabled={settingsLoading || settingsSaving}
          />
        </label>
      </div>
      <div className="ui-pref-block">
        <p className="sub compact">기기별 UI 설정</p>
        <div className="form-grid ui-pref-grid">
          <label className="form-field">
            <span>공통 새로고침 주기 (초)</span>
            <input
              type="number"
              min={UI_REFRESH_MIN_SEC}
              max={UI_REFRESH_MAX_SEC}
              value={toInputValue(commonUiPrefs.refreshSec)}
              onChange={handleRefreshSecChange}
              disabled={settingsLoading || settingsSaving}
            />
          </label>
          <label className="form-field">
            <span>데스크톱 기본 화면</span>
            <select
              value={normalizeRouteToken(desktopUiPrefs.defaultRoute, DASHBOARD_ROUTE)}
              onChange={(event) => handleDefaultRouteChange(UI_SCOPE_DESKTOP, event.target.value)}
              disabled={settingsLoading || settingsSaving}
            >
              <option value={DASHBOARD_ROUTE}>실시간 현황</option>
              <option value={SETTINGS_ROUTE}>매매 세팅</option>
            </select>
          </label>
          <label className="form-field">
            <span>데스크톱 테이블 밀도</span>
            <select
              value={normalizeTableDensity(desktopUiPrefs.tableDensity, UI_DENSITY_COMFORTABLE)}
              onChange={(event) => handleTableDensityChange(UI_SCOPE_DESKTOP, event.target.value)}
              disabled={settingsLoading || settingsSaving}
            >
              <option value={UI_DENSITY_COMFORTABLE}>컴포터블</option>
              <option value={UI_DENSITY_COMPACT}>컴팩트</option>
            </select>
          </label>
          <label className="form-field">
            <span>모바일 기본 화면</span>
            <select
              value={normalizeRouteToken(mobileUiPrefs.defaultRoute, DASHBOARD_ROUTE)}
              onChange={(event) => handleDefaultRouteChange(UI_SCOPE_MOBILE, event.target.value)}
              disabled={settingsLoading || settingsSaving}
            >
              <option value={DASHBOARD_ROUTE}>실시간 현황</option>
              <option value={SETTINGS_ROUTE}>매매 세팅</option>
            </select>
          </label>
          <label className="form-field">
            <span>모바일 테이블 밀도</span>
            <select
              value={normalizeTableDensity(mobileUiPrefs.tableDensity, UI_DENSITY_COMPACT)}
              onChange={(event) => handleTableDensityChange(UI_SCOPE_MOBILE, event.target.value)}
              disabled={settingsLoading || settingsSaving}
            >
              <option value={UI_DENSITY_COMPACT}>컴팩트</option>
              <option value={UI_DENSITY_COMFORTABLE}>컴포터블</option>
            </select>
          </label>
        </div>
        <p className="sub compact">
          현재 접속: {deviceLabel} / 적용 화면: {effectiveRouteLabel} / 적용 밀도: {effectiveDensityLabel} / 자동 갱신: {Math.round(pollingIntervalMs / 1000)}초
        </p>
      </div>
      <p className="sub compact">마켓 코드는 쉼표로 구분해 입력하세요. 형식 예: KRW-BTC</p>
      <div className="button-row">
        <button
          className="primary-button"
          type="button"
          onClick={handleSaveMySettings}
          disabled={settingsLoading || settingsSaving}
        >
          {settingsSaving ? '저장 중...' : '내 설정 저장'}
        </button>
        <button
          className="ghost-button"
          type="button"
          onClick={fetchMySettings}
          disabled={settingsLoading || settingsSaving}
        >
          {settingsLoading ? '불러오는 중...' : '다시 불러오기'}
        </button>
      </div>
      {userSettings?.updatedAt && (
        <p className="sub compact">마지막 저장 {formatDateTime(userSettings.updatedAt)}</p>
      )}
    </article>
  )
}

export default memo(UserPreferencesCard)
