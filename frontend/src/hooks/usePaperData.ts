import { useEffect, useState, useCallback } from 'react'
import { apiFetch } from '../lib/api'
import type { PaperSummary, PaperPerformance } from '../lib/types'

export default function usePaperData(token?: string) {
  const [paperSummary, setPaperSummary] = useState<PaperSummary | null>(null)
  const [performance, setPerformance] = useState<PaperPerformance | null>(null)
  const [summaryStatus, setSummaryStatus] = useState<'empty' | 'live' | 'error'>('empty')
  const [performanceStatus, setPerformanceStatus] = useState<'empty' | 'live' | 'error'>('empty')

  const fetchAll = useCallback(() => {
    if (!token) return
    apiFetch<PaperSummary>('/api/paper/summary', token)
      .then((data) => {
        setPaperSummary(data)
        setSummaryStatus('live')
      })
      .catch(() => {
        setSummaryStatus((prev) => (prev === 'live' ? 'error' : 'empty'))
      })
    apiFetch<PaperPerformance>('/api/paper/performance?days=7&weeks=4', token)
      .then((data) => {
        setPerformance(data)
        setPerformanceStatus('live')
      })
      .catch(() => {
        setPerformanceStatus((prev) => (prev === 'live' ? 'error' : 'empty'))
      })
  }, [token])

  useEffect(() => {
    if (!token) return
    fetchAll()
    const interval = setInterval(fetchAll, 15000) // poll every 15s

    // also refresh when the window regains focus
    const onFocus = () => fetchAll()
    window.addEventListener('focus', onFocus)

    // SSE: open a server-sent events connection for live updates with reconnection and debug logs
    let es: EventSource | null = null
    let reconnectTimer: number | null = null

    const connect = () => {
      try {
        console.debug('SSE: connecting to /api/paper/stream')
        es = new EventSource(`/api/paper/stream?token=${encodeURIComponent(token)}`)

        // listen for named event "summary" (server emits SseEmitter.event().name("summary"))
        es.addEventListener('summary', (ev: MessageEvent) => {
          try {
            const data = JSON.parse(ev.data)
            console.debug('SSE: summary received', data)
            setPaperSummary(data)
            setSummaryStatus('live')
          } catch (err) {
            console.warn('SSE: failed to parse summary', err)
          }
        })

        // also handle default message events in case server uses default event name
        es.onmessage = (ev) => {
          try {
            const data = JSON.parse(ev.data)
            console.debug('SSE: message received', data)
            setPaperSummary(data)
            setSummaryStatus('live')
          } catch (err) {
            // ignore
          }
        }

        es.onopen = () => {
          console.debug('SSE: connection opened')
        }

        es.onerror = (err) => {
          console.warn('SSE: error or closed', err)
          if (es) {
            try { es.close() } catch (e) { /* ignore */ }
            es = null
          }
          if (reconnectTimer) window.clearTimeout(reconnectTimer)
          reconnectTimer = window.setTimeout(() => {
            console.debug('SSE: attempting reconnect')
            connect()
          }, 5000)
        }
      } catch (err) {
        console.warn('SSE: could not create EventSource', err)
      }
    }

    connect()

    return () => {
      clearInterval(interval)
      window.removeEventListener('focus', onFocus)
      if (reconnectTimer) window.clearTimeout(reconnectTimer)
      if (es) es.close()
    }
  }, [fetchAll, token])

  const resetPaper = useCallback(async () => {
    if (!token) return null
    try {
      const updated = await apiFetch<PaperSummary>('/api/paper/reset', token, {
        method: 'POST',
        body: JSON.stringify({ initialCash: 1000000 }), // restore default initial cash
      })
      setPaperSummary(updated)
      setSummaryStatus('live')
      return updated
    } catch (err) {
      return null
    }
  }, [token])

  return { paperSummary, performance, summaryStatus, performanceStatus, fetchAll, resetPaper }
}
