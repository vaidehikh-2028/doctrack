import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import RequireAuth from './auth/RequireAuth';
import Layout from './components/Layout';
import { HOME_BY_ROLE } from './config';
import AdminTypesPage from './pages/AdminTypesPage';
import AdminUsersPage from './pages/AdminUsersPage';
import { DecisionHistoryPage, PendingApprovalsPage } from './pages/ApprovalsPage';
import LoginPage from './pages/LoginPage';
import MyRequestsPage from './pages/MyRequestsPage';
import NewRequestPage from './pages/NewRequestPage';
import NotificationsPage from './pages/NotificationsPage';
import ReportsPage from './pages/ReportsPage';
import RequestDetailPage from './pages/RequestDetailPage';
import StaffQueuePage from './pages/StaffQueuePage';

function Home() {
  const { role } = useAuth();
  return <Navigate to={HOME_BY_ROLE[role] ?? '/login'} replace />;
}

const only = (roles, el) => <RequireAuth roles={roles}>{el}</RequireAuth>;

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Home />} />
        <Route path="requests" element={only(['STUDENT'], <MyRequestsPage />)} />
        <Route path="requests/new" element={only(['STUDENT'], <NewRequestPage />)} />
        <Route path="requests/:requestNumber" element={<RequestDetailPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="queue" element={only(['STAFF'], <StaffQueuePage />)} />
        <Route path="reports" element={only(['STAFF', 'ADMIN'], <ReportsPage />)} />
        <Route path="approvals" element={only(['APPROVER'], <PendingApprovalsPage />)} />
        <Route path="approvals/history" element={only(['APPROVER'], <DecisionHistoryPage />)} />
        <Route path="admin/users" element={only(['ADMIN'], <AdminUsersPage />)} />
        <Route path="admin/document-types" element={only(['ADMIN'], <AdminTypesPage />)} />
        <Route path="*" element={<Home />} />
      </Route>
    </Routes>
  );
}
