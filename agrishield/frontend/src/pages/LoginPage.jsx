  // src/pages/LoginPage.jsx
  // ═══════════════════════════════════════════════════════════════════
  // The login page. Demonstrates:
  //  - Controlled form inputs (React manages input values)
  //  - Loading states (disable button while request is pending)
  //  - Error handling (show server error to user)
  //  - Multi-step form (normal login → TOTP step for regulators)
  //  - Navigation after success
  // ═══════════════════════════════════════════════════════════════════
  
  import { useState } from "react";
  import { useNavigate } from "react-router-dom";
  import { useAuth } from "../context/useAuth";
  
  export default function LoginPage() {
  
    const { login, socialLogin, requestPasswordReset, resetPassword } = useAuth();
    const navigate   = useNavigate();  // programmatic navigation
  
    // ── FORM STATE ────────────────────────────────────────────────────
    // Controlled inputs: React owns the value, not the browser.
    // Every keypress updates state → React re-renders → input shows new value.
    const [email,     setEmail]     = useState("");
    const [password,  setPassword]  = useState("");
    const [totpCode,  setTotpCode]  = useState("");
    const [socialToken, setSocialToken] = useState("");
    const [forgotId, setForgotId] = useState("");
    const [resetCode, setResetCode] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [resetPreview, setResetPreview] = useState("");
  
    // ── UI STATE ──────────────────────────────────────────────────────
    const [loading,    setLoading]    = useState(false);
    const [error,      setError]      = useState(null);   // null = no error
    const [needsTotp,  setNeedsTotp]  = useState(false);  // show TOTP step?
    const [showForgot, setShowForgot] = useState(false);
    const [info, setInfo] = useState(null);
  
    // ── FORM SUBMIT HANDLER ───────────────────────────────────────────
    // async function — we await the API call
    const handleSubmit = async (e) => {
      e.preventDefault();   // CRITICAL: prevents the browser from reloading the page
      setError(null);        // clear previous errors
      setLoading(true);      // show spinner, disable button
  
      try {
        await login(email, password, needsTotp ? totpCode : null);
  
        // SUCCESS: redirect based on role
        navigate("/dashboard");
  
      } catch (err) {
        const status = err.response?.status;
        const message = err.response?.data?.message;
  
        if (status === 403 && message === "TOTP_REQUIRED") {
          // Server says: valid password, but TOTP needed (regulator/admin)
          setNeedsTotp(true);
          setError("Enter the 6-digit code from your Google Authenticator app.");
        } else if (status === 403 && message === "FARMER_WEB_NOT_ALLOWED") {
          setError("Farmers use AgriShield mobile app only. Use a staff account for web access.");
        } else if (status === 401) {
          setError("Incorrect email or password.");
        } else if (status === 423) {
          setError("Account locked after too many failed attempts. Try again in 30 minutes.");
        } else {
          setError("Connection failed. Check your internet and try again.");
        }
      } finally {
        setLoading(false);  // always re-enable the button
      }
    };

    const handleSocialLogin = async (provider) => {
      setError(null);
      setInfo(null);
      setLoading(true);
      try {
        const token = socialToken.trim() || `${provider}@agrishield.tz`;
        await socialLogin(provider, token);
        navigate("/dashboard");
      } catch (err) {
        const status = err.response?.status;
        const message = err.response?.data?.message;
        if (status === 403 && message === "FARMER_WEB_NOT_ALLOWED") {
          setError("Farmers use AgriShield mobile app only. Use a staff account for web access.");
        } else {
          setError(`Unable to sign in with ${provider}. Check token and try again.`);
        }
      } finally {
        setLoading(false);
      }
    };

    const handleForgotRequest = async (e) => {
      e.preventDefault();
      setError(null);
      setInfo(null);
      setLoading(true);
      try {
        const response = await requestPasswordReset(forgotId);
        setResetPreview(response.previewCode || "");
        setInfo(response.message || "Reset instructions sent.");
      } catch {
        setError("Unable to start reset. Try again.");
      } finally {
        setLoading(false);
      }
    };

    const handleResetPassword = async (e) => {
      e.preventDefault();
      setError(null);
      setInfo(null);
      setLoading(true);
      try {
        const result = await resetPassword(resetCode, newPassword);
        setInfo(result.message || "Password reset complete.");
        setShowForgot(false);
        setResetCode("");
        setNewPassword("");
      } catch (err) {
        const message = err.response?.data?.message;
        if (message === "RESET_CODE_INVALID") {
          setError("Invalid/expired reset code.");
        } else if (message === "PASSWORD_POLICY_FAILED") {
          setError("New password must be at least 8 characters.");
        } else {
          setError("Password reset failed.");
        }
      } finally {
        setLoading(false);
      }
    };
  
    // ── RENDER ────────────────────────────────────────────────────────
    // JSX: looks like HTML but it is JavaScript.
    // className instead of class (class is a reserved word in JS).
    // onChange and onSubmit are React event handlers.
    return (
      <div className="login-page">
        <div className="login-card">
  
          <div className="login-header">
            <span className="login-logo">⬡</span>
            <h1>AgriShield</h1>
            <p>Agricultural Input Verification System</p>
          </div>
  
          {/* Error message — only renders if error state is not null */}
          {error && (
            <div className="alert alert-error">{error}</div>
          )}
          {info && (
            <div className="alert alert-info">{info}</div>
          )}
  
          <form onSubmit={handleSubmit}>
  
            {/* Email field — only shown in step 1 */}
            {!needsTotp && (
              <>
                <div className="form-group">
                  <label htmlFor="email">Email Address</label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@example.com"
                    required
                    autoFocus
                  />
                </div>
  
                <div className="form-group">
                  <label htmlFor="password">Password</label>
                  <input
                    id="password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your password"
                    required
                  />
                </div>
              </>
            )}
  
            {/* TOTP field — only shown in step 2 (regulator/admin) */}
            {needsTotp && (
              <div className="form-group">
                <label htmlFor="totp">
                  6-Digit Code from Google Authenticator
                </label>
                <input
                  id="totp"
                  type="text"
                  inputMode="numeric"   // shows number keyboard on mobile
                  maxLength={6}
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value)}
                  placeholder="000000"
                  autoFocus
                  required
                />
                <small>Code changes every 30 seconds. Enter it quickly.</small>
              </div>
            )}
  
            <button
              type="submit"
              className="btn-primary"
              disabled={loading}   // disabled while loading prevents double-submit
            >
              {loading ? "Signing in..." : needsTotp ? "Verify Code" : "Sign In"}
            </button>

            {!needsTotp && (
              <>
                <div className="divider">OR</div>
                <div className="form-group">
                  <label htmlFor="socialToken">Social token (demo)</label>
                  <input
                    id="socialToken"
                    type="text"
                    value={socialToken}
                    onChange={(e) => setSocialToken(e.target.value)}
                    placeholder="optional: email or provider token"
                  />
                </div>
                <button type="button" className="btn-secondary" style={{ width: "100%" }} disabled={loading} onClick={() => handleSocialLogin("google")}>
                  Continue with Google
                </button>
              </>
            )}
  
          </form>

          <button type="button" className="link-button" onClick={() => setShowForgot((value) => !value)}>
            {showForgot ? "Back to sign in" : "Forgot password?"}
          </button>

          {showForgot && (
            <div className="forgot-card">
              <form onSubmit={handleForgotRequest}>
                <div className="form-group">
                  <label htmlFor="forgotId">Email</label>
                  <input
                    id="forgotId"
                    type="email"
                    value={forgotId}
                    onChange={(e) => setForgotId(e.target.value)}
                    placeholder="you@example.com"
                    required
                  />
                </div>
                <button type="submit" className="btn-secondary" disabled={loading}>Send reset code</button>
              </form>

              <form onSubmit={handleResetPassword}>
                <div className="form-group">
                  <label htmlFor="resetCode">Reset code</label>
                  <input
                    id="resetCode"
                    type="text"
                    value={resetCode}
                    onChange={(e) => setResetCode(e.target.value)}
                    placeholder="6-digit code"
                    required
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="newPassword">New password</label>
                  <input
                    id="newPassword"
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="At least 8 characters"
                    required
                  />
                </div>
                <button type="submit" className="btn-primary" disabled={loading}>Reset password</button>
              </form>

              {resetPreview && (
                <small>Demo reset code: <strong>{resetPreview}</strong> (replace with SMS integration in production)</small>
              )}
            </div>
          )}
  
          <p className="verify-link">
            Verify a product without logging in? <a href="/verify">Click here</a>
          </p>
        </div>
      </div>
    );
  }
