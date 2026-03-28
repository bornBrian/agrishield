import {
  ensureRoleAllowed,
  json,
  normalizeEmail,
  readBody,
  setSessionCookie,
  signSession,
  supabaseAdmin,
} from "../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  try {
    const body = readBody(req);
    const provider = String(body.provider || "").toLowerCase();
    const idToken = String(body.idToken || "").trim();

    if (!provider || !idToken) return json(res, 400, { message: "SOCIAL_TOKEN_REQUIRED" });

    // Live token verification can be added here. For now we accept email-like debug tokens.
    const email = normalizeEmail(idToken.includes("@") ? idToken : "");
    if (!email) return json(res, 401, { message: "SOCIAL_TOKEN_INVALID" });

    const supabase = supabaseAdmin();
    const { data: user } = await supabase
      .from("users")
      .select("id, full_name, email, role, verified")
      .eq("email", email)
      .maybeSingle();

    if (!user) return json(res, 403, { message: "ACCOUNT_NOT_PROVISIONED" });
    if (!user.verified) return json(res, 403, { message: "ACCOUNT_NOT_VERIFIED" });
    if (!ensureRoleAllowed(user.role)) return json(res, 403, { message: "FARMER_WEB_NOT_ALLOWED" });

    const token = signSession({ ...user, auth_provider: provider });
    setSessionCookie(res, token);

    return json(res, 200, {
      id: user.id,
      fullName: user.full_name,
      email: user.email,
      role: user.role,
      verified: user.verified,
      authProvider: provider,
    });
  } catch (error) {
    return json(res, 500, { message: "SOCIAL_LOGIN_FAILED", error: error.message });
  }
}
