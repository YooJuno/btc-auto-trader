import { useMemo } from 'react'
import type useAutomation from './useAutomation'
import type { BotDefaults } from '../lib/types'
import type { AutomationProps } from '../pages/AutomationPage'

type Params = {
  automation: ReturnType<typeof useAutomation>
  loading: boolean
  riskBadge: string
  operationLabelFor: (mode: string) => string
  loadedDefaults?: BotDefaults | null
}

export default function useAutomationProps({ automation, loading, riskBadge, operationLabelFor, loadedDefaults }: Params): AutomationProps {
  return useMemo(() => ({
    ...automation,
    loading,
    riskBadge,
    operationLabelFor,
    loadedDefaults,
  }), [automation, loading, riskBadge, operationLabelFor, loadedDefaults])
}
