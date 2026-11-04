import { Navigate, Route, Routes } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { AppShell } from './components/AppShell'
import { GoalPage } from './pages/GoalPage'
import { GoalsPage } from './pages/GoalsPage'
import { LoginPage } from './pages/LoginPage'
import { NewGoalPage } from './pages/NewGoalPage'
import { RegisterPage } from './pages/RegisterPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<GoalsPage />} />
          <Route path="/goals/new" element={<NewGoalPage />} />
          <Route path="/goals/:goalId" element={<GoalPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
