import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { authApi } from "@/api";
import { tokenStore } from "@/api/client";
import type { UserProfile } from "@/api/types";

interface AuthState {
  user: UserProfile | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (body: Record<string, unknown>) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!tokenStore.access && !tokenStore.refresh) {
        setLoading(false);
        return;
      }
      try {
        const me = await authApi.me();
        if (!cancelled) setUser(me);
      } catch {
        tokenStore.clear();
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    bootstrap();

    const onExpired = () => {
      tokenStore.clear();
      setUser(null);
    };
    window.addEventListener("ieia:session-expired", onExpired);
    return () => {
      cancelled = true;
      window.removeEventListener("ieia:session-expired", onExpired);
    };
  }, []);

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    async login(email, password) {
      const res = await authApi.login(email, password);
      tokenStore.set(res.accessToken, res.refreshToken);
      setUser(res.user);
    },
    async register(body) {
      const res = await authApi.register(body);
      tokenStore.set(res.accessToken, res.refreshToken);
      setUser(res.user);
    },
    async logout() {
      try {
        if (tokenStore.refresh) await authApi.logout(tokenStore.refresh);
      } catch { /* best effort */ }
      tokenStore.clear();
      setUser(null);
    },
    async refreshUser() {
      setUser(await authApi.me());
    },
  }), [user, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
