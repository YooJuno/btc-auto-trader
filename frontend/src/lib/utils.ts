export function formatMoney(value: number) {
  return new Intl.NumberFormat('ko-KR').format(Math.round(value))
}

export function formatPct(value: number) {
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
}

export function parseNumberInput(value: string): number | '' {
  if (value.trim() === '') {
    return ''
  }
  const parsed = Number(value)
  return Number.isNaN(parsed) ? '' : parsed
}

export function toNumberOrNull(value: number | ''): number | null {
  if (value === '' || Number.isNaN(value as number)) {
    return null
  }
  return value as number
}

export function buildSparkline(points: { timestamp: string; open: number; high: number; low: number; close: number }[]) {
  if (points.length < 2) {
    return ''
  }
  const values = points.map((point) => point.close)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * 100
      const y = 32 - ((value - min) / range) * 32
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
    })
    .join(' ')
}
