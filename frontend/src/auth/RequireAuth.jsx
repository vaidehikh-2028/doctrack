import { Navigate, useLocation } from 'react-router-dom';
import { HOME_BY_ROLE } from '../config';
import { Spinner } from '../components/Feedback';
import { useAuth } from './AuthContext';

/** Route guard for the UI only: every endpoint is still protected by SecurityConfig and @PreAuthorize. */
export default function RequireAuth({ roles, children }) {
  const { isAuthenticated, restoring, role } = useAuth();
  const location = useLocation();

  if (restoring) return <Spinner label="Restoring your session…" />;
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location }} />;
  if (roles && !roles.includes(role)) return <Navigate to={HOME_BY_ROLE[role] ?? '/'} replace />;
  return children;
}
