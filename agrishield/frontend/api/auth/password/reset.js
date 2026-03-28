import {
  isExpired,
  isStrongPassword,
  json,
  normalizeEmail,
  readBody,
  supabaseAdmin,
  hashPassword,
} from "../../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  try {
    const body = readBody(req);
    const code = String(body.code || "").trim();
    const newPassword = String(body.newPassword || "");

    if (!code || !newPassword) return json(res, 400, { message: "RESET_INPUT_INVALID" });
    if (!isStrongPassword(newPassword)) return json(res, 400, { message: "PASSWORD_POLICY_FAILED" });

    const supabase = supabaseAdmin();
    const { data: token } = await supabase
      .from("reset_codes")
      .select("code, email, expires_at, used")
      .eq("code", code)
      .maybeSingle();

    if (!token || token.used || isExpired(token.expires_at)) {
      return json(res, 400, { message: "RESET_CODE_INVALID" });
    }

    const email = normalizeEmail(token.email);
    const { error: updateUserError } = await supabase
      .from("users")
      .update({ password_hash: hashPassword(newPassword) })
      .eq("email", email);

    if (updateUserError) return json(res, 500, { message: "RESET_FAILED" });

    await supabase.from("reset_codes").update({ used: true }).eq("code", code);

    return json(res, 200, {
      success: true,
      message: "Password reset successful. You can now sign in.",
    });
  } catch (error) {
    return json(res, 500, { message: "RESET_FAILED", error: error.message });
  }
}
