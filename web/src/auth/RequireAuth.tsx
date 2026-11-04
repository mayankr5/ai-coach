import { Navigate, Outlet, useLocation } from 'react-router'
import { Spinner } from '../components/ui'
import { useAuth } from './useAuth'

/** Renders the nested routes for a logged-in user; otherwise sends them to log in and back again. */
export function RequireAuth() {
  const { user, restoring } = useAuth()
  const location = useLocation()
  if (restoring) return <Spinner label="Restoring your session" />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}
