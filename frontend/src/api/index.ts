import { api, unwrap, ApiEnvelope } from "./client";
import type {
  ActivityType, AnomalyItem, AuthResponse, BenchmarkComparison, ChatMessage, ChatResponse,
  Conversation, Dashboard, EnvRecord, Page, PredictionResponse, Recommendation, ReportSummary,
  ScenarioResult, ScoreResponse, UserProfile,
} from "./types";

const get = <T>(url: string, params?: object) =>
  unwrap<T>(api.get<ApiEnvelope<T>>(url, { params }));
const post = <T>(url: string, body?: unknown) =>
  unwrap<T>(api.post<ApiEnvelope<T>>(url, body));
const put = <T>(url: string, body?: unknown) =>
  unwrap<T>(api.put<ApiEnvelope<T>>(url, body));
const patch = <T>(url: string, body?: unknown) =>
  unwrap<T>(api.patch<ApiEnvelope<T>>(url, body));
const del = (url: string) => api.delete(url);

export const authApi = {
  register: (body: Record<string, unknown>) => post<AuthResponse>("/auth/register", body),
  login: (email: string, password: string) => post<AuthResponse>("/auth/login", { email, password }),
  logout: (refreshToken: string) => post<void>("/auth/logout", { refreshToken }),
  me: () => get<UserProfile>("/auth/me"),
};

export const profileApi = {
  get: () => get<UserProfile>("/profile"),
  update: (body: { fullName?: string; country?: string; region?: string }) => put<UserProfile>("/profile", body),
  changePassword: (currentPassword: string, newPassword: string) =>
    put<void>("/profile/password", { currentPassword, newPassword }),
};

export const activityApi = {
  list: (category?: string) => get<ActivityType[]>("/activity-types", category ? { category } : undefined),
};

export const recordsApi = {
  list: (params: { from?: string; to?: string; category?: string; activityTypeId?: string; page?: number; size?: number; sort?: string }) =>
    get<Page<EnvRecord>>("/environmental-records", params),
  create: (body: Record<string, unknown>) => post<EnvRecord>("/environmental-records", body),
  update: (id: string, body: Record<string, unknown>) => put<EnvRecord>(`/environmental-records/${id}`, body),
  remove: (id: string) => del(`/environmental-records/${id}`),
};

export const dashboardApi = {
  get: (period?: string) => get<Dashboard>("/dashboard", period ? { period } : undefined),
};

export const impactApi = {
  impact: (from?: string, to?: string) => get<any>("/impact", { from, to }),
  trends: (from?: string, to?: string, metrics?: string) => get<any>("/impact/trends", { from, to, metrics }),
  contributors: (by: "category" | "activity", period?: string, limit = 10) =>
    get<any>("/impact/contributors", { by, period, limit }),
  explain: (period?: string) => get<any>("/impact/explain", period ? { period } : undefined),
};

export const scoresApi = {
  history: (from?: string, to?: string) => get<ScoreResponse[]>("/scores", { from, to }),
  forPeriod: (period: string) => get<ScoreResponse>(`/scores/${period}`),
  methodology: () => get<any>("/scores/methodology"),
  recompute: () => post<{ recomputedMonths: number }>("/scores/recompute", {}),
};

export const predictionsApi = {
  get: (metric: string, horizon = 1) => get<PredictionResponse>("/predictions", { metric, horizon }),
  create: (metric: string, horizon: number, force = false) =>
    post<PredictionResponse>("/predictions", { metric, horizon, force }),
  history: (metric: string) => get<PredictionResponse[]>("/predictions/history", { metric }),
};

export const anomaliesApi = {
  list: (params: { status?: string; from?: string; to?: string; metric?: string; severity?: string; page?: number; size?: number }) =>
    get<Page<AnomalyItem>>("/anomalies", params),
  get: (id: string) => get<AnomalyItem>(`/anomalies/${id}`),
  scan: (body?: { from?: string; to?: string; sensitivity?: string }) =>
    post<{ detected: number; newlyOpened: number; updated: number; anomalies: AnomalyItem[]; mlSummary: Record<string, unknown> }>("/anomalies/scan", body ?? {}),
  review: (id: string, status: string, note?: string) => patch<AnomalyItem>(`/anomalies/${id}`, { status, note }),
};

export const scenariosApi = {
  simulate: (body: Record<string, unknown>) => post<ScenarioResult>("/scenarios/simulate", body),
  list: () => get<Page<any>>("/scenarios", { size: 50 }),
  get: (id: string) => get<any>(`/scenarios/${id}`),
  save: (body: Record<string, unknown>) => post<any>("/scenarios", body),
  remove: (id: string) => del(`/scenarios/${id}`),
  compare: (scenarioIds: string[]) => post<any>("/scenarios/compare", { scenarioIds }),
};

export const recommendationsApi = {
  get: (period?: string) => get<Recommendation>("/recommendations", period ? { period } : undefined),
  generate: (useLlm: boolean, period?: string) => post<Recommendation>("/recommendations/generate", { useLlm, period }),
  updateStatus: (id: string, status: string) => patch<any>(`/recommendations/${id}`, { status }),
};

export const assistantApi = {
  conversations: () => get<Conversation[]>("/ai/conversations"),
  conversation: (id: string) => get<{ conversation: Conversation; messages: ChatMessage[] }>(`/ai/conversations/${id}`),
  createConversation: (title?: string) => post<Conversation>("/ai/conversations", { title }),
  remove: (id: string) => del(`/ai/conversations/${id}`),
  chat: (message: string, conversationId?: string) => post<ChatResponse>("/ai/chat", { message, conversationId }),
};

export const benchmarksApi = {
  activeSet: () => get<any[]>("/benchmarks"),
  comparison: (period?: string) => get<BenchmarkComparison>("/benchmarks/comparison", period ? { period } : undefined),
};

export const reportsApi = {
  list: () => get<Page<ReportSummary>>("/reports", { size: 50 }),
  create: (body: Record<string, unknown>) => post<ReportSummary>("/reports", body),
  get: (id: string) => get<{ meta: ReportSummary; payload: Record<string, unknown> }>(`/reports/${id}`),
  remove: (id: string) => del(`/reports/${id}`),
  downloadUrl: (id: string) => `${import.meta.env.VITE_API_BASE_URL ?? "/api"}/reports/${id}/download`,
};
