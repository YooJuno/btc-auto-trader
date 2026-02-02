import { useEffect, useState } from 'react'
import { apiFetch } from '../lib/api'
import type { BotDefaults } from '../lib/types'

export default function useDefaults() {
  const [defaults, setDefaults] = useState<BotDefaults | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    apiFetch<BotDefaults>('/api/bot-configs/defaults')
      .then((data) => {
        if (!cancelled) {
          setDefaults(data)
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError((err && err.message) || 'Failed')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { defaults, loading, error }
}
