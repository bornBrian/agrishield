  // src/pages/DashboardPage.jsx
  // ═══════════════════════════════════════════════════════════════════
  // The main dashboard. Demonstrates:
  //  - useEffect: fetch data when the page loads
  //  - Conditional rendering: different content for different roles
  //  - Component composition: assembling StatCard + AnomalyTable
  //  - Loading and error states
  // ═══════════════════════════════════════════════════════════════════
  
  import { useState, useEffect } from "react";
  import { useAuth } from "../context/useAuth";
  import { dashboardApi } from "../agrishield";
  import StatCard from "../components/StatCard";
  import AnomalyTable from "../components/AnomalyTable";
  
  export default function DashboardPage() {
  
    const { currentUser, isRegulator, isAdmin, isManufacturer } = useAuth();
    const userRole = currentUser?.role;
  
    // Data state
    const [stats,      setStats]      = useState(null);
    const [anomalies,  setAnomalies]  = useState([]);
  
    // UI state
    const [loading,    setLoading]    = useState(true);
    const [error,      setError]      = useState(null);
  
    // ── FETCH DATA ON MOUNT ───────────────────────────────────────────
    // useEffect runs after the component renders for the first time.
    // The empty array [] means "run this ONLY on the first render."
    // If you put [someVariable] it would re-run when someVariable changes.
    useEffect(() => {
  
      const loadDashboard = async () => {
        try {
          const res = await dashboardApi.getStats();
          setStats(res.data.stats);
  
          // Anomaly feed: only relevant for regulators and admins
          if (userRole === "regulator" || userRole === "admin") {
            const anomalyRes = await dashboardApi.getAnomalies();
            setAnomalies(anomalyRes.data);
          }
        } catch {
          setError("Failed to load dashboard. Please refresh.");
        } finally {
          setLoading(false);
        }
      };
  
      loadDashboard();
  
    }, [userRole]);
  
    // ── RENDER LOGIC ──────────────────────────────────────────────────
    if (loading) return <div className="page-loading">Loading dashboard...</div>;
    if (error)   return <div className="page-error">{error}</div>;
  
    return (
      <div className="dashboard-page">
  
        {/* Page header with personalised greeting */}
        <div className="page-header">
          <h2>Dashboard</h2>
          <p>Welcome back, <strong>{currentUser.name}</strong>
             &nbsp;·&nbsp; {currentUser.orgName}</p>
        </div>
  
        {/* ── STATS GRID ─────────────────────────────────────────── */}
        {/* StatCard is a reusable component — we pass data as props */}
        <div className="stats-grid">
          <StatCard
            value={stats.verificationsToday}
            label="Verifications Today"
            color="green"
            icon="✓"
          />
          <StatCard
            value={stats.openAnomalies}
            label="Active Anomalies"
            color={stats.openAnomalies > 0 ? "red" : "green"}
            icon="⚠"
          />
          {/* Conditional: only manufacturers see their batch count */}
          {isManufacturer() && (
            <StatCard
              value={stats.myBatches}
              label="My Batches"
              color="blue"
              icon="📦"
            />
          )}
          {/* Conditional: only regulators see pending approval count */}
          {(isRegulator() || isAdmin()) && (
            <StatCard
              value={stats.pendingApprovals}
              label="Awaiting My Approval"
              color={stats.pendingApprovals > 0 ? "amber" : "green"}
              icon="⚖"
            />
          )}
        </div>
  
        {/* ── ANOMALY TABLE — regulators and admins only ──────────── */}
        {(isRegulator() || isAdmin()) && anomalies.length > 0 && (
          <div className="section">
            <h3 className="section-title danger">
              🚨 Active Anomalies — Require Investigation
            </h3>
            <AnomalyTable
              anomalies={anomalies}
              // When an anomaly is resolved, refresh the list
              onResolved={() => dashboardApi.getAnomalies().then(r => setAnomalies(r.data))}
            />
          </div>
        )}
  
      </div>
    );
  }
