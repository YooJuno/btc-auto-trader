import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'

const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const HoldingsPage = lazy(() => import('./pages/HoldingsPage'))
const AutomationPage = lazy(() => import('./pages/AutomationPage'))

import type { DashboardProps } from './pages/DashboardPage'
import type { HoldingsProps } from './pages/HoldingsPage'
import type { AutomationProps } from './pages/AutomationPage'

type AppRoutesProps = {
  dashboardProps: DashboardProps
  holdingsProps: HoldingsProps
  automationProps: AutomationProps
}

export default function AppRoutes({ dashboardProps, holdingsProps, automationProps }: AppRoutesProps) {
  return (
    <Suspense fallback={<div className="loading">Loading...</div>}>
      <Routes>
        <Route path="/" element={<DashboardPage {...dashboardProps} />} />
        <Route path="/holdings" element={<HoldingsPage {...holdingsProps} />} />
        <Route path="/automation" element={<AutomationPage {...automationProps} />} />
      </Routes>
    </Suspense>
  )
}
