import { useState, useEffect } from "react";
import { authApi } from "../agrishield";
import { AuthContext } from "./AuthContextObject";

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    authApi
      .me()
      .then((res) => setCurrentUser(res.data))
      .catch(() => setCurrentUser(null))
      .finally(() => setLoading(false));
  }, []);

  const login = async (email, password, totpCode) => {
    const res = await authApi.login(email, password, totpCode);
    setCurrentUser(res.data);
    return res.data;
  };

  const socialLogin = async (provider, idToken) => {
    const res = await authApi.socialLogin(provider, idToken);
    setCurrentUser(res.data);
    return res.data;
  };

  const requestPasswordReset = async (identifier) => {
    const res = await authApi.requestPasswordReset(identifier);
    return res.data;
  };

  const resetPassword = async (code, newPassword) => {
    const res = await authApi.resetPassword(code, newPassword);
    return res.data;
  };

  const logout = async () => {
    await authApi.logout();
    setCurrentUser(null);
  };

  const isManufacturer = () => currentUser?.role === "manufacturer";
  const isRegulator = () => currentUser?.role === "regulator";
  const isDistributor = () => currentUser?.role === "distributor";
  const isAdmin = () => currentUser?.role === "admin";

  const value = {
    currentUser,
    loading,
    login,
    socialLogin,
    requestPasswordReset,
    resetPassword,
    logout,
    isManufacturer,
    isRegulator,
    isDistributor,
    isAdmin,
  };

  if (loading) {
    return <div className="loading-screen">Loading AgriShield...</div>;
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
