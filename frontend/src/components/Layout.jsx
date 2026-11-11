import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { titleCase } from '../lib/format';

const NAV = {
  STUDENT: [
    { to: '/requests', label: 'My requests' },
    { to: '/requests/new', label: 'New request' },
    { to: '/notifications', label: 'Notifications' },
  ],
  STAFF: [
    { to: '/queue', label: 'Work queue' },
    { to: '/reports', label: 'Reports' },
    { to: '/notifications', label: 'Notifications' },
  ],
  APPROVER: [
    { to: '/approvals', label: 'Pending approvals' },
    { to: '/approvals/history', label: 'My decisions' },
    { to: '/notifications', label: 'Notifications' },
  ],
  ADMIN: [
    { to: '/admin/users', label: 'Users' },
    { to: '/admin/document-types', label: 'Document types' },
    { to: '/reports', label: 'Reports' },
  ],
};

export default function Layout() {
  const { user, logout } = useAuth();
  const links = NAV[user?.role] ?? [];

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand__logo" aria-hidden="true">
            D
          </span>
          <span>
            <span className="brand__name">DocTrack</span>
            <span className="brand__sub">Student document requests · LNMIIT</span>
          </span>
        </div>
        <div className="topbar__user">
          <span className="topbar__name">{user?.name}</span>
          <span className="role-chip">{titleCase(user?.role)}</span>
          <button type="button" className="btn btn--ghost btn--small" onClick={() => logout()}>
            Log out
          </button>
        </div>
      </header>
      <nav className="tabs" aria-label="Sections">
        {links.map((l) => (
          <NavLink key={l.to} to={l.to} end>
            {l.label}
          </NavLink>
        ))}
      </nav>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
