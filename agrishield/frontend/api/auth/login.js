import {
  ensureRoleAllowed,
  json,
  normalizeEmail,
  readBody,
  setSessionCookie,
  signSession,
  supabaseAdmin,
  verifyPassword,
} from "../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  try {
    const body = readBody(req);
    const email = normalizeEmail(body.email);
    const password = String(body.password || "");
    const totpCode = String(body.totpCode || "");

    const supabase = supabaseAdmin();
    const { data: user } = await supabase
      .from("users")
      .select("id, full_name, email, role, password_hash, totp_required, verified")
      .eq("email", email)
      .maybeSingle();

    if (!user || !verifyPassword(password, user.password_hash)) {
      return json(res, 401, { message: "INVALID_CREDENTIALS" });
    }

    if (!user.verified) {
      return json(res, 403, { message: "ACCOUNT_NOT_VERIFIED" });
    }

    if (user.totp_required && totpCode !== "123456") {
      return json(res, 403, { message: "TOTP_REQUIRED" });
    }

    if (!ensureRoleAllowed(user.role)) {
      return json(res, 403, { message: "FARMER_WEB_NOT_ALLOWED" });
    }

    const token = signSession({ ...user, auth_provider: "password" });
    setSessionCookie(res, token);

    return json(res, 200, {
      id: user.id,
      fullName: user.full_name,
      email: user.email,
      role: user.role,
      verified: user.verified,
      authProvider: "password",
    });
  } catch (error) {
    return json(res, 500, { message: "LOGIN_FAILED", error: error.message });
  }
}
