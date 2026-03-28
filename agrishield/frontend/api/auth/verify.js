import { isExpired, json, normalizeEmail, readBody, supabaseAdmin } from "../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  try {
    const body = readBody(req);
    const email = normalizeEmail(body.email);
    const code = String(body.code || "").trim();

    if (!email || !code) return json(res, 400, { message: "VERIFY_INPUT_INVALID" });

    const supabase = supabaseAdmin();

    const { data: token } = await supabase.from("verify_codes").select("email, code, expires_at, used").eq("email", email).maybeSingle();
    if (!token || token.used || token.code !== code || isExpired(token.expires_at)) {
      return json(res, 400, { message: "VERIFY_CODE_INVALID" });
    }

    const { error: verifyUserError } = await supabase.from("users").update({ verified: true }).eq("email", email);
    if (verifyUserError) return json(res, 500, { message: "VERIFY_FAILED" });

    await supabase.from("verify_codes").update({ used: true }).eq("email", email);

    return json(res, 200, { success: true, message: "Account verified successfully. You can now sign in." });
  } catch (error) {
    return json(res, 500, { message: "VERIFY_FAILED", error: error.message });
  }
}
