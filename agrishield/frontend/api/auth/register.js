import {
  isDebugEnabled,
  isStrongPassword,
  json,
  normalizeEmail,
  plusMinutesIso,
  randomCode,
  readBody,
  sendSms,
  supabaseAdmin,
  hashPassword,
} from "../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  try {
    const body = readBody(req);
    const fullName = String(body.fullName || "").trim();
    const email = normalizeEmail(body.email);
    const password = String(body.password || "");
    const role = String(body.role || "dealer").toLowerCase();
    const phone = String(body.phone || "").trim() || null;

    if (!fullName || !email || !password || !email.includes("@")) {
      return json(res, 400, { message: "REGISTER_INPUT_INVALID" });
    }
    if (!isStrongPassword(password)) {
      return json(res, 400, { message: "PASSWORD_POLICY_FAILED" });
    }
    if (!["manufacturer", "distributor", "dealer", "farmer"].includes(role)) {
      return json(res, 400, { message: "ROLE_INVALID" });
    }

    const supabase = supabaseAdmin();

    const { data: existing } = await supabase.from("users").select("email").eq("email", email).maybeSingle();
    if (existing) return json(res, 409, { message: "ACCOUNT_EXISTS" });

    const passwordHash = hashPassword(password);
    const { error: insertError } = await supabase.from("users").insert({
      full_name: fullName,
      email,
      role,
      phone,
      password_hash: passwordHash,
      verified: false,
      totp_required: false,
    });

    if (insertError) return json(res, 500, { message: "REGISTER_FAILED" });

    const code = randomCode();
    const { error: verifyCodeError } = await supabase.from("verify_codes").upsert({
      email,
      code,
      expires_at: plusMinutesIso(15),
      used: false,
    });

    if (verifyCodeError) return json(res, 500, { message: "VERIFY_CODE_CREATE_FAILED" });

    await sendSms(phone, `AgriShield: Your verification code is ${code}. Valid for 15 minutes.`);

    if (isDebugEnabled()) {
      return json(res, 200, {
        accepted: true,
        message: "Registration received. Verify your account with the code sent.",
        previewCode: code,
        expiresInMinutes: 15,
      });
    }

    return json(res, 200, {
      accepted: true,
      message: "Registration received. Verify your account with the code sent.",
    });
  } catch (error) {
    return json(res, 500, { message: "REGISTER_FAILED", error: error.message });
  }
}
