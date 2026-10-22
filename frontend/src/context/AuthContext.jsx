import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/authApi';
import { setUnauthorizedHandler } from '../api/client';
import { stripRole } from '../utils/format';

const AuthContext = createContext(null);
const KEY = 'siddu.user';

function load() {
  try { return JSON.parse(sessionStorage.getItem(KEY)); } catch { return null; }
}
function save(user) {
  try { user ? sessionStorage.setItem(KEY, JSON.stringify(user)) : sessionStorage.removeItem(KEY); } catch { /* storage unavailable */ }
}

/* The auth service has no "who am I" endpoint, so email and roles from the login
   response are kept for the tab's lifetime. The tokens themselves stay in HttpOnly cookies. */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(load);

  const clear = useCallback(() => { save(null); setUser(null); }, []);
  useEffect(() => { setUnauthorizedHandler(clear); }, [clear]);

  const accept = (res) => {
    const u = { email: res.email, roles: (res.roles || []).map(stripRole) };
    save(u); setUser(u);
    return u;
  };

  const value = useMemo(() => ({
    user,
    isStaff: !!user && user.roles.some((r) => r === 'ADMIN' || r === 'MANAGER'),
    isAdmin: !!user && user.roles.includes('ADMIN'),
    signIn: async (email, password) => accept(await authApi.login(email, password)),
    signUp: async (email, password) => authApi.register(email, password),
    signOut: async () => { try { await authApi.logout(); } finally { clear(); } },
  }), [user, clear]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
