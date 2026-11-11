import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { configureClient } from '../api/client';
import * as backend from '../api/doctrack';
import { msUntilExpiry } from '../lib/jwt';

const TOKEN_KEY = 'doctrack.token';
const AuthContext = createContext(null);

const storage = {
  get: () => {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (t) => {
    try {
      sessionStorage.setItem(TOKEN_KEY, t);
    } catch {
      /* storage blocked: token stays in memory only */
    }
  },
  clear: () => {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      /* ignore */
    }
  },
};

/**
 * Holds the JWT and the signed-in user.
 * - Token lives in memory and sessionStorage (cleared when the tab closes).
 * - On reload, the stored token is validated with GET /api/auth/me before the app renders.
 * - The session ends exactly at the token's `exp`, or on any 401 from the API.
 */
export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => {
    const t = storage.get();
    return t && msUntilExpiry(t) > 0 ? t : null;
  });
  const [user, setUser] = useState(null);
  const [restoring, setRestoring] = useState(Boolean(token));
  const [notice, setNotice] = useState(null);
  const tokenRef = useRef(token);
  tokenRef.current = token;

  const logout = useCallback((reason) => {
    storage.clear();
    tokenRef.current = null;
    setToken(null);
    setUser(null);
    setNotice(typeof reason === 'string' ? reason : null);
  }, []);

  // Configure synchronously so the very first request (the /me restore below) carries the token.
  configureClient({ getToken: () => tokenRef.current, unauthorized: (msg) => logout(msg) });

  useEffect(() => {
    if (!restoring) return undefined;
    const ctrl = new AbortController();
    backend
      .me(ctrl.signal)
      .then((u) => setUser(u))
      .catch((err) => {
        if (err.name !== 'AbortError') logout();
      })
      .finally(() => {
        if (!ctrl.signal.aborted) setRestoring(false);
      });
    return () => ctrl.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!token) return undefined;
    const ms = msUntilExpiry(token);
    if (!Number.isFinite(ms)) return undefined;
    // setTimeout overflows above ~24.8 days; tokens here last 2 hours.
    const id = setTimeout(() => logout('Your session has expired. Please log in again.'), Math.max(0, Math.min(ms, 2 ** 31 - 1)));
    return () => clearTimeout(id);
  }, [token, logout]);

  const login = useCallback(async (username, password) => {
    const res = await backend.login(username, password);
    storage.set(res.token);
    tokenRef.current = res.token;
    setNotice(null);
    setUser(res.user);
    setToken(res.token);
    return res.user;
  }, []);

  const value = useMemo(
    () => ({
      token,
      user,
      role: user?.role ?? null,
      isAuthenticated: Boolean(token && user),
      restoring,
      login,
      logout,
      notice,
    }),
    [token, user, restoring, login, logout, notice],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
