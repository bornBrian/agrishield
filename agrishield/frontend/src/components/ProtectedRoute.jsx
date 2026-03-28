// src/components/ProtectedRoute.jsx
  // ═══════════════════════════════════════════════════════════════════
  // Wraps all pages that need authentication.
  // Used in App.jsx: <Route element={<ProtectedRoute />}>...</Route>
  // ═══════════════════════════════════════════════════════════════════
  
  import { Navigate, Outlet } from "react-router-dom";
  import { useAuth } from "../context/useAuth";
  
  export default function ProtectedRoute({ allowedRoles }) {
    const { currentUser } = useAuth();
  
    // Not logged in at all — go to login
    if (!currentUser) {
      return <Navigate to="/login" replace />;
    }
  
    // Role check: if allowedRoles is specified, user must have one of those roles
    // e.g. <Route element={<ProtectedRoute allowedRoles={["regulator","admin"]} />}
    if (allowedRoles && !allowedRoles.includes(currentUser.role)) {
      return <Navigate to="/dashboard" replace />;
    }
  
    // User is authenticated and authorised — render the page
    // Outlet renders whatever child route matched (e.g. DashboardPage)
    return (
      <div className="main-content">
        <Outlet />
      </div>
    );
  }
