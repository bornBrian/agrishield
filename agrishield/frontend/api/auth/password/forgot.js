import {
  isDebugEnabled,
  json,
  normalizeEmail,
  plusMinutesIso,
  randomCode,
  readBody,
  sendSms,
  supabaseAdmin,
} from "../../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  try {
    const body = readBody(req);
    const identifier = normalizeEmail(body.identifier);
    const supabase = supabaseAdmin();

    const { data: user } = await supabase
      .from("users")
      .select("email, phone")
      .eq("email", identifier)
      .maybeSingle();

    if (!user) {
      return json(res, 200, {
        accepted: true,
        channel: "sms_or_email",
        message: "If the account exists, reset instructions were sent.",
      });
    }

    const code = randomCode();
    await supabase.from("reset_codes").insert({
      code,
      email: user.email,
      expires_at: plusMinutesIso(15),
      used: false,
    });

    await sendSms(user.phone, `AgriShield: Your password reset code is ${code}. Valid for 15 minutes.`);

    if (isDebugEnabled()) {
      return json(res, 200, {
        accepted: true,
        channel: "sms_or_email",
        message: "Reset code sent successfully.",
        previewCode: code,
        expiresInMinutes: 15,
      });
    }

    return json(res, 200, {
      accepted: true,
      channel: "sms_or_email",
      message: "If the account exists, reset instructions were sent.",
    });
  } catch (error) {
    return json(res, 500, { message: "RESET_REQUEST_FAILED", error: error.message });
  }
}
