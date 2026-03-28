  
  import axios from "axios";

  const AUTH_STORAGE_KEY = "agrishield_auth_user";
  const RESET_STORAGE_KEY = "agrishield_reset_tokens";
  const VERIFY_STORAGE_KEY = "agrishield_verify_tokens";
  const AUTH_MODE = (import.meta.env.VITE_AUTH_MODE || "api").toLowerCase();

  const DEMO_USERS = [
    {
      id: "u-admin-1",
      fullName: "Amina Admin",
      email: "admin@agrishield.tz",
      role: "admin",
      password: "Admin#123",
      totpRequired: true,
      verified: true,
    },
    {
      id: "u-reg-1",
      fullName: "Rashid Regulator",
      email: "regulator@agrishield.tz",
      role: "regulator",
      password: "Regulator#123",
      totpRequired: true,
      verified: true,
    },
    {
      id: "u-mfr-1",
      fullName: "Mariam Manufacturer",
      email: "manufacturer@agrishield.tz",
      role: "manufacturer",
      password: "Manufacturer#123",
      totpRequired: false,
      verified: true,
    },
    {
      id: "u-dist-1",
      fullName: "Daniel Distributor",
      email: "distributor@agrishield.tz",
      role: "distributor",
      password: "Distributor#123",
      totpRequired: false,
      verified: true,
    },
    {
      id: "u-dealer-1",
      fullName: "Dora Dealer",
      email: "dealer@agrishield.tz",
      role: "dealer",
      password: "Dealer#123",
      totpRequired: false,
      verified: true,
    },
    {
      id: "u-farmer-1",
      fullName: "Farmer Mobile User",
      email: "farmer@agrishield.tz",
      role: "farmer",
      password: "Farmer#123",
      totpRequired: false,
      verified: true,
    },
  ];

  const mockState = {
    users: new Map(DEMO_USERS.map((user) => [user.email.toLowerCase(), { ...user }])),
  };

  function getSessionUser() {
    try {
      const raw = localStorage.getItem(AUTH_STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  function saveSessionUser(user) {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user));
  }

  function clearSessionUser() {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  }

  function ensureWebPortalRole(user) {
    if (user.role === "farmer") {
      const error = new Error("Farmers use the mobile app and cannot sign in to the web portal.");
      error.response = { status: 403, data: { message: "FARMER_WEB_NOT_ALLOWED" } };
      throw error;
    }
  }

  function randomCode() {
    return String(Math.floor(100000 + Math.random() * 900000));
  }

  function readResetTokens() {
    try {
      const raw = localStorage.getItem(RESET_STORAGE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch {
      return {};
    }
  }

  function writeResetTokens(tokens) {
    localStorage.setItem(RESET_STORAGE_KEY, JSON.stringify(tokens));
  }

  function readVerifyTokens() {
    try {
      const raw = localStorage.getItem(VERIFY_STORAGE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch {
      return {};
    }
  }

  function writeVerifyTokens(tokens) {
    localStorage.setItem(VERIFY_STORAGE_KEY, JSON.stringify(tokens));
  }

  function mockResponse(data) {
    return Promise.resolve({ data });
  }

  function loginWithPassword(email, password, totpCode) {
    const user = mockState.users.get(String(email || "").toLowerCase());
    if (!user || user.password !== password) {
      const error = new Error("Invalid credentials");
      error.response = { status: 401, data: { message: "INVALID_CREDENTIALS" } };
      return Promise.reject(error);
    }

    if (!user.verified) {
      const error = new Error("Account not verified");
      error.response = { status: 403, data: { message: "ACCOUNT_NOT_VERIFIED" } };
      return Promise.reject(error);
    }

    ensureWebPortalRole(user);

    if (user.totpRequired && String(totpCode || "") !== "123456") {
      const error = new Error("TOTP required");
      error.response = { status: 403, data: { message: "TOTP_REQUIRED" } };
      return Promise.reject(error);
    }

    const sessionUser = {
      id: user.id,
      fullName: user.fullName,
      email: user.email,
      role: user.role,
      authProvider: "password",
    };
    saveSessionUser(sessionUser);
    return mockResponse(sessionUser);
  }

  function loginWithSocial(provider, idToken) {
    const providerName = provider === "apple" ? "apple" : "google";
    const token = String(idToken || "").trim();
    if (!token) {
      const error = new Error("Missing social token");
      error.response = { status: 400, data: { message: "SOCIAL_TOKEN_REQUIRED" } };
      return Promise.reject(error);
    }

    const tokenEmail = token.includes("@") ? token.toLowerCase() : null;
    let user = tokenEmail ? mockState.users.get(tokenEmail) : null;

    if (!user) {
      user = providerName === "google"
        ? mockState.users.get("manufacturer@agrishield.tz")
        : mockState.users.get("dealer@agrishield.tz");
    }

    ensureWebPortalRole(user);

    const sessionUser = {
      id: user.id,
      fullName: user.fullName,
      email: user.email,
      role: user.role,
      authProvider: providerName,
    };
    saveSessionUser(sessionUser);
    return mockResponse(sessionUser);
  }

  function requestForgotPassword(identifier) {
    const key = String(identifier || "").toLowerCase().trim();
    const user = mockState.users.get(key);
    if (!user) {
      return mockResponse({
        accepted: true,
        channel: "sms_or_email",
        message: "If the account exists, reset instructions were sent.",
      });
    }

    const code = randomCode();
    const tokens = readResetTokens();
    tokens[code] = {
      email: user.email,
      expiresAt: Date.now() + 15 * 60 * 1000,
      used: false,
    };
    writeResetTokens(tokens);

    return mockResponse({
      accepted: true,
      channel: "sms_or_email",
      message: "Reset code sent successfully.",
      previewCode: code,
      expiresInMinutes: 15,
    });
  }

  function resetPassword(resetCode, newPassword) {
    const code = String(resetCode || "").trim();
    const nextPassword = String(newPassword || "").trim();
    const tokens = readResetTokens();
    const token = tokens[code];

    if (!token || token.used || token.expiresAt < Date.now()) {
      const error = new Error("Invalid or expired reset code");
      error.response = { status: 400, data: { message: "RESET_CODE_INVALID" } };
      return Promise.reject(error);
    }

    if (nextPassword.length < 8) {
      const error = new Error("Password too short");
      error.response = { status: 400, data: { message: "PASSWORD_POLICY_FAILED" } };
      return Promise.reject(error);
    }

    const user = mockState.users.get(token.email.toLowerCase());
    user.password = nextPassword;
    token.used = true;
    tokens[code] = token;
    writeResetTokens(tokens);

    return mockResponse({
      success: true,
      message: "Password reset successful. You can now sign in.",
    });
  }

  function registerUser(fullName, email, password, role, phone) {
    const normalizedEmail = String(email || "").toLowerCase().trim();
    if (!fullName || !normalizedEmail || !password) {
      const error = new Error("Invalid registration payload");
      error.response = { status: 400, data: { message: "REGISTER_INPUT_INVALID" } };
      return Promise.reject(error);
    }

    if (mockState.users.has(normalizedEmail)) {
      const error = new Error("Account exists");
      error.response = { status: 409, data: { message: "ACCOUNT_EXISTS" } };
      return Promise.reject(error);
    }

    const finalRole = (role || "dealer").toLowerCase();
    const user = {
      id: `u-${Date.now()}`,
      fullName,
      email: normalizedEmail,
      role: finalRole,
      password,
      phone: phone || null,
      totpRequired: false,
      verified: false,
    };
    mockState.users.set(normalizedEmail, user);

    const code = randomCode();
    const verifyTokens = readVerifyTokens();
    verifyTokens[normalizedEmail] = {
      code,
      expiresAt: Date.now() + 15 * 60 * 1000,
      used: false,
    };
    writeVerifyTokens(verifyTokens);

    return mockResponse({
      accepted: true,
      message: "Registration received. Verify your account with the code sent.",
      previewCode: code,
      expiresInMinutes: 15,
    });
  }

  function verifyRegistration(email, code) {
    const normalizedEmail = String(email || "").toLowerCase().trim();
    const normalizedCode = String(code || "").trim();
    const verifyTokens = readVerifyTokens();
    const token = verifyTokens[normalizedEmail];

    if (!token || token.used || token.expiresAt < Date.now() || token.code !== normalizedCode) {
      const error = new Error("Invalid verification code");
      error.response = { status: 400, data: { message: "VERIFY_CODE_INVALID" } };
      return Promise.reject(error);
    }

    const user = mockState.users.get(normalizedEmail);
    if (!user) {
      const error = new Error("Account not found");
      error.response = { status: 400, data: { message: "ACCOUNT_NOT_FOUND" } };
      return Promise.reject(error);
    }

    user.verified = true;
    mockState.users.set(normalizedEmail, user);
    verifyTokens[normalizedEmail] = { ...token, used: true };
    writeVerifyTokens(verifyTokens);

    return mockResponse({
      success: true,
      message: "Account verified successfully. You can now sign in.",
    });
  }
  
  // ── CREATE THE AXIOS INSTANCE ────────────────────────────────────────
  // baseURL: every API call automatically prepends this.
  // So apiClient.get("/verify") calls http://localhost:8080/agrishield/api/verify
  // In production you change this one line and everything updates.
  const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
    withCredentials: true,     // send session cookie with every request
    timeout: 10000,            // fail if server takes longer than 10 seconds
    headers: {
      "Content-Type": "application/json",
    },
  });
  
  // ── RESPONSE INTERCEPTOR ─────────────────────────────────────────────
  // This runs on EVERY response before your component sees it.
  // If the server returns 401 (not logged in), redirect to login automatically.
  apiClient.interceptors.response.use(
    (response) => response,    // success — pass through unchanged
    (error) => {
      if (error.response?.status === 401) {
        // Session expired or not logged in — send to login page
        window.location.href = "/login";
      }
      // Re-throw the error so individual API calls can still catch it
      return Promise.reject(error);
    }
  );
  
  // ══════════════════════════════════════════════════════════════════════
  // AUTH API
  // ══════════════════════════════════════════════════════════════════════
  
  export const authApi = {
  
    // Login: POST /api/auth/login with {email, password, totpCode?}
    login: (email, password, totpCode = null) =>
      AUTH_MODE === "mock"
        ? loginWithPassword(email, password, totpCode)
        : apiClient.post("/auth/login", { email, password, totpCode }),
  
    // Logout: POST /api/auth/logout
    logout: async () => {
      if (AUTH_MODE === "mock") {
        clearSessionUser();
        return mockResponse({ success: true });
      }
      return apiClient.post("/auth/logout");
    },
  
    // Get the current logged-in user profile
    me: async () => {
      if (AUTH_MODE === "mock") {
        const user = getSessionUser();
        if (!user) {
          const error = new Error("No active session");
          error.response = { status: 401, data: { message: "UNAUTHENTICATED" } };
          throw error;
        }
        return mockResponse(user);
      }
      return apiClient.get("/auth/me");
    },

    socialLogin: (provider, idToken) =>
      AUTH_MODE === "mock"
        ? loginWithSocial(provider, idToken)
        : apiClient.post("/auth/social", { provider, idToken }),

    requestPasswordReset: (identifier) =>
      AUTH_MODE === "mock"
        ? requestForgotPassword(identifier)
        : apiClient.post("/auth/password/forgot", { identifier }),

    resetPassword: (code, newPassword) =>
      AUTH_MODE === "mock"
        ? resetPassword(code, newPassword)
        : apiClient.post("/auth/password/reset", { code, newPassword }),

    register: (fullName, email, password, role, phone) =>
      AUTH_MODE === "mock"
        ? registerUser(fullName, email, password, role, phone)
        : apiClient.post("/auth/register", { fullName, email, password, role, phone }),

    verifyAccount: (email, code) =>
      AUTH_MODE === "mock"
        ? verifyRegistration(email, code)
        : apiClient.post("/auth/verify", { email, code }),
  
  };
  
  // ══════════════════════════════════════════════════════════════════════
  // DASHBOARD API
  // ══════════════════════════════════════════════════════════════════════
  
  export const dashboardApi = {
  
    // GET /api/dashboard — returns stats + recent anomalies
    getStats: () => apiClient.get("/dashboard"),
  
    // GET /api/dashboard/anomalies — live anomaly feed (for regulators)
    getAnomalies: () => apiClient.get("/dashboard/anomalies"),
  
  };
  
  // ══════════════════════════════════════════════════════════════════════
  // BATCH API
  // ══════════════════════════════════════════════════════════════════════
  
  export const batchApi = {
  
    // List all batches visible to current user (RLS filters by role)
    list: (page = 0, size = 20) =>
      apiClient.get("/batches", { params: { page, size } }),
  
    // Get one batch by ID
    getById: (batchId) => apiClient.get(`/batches/${batchId}`),
  
    // Manufacturer creates a new batch (status starts as DRAFT)
    create: (batchData) => apiClient.post("/batches", batchData),
  
    // Manufacturer submits draft batch for review
    submit: (batchId) => apiClient.post(`/batches/${batchId}/submit`),
  
    // Regulator approves (their first or second sign-off)
    approve: (batchId, notes) =>
      apiClient.post(`/batches/${batchId}/approve`, { notes }),
  
    // Regulator rejects — notes are mandatory (min 50 chars enforced by server)
    reject: (batchId, reason) =>
      apiClient.post(`/batches/${batchId}/reject`, { reason }),
  
    // List batches pending review (regulators only)
    getPending: () => apiClient.get("/batches/pending"),
  
    // Upload lab certificate PDF for a batch
    uploadLabCert: (batchId, file) => {
      const formData = new FormData();
      formData.append("file", file);
      return apiClient.post(`/batches/${batchId}/lab-cert`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
    },
  
  };
  
  // ══════════════════════════════════════════════════════════════════════
  // VERIFICATION API
  // ══════════════════════════════════════════════════════════════════════
  
  export const verifyApi = {
  
    // Verify a serial code (public — no login required)
    verify: (serialCode, lat = null, lng = null) =>
      apiClient.post("/verify", {
        serialCode,
        channel: "web",
        ...(lat && lng && { gpsLat: lat, gpsLng: lng }),
      }),
  
    // Get the full supply chain for a batch
    getCustodyChain: (batchId) => apiClient.get(`/custody/${batchId}`),
  
  };
  
  // ══════════════════════════════════════════════════════════════════════
  // ANOMALY API (regulators/admins only)
  // ══════════════════════════════════════════════════════════════════════
  
  export const anomalyApi = {
  
    list: (status = "open") => apiClient.get("/anomalies", { params: { status } }),
  
    resolve: (anomalyId, notes) =>
      apiClient.post(`/anomalies/${anomalyId}/resolve`, { notes }),
  
    suspend: (serialId) => apiClient.post(`/serials/${serialId}/suspend`),
  
  };
