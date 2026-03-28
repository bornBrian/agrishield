import { clearSessionCookie, json } from "../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });
  clearSessionCookie(res);
  return json(res, 200, { success: true });
}
