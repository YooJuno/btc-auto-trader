export const PROFILE_VALUES = ['BALANCED', 'AGGRESSIVE', 'CONSERVATIVE']
export const DEFAULT_MARKET_MAX_ORDER_KRW = '30000'
export const DEFAULT_MARKET_PROFILE = 'BALANCED'
export const MARKET_CODE_PATTERN = /^[A-Z]{2,10}-[A-Z0-9]{2,15}$/

export const RATIO_FIELDS = [
  'takeProfitPct',
  'stopLossPct',
  'trailingStopPct',
  'partialTakeProfitPct',
  'stopExitPct',
  'trendExitPct',
  'momentumExitPct',
]

export const RATIO_FIELD_LABELS = {
  takeProfitPct: '익절 %',
  stopLossPct: '손절 %',
  trailingStopPct: '트레일링 %',
  partialTakeProfitPct: '부분 익절 %',
  stopExitPct: '손절/트레일링 매도 %',
  trendExitPct: '추세 이탈 매도 %',
  momentumExitPct: '모멘텀 역전 매도 %',
}

export const DASHBOARD_ROUTE = 'dashboard'
export const SETTINGS_ROUTE = 'settings'
export const SETTINGS_PATH = '/settings'

export const UI_SCOPE_COMMON = 'common'
export const UI_SCOPE_MOBILE = 'mobile'
export const UI_SCOPE_DESKTOP = 'desktop'

export const UI_DENSITY_COMFORTABLE = 'comfortable'
export const UI_DENSITY_COMPACT = 'compact'

export const UI_REFRESH_MIN_SEC = 2
export const UI_REFRESH_MAX_SEC = 30
