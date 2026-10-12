import axios, { AxiosError, AxiosRequestConfig } from "axios";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

const ACCESS_KEY = "ieia.accessToken";
const REFRESH_KEY = "ieia.refreshToken";

export const tokenStore = {
  get access() {
    try { return localStorage.getItem(ACCESS_KEY); } catch { return null; }
  },
  get refresh() {
    try { return localStorage.getItem(REFRESH_KEY); } catch { return null; }
  },
  set(access: string, refresh: string) {
    try {
      localStorage.setItem(ACCESS_KEY, access);
      localStorage.setItem(REFRESH_KEY, refresh);
    } catch { /* private mode — session only */ }
  },
  clear() {
    try {
      localStorage.removeItem(ACCESS_KEY);
      localStorage.removeItem(REFRESH_KEY);
    } catch { /* ignore */ }
  },
};

export const api = axios.create({ baseURL: BASE_URL, timeout: 30_000 });

api.interceptors.request.use((config) => {
  const token = tokenStore.access;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

/** Shape of the backend envelope: { success, data, error }. */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  error: null | {
    code: string;
    message: string;
    path: string;
    timestamp: string;
    fieldErrors: { field: string; message: string }[];
  };
}

export class ApiError extends Error {
  code: string;
  status: number;
  fieldErrors: { field: string; message: string }[];
  constructor(message: string, code: string, status: number, fieldErrors: { field: string; message: string }[] = []) {
    super(message);
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refresh = tokenStore.refresh;
  if (!refresh) return null;
  try {
    const res = await axios.post<ApiEnvelope<{ accessToken: string; refreshToken: string }>>(
      `${BASE_URL}/auth/refresh`,
      { refreshToken: refresh },
    );
    const { accessToken, refreshToken } = res.data.data;
    tokenStore.set(accessToken, refreshToken);
    return accessToken;
  } catch {
    tokenStore.clear();
    return null;
  }
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const original = error.config as AxiosRequestConfig & { _retried?: boolean };
    const status = error.response?.status ?? 0;
    const body = error.response?.data;
    const isAuthCall = original?.url?.includes("/auth/");

    if (status === 401 && !original._retried && !isAuthCall && tokenStore.refresh) {
      original._retried = true;
      refreshing = refreshing ?? refreshAccessToken();
      const newToken = await refreshing;
      refreshing = null;
      if (newToken) {
        original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` };
        return api.request(original);
      }
      window.dispatchEvent(new CustomEvent("ieia:session-expired"));
    }

    const err = body?.error;
    throw new ApiError(
      err?.message ?? error.message ?? "Something went wrong",
      err?.code ?? "NETWORK_ERROR",
      status,
      err?.fieldErrors ?? [],
    );
  },
);

/** Unwrap the envelope; throws ApiError on failure. */
export async function unwrap<T>(p: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  const res = await p;
  if (!res.data.success) {
    const e = res.data.error;
    throw new ApiError(e?.message ?? "Request failed", e?.code ?? "ERROR", 0, e?.fieldErrors ?? []);
  }
  return res.data.data;
}
