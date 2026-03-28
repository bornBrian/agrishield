import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
  import { AuthProvider } from "./context/AuthContext";
  import ProtectedRoute from "./components/ProtectedRoute";
  
  // Pages — each is a full-screen component
  import LoginPage         from "./pages/LoginPage";
  import DashboardPage     from "./pages/DashboardPage";
  import BatchCreatePage   from "./pages/BatchCreatePage";
  
  export default function App() {
    return (
      // BrowserRouter enables React Router throughout the app
      <BrowserRouter>
        {/* AuthProvider wraps everything — all pages can access auth */}
        <AuthProvider>
          <Routes>
  
            {/* PUBLIC ROUTES — no login required */}
            <Route path="/login"  element={<LoginPage />} />
            {/* PROTECTED ROUTES — redirect to /login if not authenticated */}
            {/* ProtectedRoute checks the session and redirects if needed */}
            <Route element={<ProtectedRoute />}>
              <Route path="/dashboard"   element={<DashboardPage />} />
              <Route path="/batch/new"   element={<BatchCreatePage />} />
            </Route>
  
            {/* Redirect root URL to dashboard (or login if not authenticated) */}
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
  
            {/* 404 — anything else goes to dashboard */}
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
  
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    );
  }
