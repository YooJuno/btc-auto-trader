import { useEffect, useState } from 'react'
import { apiFetch } from '../lib/api'
import type { Recommendation, MarketStreamEvent } from '../lib/types'

export default function useMarketStream(autoPickTopN: number, token?: string) {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([])
  const [recoStatus, setRecoStatus] = useState<'empty' | 'live' | 'error'>('empty')
  const [streamStatus, setStreamStatus] = useState<'idle' | 'live' | 'reconnecting' | 'error'>('idle')
  const [lastUpdated, setLastUpdated] = useState('')

  useEffect(() => {
    const streamUrl = `/api/market/stream?topN=${autoPickTopN}`
    let closed = false
    let source: EventSource | null = null
    let retryTimer: number | undefined
    let retryCount = 0

    const handleStream = (event: MessageEvent) => {
      try {
        const payload = JSON.parse(event.data) as MarketStreamEvent
        if (!payload || !payload.recommendations) {
          return
        }
        setRecommendations(payload.recommendations)
        setRecoStatus('live')
        const timestamp = payload.timestamp ? new Date(payload.timestamp) : new Date()
        if (!Number.isNaN(timestamp.valueOf())) {
          setLastUpdated(timestamp.toLocaleTimeString('ko-KR'))
        }
      } catch (error) {
        // ignore parse errors
      }
    }

    const connect = () => {
      if (closed) return
      setStreamStatus('idle')
      source = new EventSource(streamUrl)
      source.addEventListener('recommendations', handleStream as EventListener)
      source.onmessage = handleStream
      source.onopen = () => {
        if (!closed) {
          retryCount = 0
          setStreamStatus('live')
        }
      }
      source.onerror = () => {
        if (closed) return
        setStreamStatus('reconnecting')
        setRecoStatus((prev) => (prev === 'live' ? 'error' : 'empty'))
        source?.close()
        source = null
        if (token) {
          apiFetch<Recommendation[]>(`/api/market/recommendations?topN=${autoPickTopN}`, token)
            .then((data) => {
              setRecommendations(data)
              setRecoStatus('live')
            })
            .catch(() => null)
        }
        const delay = Math.min(30000, 1000 * Math.pow(2, retryCount))
        retryCount = Math.min(retryCount + 1, 5)
        retryTimer = window.setTimeout(connect, delay)
      }
    }

    connect()

    return () => {
      closed = true
      if (retryTimer) window.clearTimeout(retryTimer)
      source?.close()
    }
  }, [autoPickTopN, token])

  return { recommendations, recoStatus, streamStatus, lastUpdated }
}
