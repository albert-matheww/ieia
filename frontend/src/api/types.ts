// Mirrors the backend DTOs (docs/03-api-specification.md).

export interface Organization {
  id: string;
  name: string;
  sizeCategory: string;
  region: string;
  headcount: number | null;
}

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  role: "ADMIN" | "ORG_OWNER" | "MEMBER" | "VIEWER";
  active: boolean;
  lastLoginAt: string | null;
  organization?: Organization | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshToken: string;
  user: UserProfile;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type Category = "ELECTRICITY" | "TRANSPORT" | "FUEL" | "WATER" | "WASTE" | "MATERIALS";

export interface ActivityType {
  id: string;
  code: string;
  category: Category;
  name: string;
  description: string | null;
  defaultUnit: string;
  allowedUnits: string[];
  attributeSchema: Record<string, unknown>;
  active: boolean;
}

export interface Impact {
  impactType: "CO2E" | "ENERGY" | "WATER" | "WASTE";
  value: number;
  unit: string;
  emissionFactorId: string | null;
  factorValue: number | null;
  calculationMethod: string;
}

export interface EnvRecord {
  id: string;
  activityType: { id: string; code: string; name: string; category: Category };
  period: string;
  amount: number;
  unit: string;
  location: string | null;
  sourceDetail: string | null;
  attributes: Record<string, unknown>;
  note: string | null;
  impacts: Impact[];
  warnings: string[];
  createdAt: string;
  updatedAt: string;
}

export interface TimeSeriesPoint { period: string; value: number | null; }

export interface Delta { value: number | null; previous: number | null; delta: number | null; deltaPct: number | null; }

export interface Dashboard {
  period: string;
  summary: {
    environmentalScore: Delta;
    co2eKg: Delta;
    energyMj: Delta;
    waterLitre: Delta;
    wasteKg: Delta;
    recycledFraction: number;
  };
  categoryScores: { carbon: number | null; energy: number | null; water: number | null; waste: number | null; transport: number | null };
  charts: {
    co2eTrend: TimeSeriesPoint[];
    energyTrend: TimeSeriesPoint[];
    waterTrend: TimeSeriesPoint[];
    wasteTrend: TimeSeriesPoint[];
    scoreTrend: TimeSeriesPoint[];
    wasteComposition: { method: string; kg: number }[];
    emissionsByCategory: { category: string; kg: number; pct: number }[];
    emissionsByActivity: { activityCode: string; name: string; kg: number; pct: number }[];
  };
  topContributors: { category: string; activityCode: string; name: string; co2eKg: number; pct: number }[];
  openAnomalies: AnomalyItem[];
  latestPrediction: Record<string, unknown> | null;
  dataQuality: { monthsOfHistory: number; recordCount: number; hasGaps: boolean };
}

export interface ScoreResponse {
  period: string;
  overall: number;
  previous: number | null;
  delta: number | null;
  categories: { carbon: number; energy: number; water: number; waste: number; transport: number };
  drivers: { category: string; metric: string; changePct: number; pointsImpact: number; direction: string }[];
  breakdown: Record<string, any>;
  methodologyVersion: string | null;
  provisional: boolean;
}

export interface PredictionResponse {
  metric: string;
  generatedForMonth: string;
  horizonMonth: string;
  currentValue: number;
  predictedValue: number;
  lowerBound: number | null;
  upperBound: number | null;
  expectedChangeAbs: number | null;
  expectedChangePct: number | null;
  model: { name: string; trainedAt: string | null; trainingRows: number | null };
  evaluation: Record<string, number | null>;
  featureImportance: { feature: string; importance: number }[];
  confidenceNote: string | null;
  limitations: string[];
  cached: boolean;
}

export interface AnomalyItem {
  id: string;
  period: string;
  metric: string;
  method: string;
  direction: "SPIKE" | "DROP";
  severity: "LOW" | "MEDIUM" | "HIGH";
  observedValue: number;
  baselineValue: number;
  deviationAbs: number;
  deviationPct: number;
  score: number;
  estimatedCo2eImpactKg: number | null;
  explanation: string;
  status: "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "DISMISSED";
  detectedAt: string;
  reviewedAt: string | null;
  context?: { series: TimeSeriesPoint[]; baselineWindow: string[] };
}

export interface ScenarioMetricBundle {
  co2eKg: number; energyMj: number; waterLitre: number; wasteKg: number;
  recycledFraction: number; overallScore: number;
  categoryScores: Record<string, number>;
  co2eByCategory: Record<string, number>;
}

export interface ScenarioResult {
  basePeriodFrom: string;
  basePeriodTo: string;
  monthsAveraged: number;
  baseline: ScenarioMetricBundle;
  projected: ScenarioMetricBundle;
  deltas: { co2eKg: number; energyMj: number; waterLitre: number; wasteKg: number; overallScore: number };
  byCategory: { category: string; baselineCo2e: number; projectedCo2e: number; deltaCo2e: number }[];
  assumptions: string[];
  notes: string[];
}

export interface Recommendation {
  period: string;
  generatedAt: string;
  basis: "DATA_DRIVEN" | "GENERIC";
  source: "RULE_ENGINE" | "LLM_ASSISTED";
  topContributor: { category: string; pctOfTotal: number } | null;
  items: {
    id: string;
    rank: number;
    category: string;
    title: string;
    rationale: string;
    actionType: string;
    estimatedReductionCo2eKg: number;
    estimatedReductionPct: number;
    estimatedOther: Record<string, unknown>;
    confidence: "LOW" | "MEDIUM" | "HIGH";
    status: string;
  }[];
}

export interface ChatResponse {
  conversationId: string;
  reply: string;
  usedContext: Record<string, any>;
  disclaimers: string[];
  model: string;
  groundingCheckPassed: boolean;
}
export interface Conversation { id: string; title: string; createdAt: string; updatedAt: string; }
export interface ChatMessage { id: string; role: "USER" | "ASSISTANT" | "SYSTEM"; content: string; createdAt: string; model: string | null; }

export interface BenchmarkComparison {
  period: string;
  scope: string;
  segment: string;
  region: string;
  score: {
    current: number | null; previousMonth: number | null; changePoints: number | null;
    previousYear: number | null; changeYoY: number | null; benchmark: number | null; rating: string | null;
  };
  categories: { category: string; value: number; unit: string; benchmarkValue: number | null; rating: string | null }[];
  periodOverPeriod: Record<string, number | null> | null;
  yearOverYear: Record<string, number | null> | null;
  disclaimer: string;
}

export interface ReportSummary {
  id: string;
  title: string;
  periodFrom: string;
  periodTo: string;
  format: "JSON" | "PDF";
  status: "PENDING" | "GENERATING" | "READY" | "FAILED";
  fileSizeBytes: number | null;
  generatedAt: string | null;
  createdAt: string;
}
