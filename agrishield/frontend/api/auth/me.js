import { json, parseSession } from "../_lib/auth.js";

export default async function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { message: "METHOD_NOT_ALLOWED" });

  const session = parseSession(req);
  if (!session) return json(res, 401, { message: "UNAUTHENTICATED" });

  return json(res, 200, {
    id: session.id,
    fullName: session.fullName,
    email: session.email,
    role: session.role,
    verified: session.verified,
    authProvider: session.authProvider || "password",
  });
}
