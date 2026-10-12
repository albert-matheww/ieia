import { Route, Routes } from "react-router-dom";
import { AppLayout } from "@/components/layout/AppLayout";
import { AuthGuard, GuestGuard } from "@/components/layout/AuthGuard";
import LoginPage from "@/pages/LoginPage";
import RegisterPage from "@/pages/RegisterPage";
import DashboardPage from "@/pages/DashboardPage";
import DataPage from "@/pages/DataPage";
import ImpactPage from "@/pages/ImpactPage";
import PredictionsPage from "@/pages/PredictionsPage";
import AnomaliesPage from "@/pages/AnomaliesPage";
import SimulatorPage from "@/pages/SimulatorPage";
import RecommendationsPage from "@/pages/RecommendationsPage";
import AssistantPage from "@/pages/AssistantPage";
import ReportsPage from "@/pages/ReportsPage";
import SettingsPage from "@/pages/SettingsPage";
import NotFoundPage from "@/pages/NotFoundPage";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestGuard><LoginPage /></GuestGuard>} />
      <Route path="/register" element={<GuestGuard><RegisterPage /></GuestGuard>} />
      <Route element={<AuthGuard><AppLayout /></AuthGuard>}>
        <Route index element={<DashboardPage />} />
        <Route path="/data" element={<DataPage />} />
        <Route path="/impact" element={<ImpactPage />} />
        <Route path="/predictions" element={<PredictionsPage />} />
        <Route path="/anomalies" element={<AnomaliesPage />} />
        <Route path="/simulator" element={<SimulatorPage />} />
        <Route path="/recommendations" element={<RecommendationsPage />} />
        <Route path="/assistant" element={<AssistantPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
